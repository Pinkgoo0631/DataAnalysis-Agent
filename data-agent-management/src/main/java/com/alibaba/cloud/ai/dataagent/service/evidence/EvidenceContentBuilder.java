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
package com.alibaba.cloud.ai.dataagent.service.evidence;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 证据内容渲染器：把召回的候选文档渲染成供下游节点使用的证据文本。
 *
 * <p>
 * 从 {@code EvidenceRecallNode} 中抽取，供证据召回与精排（RerankNode）共用，避免渲染逻辑重复。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class EvidenceContentBuilder {

	private final AgentKnowledgeMapper agentKnowledgeMapper;

	/**
	 * 按文档元数据中的 vectorType 自动区分业务术语与智能体知识后渲染证据内容
	 */
	public String buildEvidenceContent(List<Document> allDocuments) {
		if (CollectionUtils.isEmpty(allDocuments)) {
			return "无";
		}

		List<Document> businessTermDocuments = new ArrayList<>();
		List<Document> agentKnowledgeDocuments = new ArrayList<>();
		for (Document doc : allDocuments) {
			Object vectorType = doc.getMetadata() == null ? null
					: doc.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE);
			if (DocumentMetadataConstant.AGENT_KNOWLEDGE.equals(vectorType)) {
				agentKnowledgeDocuments.add(doc);
			}
			else {
				businessTermDocuments.add(doc);
			}
		}

		return buildEvidenceContent(businessTermDocuments, agentKnowledgeDocuments);
	}

	// 构建证据内容，输出格式
	// 1. [来源: 2025Q3报告-销售数据.md] ...华东地区的增长主要来自于核心用户...
	// 2. [来源: 客服FAQ] Q: 退款怎么算? A: 只统计已入库退货...
	public String buildEvidenceContent(List<Document> businessTermDocuments,
			List<Document> agentKnowledgeDocuments) {
		// 构建业务知识内容
		String businessKnowledgeContent = buildBusinessKnowledgeContent(businessTermDocuments);

		// 构建智能体知识内容
		String agentKnowledgeContent = buildAgentKnowledgeContent(agentKnowledgeDocuments);

		// 使用PromptHelper的模板方法进行渲染
		String businessPrompt = PromptHelper.buildBusinessKnowledgePrompt(businessKnowledgeContent);
		String agentPrompt = PromptHelper.buildAgentKnowledgePrompt(agentKnowledgeContent);

		// 添加证据构建日志
		log.info("Building evidence content: business knowledge length {}, agent knowledge length {}",
				businessKnowledgeContent.length(), agentKnowledgeContent.length());

		// 拼接业务知识和智能体知识作为证据
		return businessKnowledgeContent.isEmpty() && agentKnowledgeContent.isEmpty() ? "无"
				: businessPrompt + (agentKnowledgeContent.isEmpty() ? "" : "\n\n" + agentPrompt);
	}

	private String buildBusinessKnowledgeContent(List<Document> businessTermDocuments) {
		if (CollectionUtils.isEmpty(businessTermDocuments)) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		// 直接使用Document的完整内容，每行一个Document
		for (Document doc : businessTermDocuments) {
			result.append(doc.getText()).append("\n");
		}

		return result.toString();
	}

	private String buildAgentKnowledgeContent(List<Document> agentKnowledgeDocuments) {
		if (CollectionUtils.isEmpty(agentKnowledgeDocuments)) {
			return "";
		}

		StringBuilder result = new StringBuilder();

		for (int i = 0; i < agentKnowledgeDocuments.size(); i++) {
			Document doc = agentKnowledgeDocuments.get(i);
			Map<String, Object> metadata = doc.getMetadata();
			String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

			// 根据知识类型调用不同的处理方法
			if (KnowledgeType.FAQ.getCode().equals(knowledgeType) || KnowledgeType.QA.getCode().equals(knowledgeType)) {
				processFaqOrQaKnowledge(doc, i, result);
			}
			else {
				processDocumentKnowledge(doc, i, result);
			}
		}

		return result.toString();
	}

	/**
	 * 处理FAQ或QA类型的知识
	 */
	private void processFaqOrQaKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = ((Number) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID)).intValue();
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					String title = knowledge.getTitle();
					// 格式：[来源: xxx] Q: xxx A: xxx
					result.append(index + 1).append(". [来源: ");
					result.append(title.isEmpty() ? "知识库" : title);
					result.append("] Q: ").append(content).append(" A: ").append(knowledge.getContent()).append("\n");

					log.debug("Successfully processed {} knowledge with title: {}", knowledgeType, title);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
				// 如果获取失败，使用原始内容
				result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
			}
		}
		else {
			// 如果没有知识ID，使用原始内容
			log.error("No knowledge id found for agent knowledge document: {}", doc.getId());
			result.append(index + 1).append(". [来源: 知识库] ").append(content).append("\n");
		}
	}

	/**
	 * 处理DOCUMENT类型的知识
	 */
	private void processDocumentKnowledge(Document doc, int index, StringBuilder result) {
		Map<String, Object> metadata = doc.getMetadata();
		String content = doc.getText();
		Integer knowledgeId = ((Number) metadata.get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID)).intValue();
		String knowledgeType = (String) metadata.get(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);
		String title = "";
		String sourceFilename = "";

		log.debug("Processing {} type knowledge with id: {}", knowledgeType, knowledgeId);

		if (knowledgeId != null) {
			try {
				AgentKnowledge knowledge = agentKnowledgeMapper.selectById(knowledgeId);
				if (knowledge != null) {
					title = knowledge.getTitle();
					sourceFilename = knowledge.getSourceFilename();

					log.debug("Successfully processed {} knowledge with title: {}, source file: {}", knowledgeType,
							title, sourceFilename);
				}
				else {
					log.warn("Knowledge not found for id: {}", knowledgeId);
				}
			}
			catch (Exception e) {
				log.error("Error getting knowledge by id: {}", knowledgeId, e);
			}
		}

		// 构建来源信息，格式为"标题-文件名"
		String sourceInfo = title.isEmpty() ? "文档" : title;
		if (!sourceFilename.isEmpty()) {
			sourceInfo += "-" + sourceFilename;
		}

		result.append(index + 1).append(". [来源: ");
		result.append(sourceInfo);
		result.append("] ").append(content).append("\n");
	}

}
