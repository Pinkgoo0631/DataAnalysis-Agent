/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.prompt.EvidenceQueryRewriteDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.evidence.EvidenceContentBuilder;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.*;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

@Slf4j
@Component
@AllArgsConstructor
public class EvidenceRecallNode implements NodeAction {

	private final LlmService llmService;

	private final AgentVectorStoreService vectorStoreService;

	private final JsonParseUtil jsonParseUtil;

	private final EvidenceContentBuilder evidenceContentBuilder;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 从state中提取question和agentId
		String question = StateUtil.getStringValue(state, INPUT_KEY);
		String agentId = StateUtil.getStringValue(state, AGENT_ID);
		Assert.hasText(agentId, "Agent ID cannot be empty.");

		log.debug("Rewriting query before getting evidence in question: {}", question);
		log.debug("Agent ID: {}", agentId);

		String multiTurn = StateUtil.getStringValue(state, MULTI_TURN_CONTEXT, "(无)");

		// 构建查询重写提示
		String prompt = PromptHelper.buildEvidenceQueryRewritePrompt(multiTurn, question);
		log.debug("Built evidence-query-rewrite prompt as follows \n {} \n", prompt);

		// 调用LLM进行查询重写
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
		Sinks.Many<String> evidenceDisplaySink = Sinks.many().multicast().onBackpressureBuffer();

		final Map<String, Object> resultMap = new HashMap<>();
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在查询重写以更好召回evidence..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n查询重写完成！")),
				result -> {
					resultMap.putAll(getEvidences(result, agentId, evidenceDisplaySink));
					return resultMap;
				});

		Flux<GraphResponse<StreamingOutput>> evidenceFlux = FluxUtil.createStreamingGenerator(this.getClass(), state,
				evidenceDisplaySink.asFlux().map(ChatResponseUtil::createPureResponse), Flux.empty(), Flux.empty(),
				result -> resultMap);
		return Map.of(EVIDENCE, generator.concatWith(evidenceFlux));
	}

	private Map<String, Object> getEvidences(String llmOutput, String agentId, Sinks.Many<String> sink) {
		try {
			String standaloneQuery = extractStandaloneQuery(llmOutput);

			if (null == standaloneQuery || standaloneQuery.isEmpty()) {
				log.debug("No standalone query from LLM output");
				sink.tryEmitNext("未能进行查询重写！\n");
				return Map.of(EVIDENCE, "无", EVIDENCE_DOCUMENTS, List.of(), EVIDENCE_QUERY, "");
			}
			// 输出重写后的查询
			outputRewrittenQuery(standaloneQuery, sink);

			// 获取业务知识和智能体知识文档
			DocumentRetrievalResult retrievalResult = retrieveDocuments(agentId, standaloneQuery);

			// 检查是否有证据文档
			if (retrievalResult.allDocuments().isEmpty()) {
				log.debug("No evidence documents found for agent: {} with query: {}", agentId, standaloneQuery);
				sink.tryEmitNext("未找到证据！\n");
				return Map.of(EVIDENCE, "无", EVIDENCE_DOCUMENTS, List.of(), EVIDENCE_QUERY, standaloneQuery);
			}

			// 构建证据内容
			String evidence = evidenceContentBuilder.buildEvidenceContent(retrievalResult.businessTermDocuments(),
					retrievalResult.agentKnowledgeDocuments());
			log.debug("Evidence content built as follows \n {} \n", evidence);
			// 输出证据内容
			outputEvidenceContent(retrievalResult.allDocuments(), sink);

			// 返回结果：同时输出候选文档，供下游 RerankNode 精排
			return Map.of(EVIDENCE, evidence, EVIDENCE_DOCUMENTS, retrievalResult.allDocuments(), EVIDENCE_QUERY,
					standaloneQuery);
		}
		catch (Exception e) {
			log.error("Error occurred while getting evidences", e);
			sink.tryEmitError(e);
			return Map.of(EVIDENCE, "", EVIDENCE_DOCUMENTS, List.of());
		}
		finally {
			sink.tryEmitComplete();
		}
	}

	private void outputRewrittenQuery(String standaloneQuery, Sinks.Many<String> sink) {
		sink.tryEmitNext("重写后查询：\n");
		sink.tryEmitNext(standaloneQuery + "\n");
		log.debug("Using standalone query for evidence recall: {}", standaloneQuery);
		sink.tryEmitNext("正在获取证据...");
	}

	private DocumentRetrievalResult retrieveDocuments(String agentId, String standaloneQuery) {
		// 获取业务知识文档
		List<Document> businessTermDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.BUSINESS_TERM)
			.stream()
			.toList();

		// 获取智能体知识文档
		List<Document> agentKnowledgeDocuments = vectorStoreService
			.getDocumentsForAgent(agentId, standaloneQuery, DocumentMetadataConstant.AGENT_KNOWLEDGE)
			.stream()
			.toList();

		// 合并所有证据文档
		List<Document> allDocuments = new ArrayList<>();
		if (!businessTermDocuments.isEmpty())
			allDocuments.addAll(businessTermDocuments);
		if (!agentKnowledgeDocuments.isEmpty())
			allDocuments.addAll(agentKnowledgeDocuments);

		// 添加文档检索日志
		log.info("Retrieved documents for agent {}: {} business term docs, {} agent knowledge docs, total {} docs",
				agentId, businessTermDocuments.size(), agentKnowledgeDocuments.size(), allDocuments.size());

		return new DocumentRetrievalResult(businessTermDocuments, agentKnowledgeDocuments, allDocuments);
	}

	private void outputEvidenceContent(List<Document> allDocuments, Sinks.Many<String> sink) {
		if (allDocuments.isEmpty()) {
			return;
		}

		log.info("Outputting evidence content for {} documents", allDocuments.size());
		sink.tryEmitNext("已找到 " + allDocuments.size() + " 条相关证据文档，如下是文档的部分信息\n");

		// 只输出文档的摘要信息，而不是完整内容
		for (int i = 0; i < allDocuments.size(); i++) {
			Document doc = allDocuments.get(i);
			String content = doc.getText();

			// 限制每个文档摘要的长度，最多显示100个字符
			String summary = content.length() > 100 ? content.substring(0, 100) + "..." : content;

			sink.tryEmitNext(String.format("证据%d: %s\n", i + 1, summary));
		}
	}

	private record DocumentRetrievalResult(List<Document> businessTermDocuments, List<Document> agentKnowledgeDocuments,
			List<Document> allDocuments) {
	}

	private String extractStandaloneQuery(String llmOutput) {
		EvidenceQueryRewriteDTO evidenceQueryRewriteDTO;
		try {
			String content = MarkdownParserUtil.extractText(llmOutput.trim());
			evidenceQueryRewriteDTO = jsonParseUtil.tryConvertToObject(content, EvidenceQueryRewriteDTO.class);
			log.debug("For getting evidence, successfully parsed EvidenceQueryRewriteDTO from LLM response: {}",
					evidenceQueryRewriteDTO);
			return evidenceQueryRewriteDTO.getStandaloneQuery();
		}
		catch (Exception e) {
			log.error("Failed to parse EvidenceQueryRewriteDTO from LLM response", e);
		}
		return null;
	}

}
