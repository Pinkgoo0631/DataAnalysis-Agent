/*
 * Copyright 2026 the original author or authors.
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

import com.alibaba.cloud.ai.dataagent.dto.prompt.RerankOutputDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.evidence.EvidenceContentBuilder;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.*;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 证据精排节点：对 EvidenceRecallNode 召回的候选文档按与用户查询的相关度重新排序，
 * 并把精排后的文档与证据文本写回全局状态。
 */
@Slf4j
@Component
@AllArgsConstructor
public class RerankNode implements NodeAction {

	private final LlmService llmService;

	private final JsonParseUtil jsonParseUtil;

	private final EvidenceContentBuilder evidenceContentBuilder;

	private final DataAgentProperties dataAgentProperties;

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 获取原始 query
		String question = StateUtil.getStringValue(state, EVIDENCE_QUERY,
				StateUtil.getStringValue(state, INPUT_KEY));
		// 获取 EvidenceRecallNode 召回的候选文档
		List<Document> candidates = (List<Document>) StateUtil.getObjectValue(state, EVIDENCE_DOCUMENTS, List.class,
				List.of());

		if (CollectionUtils.isEmpty(candidates)) {
			log.info("No candidate evidence documents to rerank, keep original evidence");
			return Map.of(EVIDENCE, StateUtil.getStringValue(state, EVIDENCE, "无"));
		}

		// 构建精排提示
		String candidatesText = buildCandidatesText(candidates);
		String prompt = PromptHelper.buildRerankPrompt(question, candidatesText);
		log.debug("Built rerank prompt as follows \n {} \n", prompt);

		// 调用LLM进行精排
		Flux<ChatResponse> responseFlux = llmService.callUser(prompt);

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGenerator(this.getClass(), state,
				responseFlux,
				Flux.just(ChatResponseUtil.createResponse("正在对召回证据进行精排..."),
						ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())),
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()),
						ChatResponseUtil.createResponse("\n证据精排完成！")),
				llmOutput -> handleRerank(llmOutput, candidates));

		return Map.of(EVIDENCE, generator);
	}

	/**
	 * 解析LLM的精排结果并按新顺序重建证据
	 */
	private Map<String, Object> handleRerank(String llmOutput, List<Document> candidates) {
		List<Integer> rankedIndices = parseRankedIndices(llmOutput);
		List<Document> reranked = limit(reorder(candidates, rankedIndices));
		String evidence = evidenceContentBuilder.buildEvidenceContent(reranked);
		log.info("Rerank finished: {} candidates, ranked indices {}", candidates.size(), rankedIndices);
		return Map.of(EVIDENCE, evidence, EVIDENCE_DOCUMENTS, reranked);
	}

	List<Document> limit(List<Document> documents) {
		int topN = Math.max(1, dataAgentProperties.getVectorStore().getRerankTopN());
		return documents.size() <= topN ? documents : List.copyOf(documents.subList(0, topN));
	}

	private List<Integer> parseRankedIndices(String llmOutput) {
		try {
			String content = MarkdownParserUtil.extractText(llmOutput.trim());
			RerankOutputDTO rerankOutputDTO = jsonParseUtil.tryConvertToObject(content, RerankOutputDTO.class);
			log.debug("Successfully parsed RerankOutputDTO from LLM response: {}", rerankOutputDTO);
			return rerankOutputDTO == null ? List.of() : rerankOutputDTO.getRankedIndices();
		}
		catch (Exception e) {
			log.error("Failed to parse RerankOutputDTO from LLM response, keep original order", e);
		}
		return List.of();
	}

	/**
	 * 按LLM给出的顺序重排候选文档，并补齐LLM未覆盖的文档，保证不丢文档
	 */
	List<Document> reorder(List<Document> candidates, List<Integer> rankedIndices) {
		if (CollectionUtils.isEmpty(rankedIndices)) {
			return candidates;
		}

		List<Document> reranked = new ArrayList<>(candidates.size());
		Set<Integer> visited = new HashSet<>();
		for (Integer index : rankedIndices) {
			if (index != null && index >= 0 && index < candidates.size() && visited.add(index)) {
				reranked.add(candidates.get(index));
			}
		}

		for (int i = 0; i < candidates.size(); i++) {
			if (!visited.contains(i)) {
				reranked.add(candidates.get(i));
			}
		}

		return reranked;
	}

	/**
	 * 把候选文档按编号拼接，编号即LLM需要排序的下标
	 */
	private String buildCandidatesText(List<Document> candidates) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < candidates.size(); i++) {
			String text = candidates.get(i).getText();
			result.append("[").append(i).append("] ").append(text == null ? "" : text).append("\n");
		}
		return result.toString();
	}

}
