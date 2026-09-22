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
package com.alibaba.cloud.ai.dataagent.service.business;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.dataagent.converter.BusinessKnowledgeConverter;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.mapper.BusinessKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class BusinessKnowledgeServiceImpl implements BusinessKnowledgeService {

	private static final int MAX_CSV_RECORDS = 1000;

	private static final Set<String> TRUE_VALUES = Set.of("true", "1", "yes", "y", "是", "召回");

	private static final Set<String> FALSE_VALUES = Set.of("false", "0", "no", "n", "否", "不召回");

	private final BusinessKnowledgeMapper businessKnowledgeMapper;

	private final AgentVectorStoreService agentVectorStoreService;

	private final BusinessKnowledgeConverter businessKnowledgeConverter;

	@Override
	public List<BusinessKnowledgeVO> getKnowledge(Long agentId) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectByAgentId(agentId);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> getAllKnowledge() {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.selectAll();
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public List<BusinessKnowledgeVO> searchKnowledge(Long agentId, String keyword) {
		List<BusinessKnowledge> businessKnowledges = businessKnowledgeMapper.searchInAgent(agentId, keyword);
		if (CollectionUtils.isEmpty(businessKnowledges)) {
			return Collections.emptyList();
		}
		return businessKnowledges.stream().map(businessKnowledgeConverter::toVo).toList();
	}

	@Override
	public BusinessKnowledgeVO getKnowledgeById(Long id) {
		BusinessKnowledge businessKnowledge = businessKnowledgeMapper.selectById(id);
		if (businessKnowledge == null) {
			return null;
		}
		return businessKnowledgeConverter.toVo(businessKnowledge);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO addKnowledge(CreateBusinessKnowledgeDTO knowledgeDTO) {
		BusinessKnowledge entity = businessKnowledgeConverter.toEntityForCreate(knowledgeDTO);

		// 插入数据库
		if (businessKnowledgeMapper.insert(entity) <= 0) {
			throw new RuntimeException("Failed to add knowledge to database");
		}

		// 未召回的知识不参与向量化
		if (entity.getIsRecall() == null || entity.getIsRecall() == 0) {
			entity.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			entity.setErrorMsg(null);
			businessKnowledgeMapper.updateById(entity);
			return businessKnowledgeConverter.toVo(entity);
		}

		try {
			Document document = DocumentConverterUtil.convertBusinessKnowledgeToDocument(entity);
			agentVectorStoreService.addDocuments(entity.getAgentId().toString(), List.of(document));
			entity.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			entity.setErrorMsg(null);
			businessKnowledgeMapper.updateById(entity);
		}
		catch (Exception e) {
			String errorMsg = "Failed to add to vector store: " + e.getMessage();
			entity.setEmbeddingStatus(EmbeddingStatus.FAILED);
			entity.setErrorMsg(errorMsg);
			businessKnowledgeMapper.updateById(entity);
			log.error("Failed to add knowledge to vector store for id: {}, error: {}", entity.getId(), errorMsg);
		}
		return businessKnowledgeConverter.toVo(entity);
	}

	@Override
	public BatchImportResult importFromCsv(InputStream inputStream, String filename, Long agentId) {
		if (inputStream == null) {
			throw new IllegalArgumentException("CSV文件不能为空");
		}
		if (agentId == null) {
			throw new IllegalArgumentException("智能体ID不能为空");
		}
		if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new IllegalArgumentException("仅支持.csv格式的文件");
		}

		List<CsvImportRow> rows = parseCsv(inputStream);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("CSV文件中没有可导入的数据");
		}
		if (rows.size() > MAX_CSV_RECORDS) {
			throw new IllegalArgumentException("单次最多导入" + MAX_CSV_RECORDS + "条业务知识");
		}

		BatchImportResult result = BatchImportResult.builder()
			.total(rows.size())
			.successCount(0)
			.failCount(0)
			.build();

		for (CsvImportRow row : rows) {
			if (row.error() != null) {
				addImportError(result, row.lineNumber(), row.error());
				continue;
			}

			CreateBusinessKnowledgeDTO dto = row.knowledge();
			dto.setAgentId(agentId);
			try {
				BusinessKnowledgeVO imported = addKnowledge(dto);
				if (EmbeddingStatus.FAILED.getValue().equals(imported.getEmbeddingStatus())) {
					addImportError(result, row.lineNumber(), "知识已保存，但向量化失败：" + imported.getErrorMsg());
				}
				else {
					result.setSuccessCount(result.getSuccessCount() + 1);
				}
			}
			catch (Exception e) {
				addImportError(result, row.lineNumber(), e.getMessage());
			}
		}

		return result;
	}

	private List<CsvImportRow> parseCsv(InputStream inputStream) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(skipUtf8Bom(inputStream), StandardCharsets.UTF_8));
				CSVParser parser = CSVFormat.DEFAULT.builder()
					.setHeader()
					.setSkipHeaderRecord(true)
					.setIgnoreEmptyLines(true)
					.setTrim(true)
					.build()
					.parse(reader)) {
			Map<String, String> headers = normalizedHeaders(parser.getHeaderMap().keySet());
			String termHeader = requiredHeader(headers, "业务名词", "businessterm", "term");
			String descriptionHeader = requiredHeader(headers, "描述", "description");
			String synonymsHeader = optionalHeader(headers, "同义词", "synonyms");
			String recallHeader = optionalHeader(headers, "是否召回", "isrecall");

			List<CsvImportRow> rows = new ArrayList<>();
			for (CSVRecord record : parser) {
				int lineNumber = Math.toIntExact(record.getRecordNumber() + 1);
				try {
					String businessTerm = value(record, termHeader);
					String description = value(record, descriptionHeader);
					if (!StringUtils.hasText(businessTerm)) {
						rows.add(new CsvImportRow(lineNumber, null, "业务名词不能为空"));
						continue;
					}
					if (!StringUtils.hasText(description)) {
						rows.add(new CsvImportRow(lineNumber, null, "描述不能为空"));
						continue;
					}

					CreateBusinessKnowledgeDTO dto = CreateBusinessKnowledgeDTO.builder()
						.businessTerm(businessTerm)
						.description(description)
						.synonyms(value(record, synonymsHeader))
						.isRecall(parseRecall(value(record, recallHeader)))
						.build();
					rows.add(new CsvImportRow(lineNumber, dto, null));
				}
				catch (IllegalArgumentException e) {
					rows.add(new CsvImportRow(lineNumber, null, e.getMessage()));
				}
			}
			return rows;
		}
		catch (IOException e) {
			throw new IllegalArgumentException("CSV文件读取失败：" + e.getMessage(), e);
		}
	}

	private InputStream skipUtf8Bom(InputStream inputStream) throws IOException {
		PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, 3);
		byte[] prefix = pushbackInputStream.readNBytes(3);
		if (!(prefix.length == 3 && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB
				&& prefix[2] == (byte) 0xBF)) {
			pushbackInputStream.unread(prefix);
		}
		return pushbackInputStream;
	}

	private Map<String, String> normalizedHeaders(Set<String> headerNames) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (String headerName : headerNames) {
			headers.put(headerName.trim().toLowerCase(Locale.ROOT), headerName);
		}
		return headers;
	}

	private String requiredHeader(Map<String, String> headers, String... aliases) {
		String header = optionalHeader(headers, aliases);
		if (header == null) {
			throw new IllegalArgumentException("CSV缺少必填列：" + aliases[0]);
		}
		return header;
	}

	private String optionalHeader(Map<String, String> headers, String... aliases) {
		for (String alias : aliases) {
			String header = headers.get(alias.toLowerCase(Locale.ROOT));
			if (header != null) {
				return header;
			}
		}
		return null;
	}

	private String value(CSVRecord record, String header) {
		return header == null ? "" : record.get(header).trim();
	}

	private Boolean parseRecall(String value) {
		if (!StringUtils.hasText(value)) {
			return true;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if (TRUE_VALUES.contains(normalized)) {
			return true;
		}
		if (FALSE_VALUES.contains(normalized)) {
			return false;
		}
		throw new IllegalArgumentException("是否召回仅支持true/false、1/0、是/否");
	}

	private void addImportError(BatchImportResult result, int lineNumber, String message) {
		result.setFailCount(result.getFailCount() + 1);
		result.addError("第" + lineNumber + "行：" + (StringUtils.hasText(message) ? message : "导入失败"));
	}

	private record CsvImportRow(int lineNumber, CreateBusinessKnowledgeDTO knowledge, String error) {
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BusinessKnowledgeVO updateKnowledge(Long id, UpdateBusinessKnowledgeDTO knowledgeDTO) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}
		// 更新属性
		knowledge.setBusinessTerm(knowledgeDTO.getBusinessTerm());
		knowledge.setDescription(knowledgeDTO.getDescription());
		if (StringUtils.hasText(knowledgeDTO.getSynonyms()))
			knowledge.setSynonyms(knowledgeDTO.getSynonyms());
		if (knowledgeDTO.getIsRecall() != null) {
			knowledge.setIsRecall(knowledgeDTO.getIsRecall() ? 1 : 0);
		}

		// 设置初始状态为处理中
		knowledge.setEmbeddingStatus(EmbeddingStatus.PROCESSING);

		// 先更新数据库
		if (businessKnowledgeMapper.updateById(knowledge) <= 0) {
			throw new RuntimeException("Failed to update knowledge in database");
		}

		// 未召回的知识不参与向量化
		if (knowledge.getIsRecall() == null || knowledge.getIsRecall() == 0) {
			knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			knowledge.setErrorMsg(null);
			businessKnowledgeMapper.updateById(knowledge);
			return businessKnowledgeConverter.toVo(knowledge);
		}

		// 尝试更新向量库
		try {
			syncToVectorStore(knowledge);
			knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			knowledge.setErrorMsg(null);
			businessKnowledgeMapper.updateById(knowledge);
		}
		catch (Exception e) {
			// 向量库更新失败，不回滚MySQL，只标记状态为失败
			String errorMsg = "Failed to update vector store: " + e.getMessage();
			knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
			knowledge.setErrorMsg(errorMsg);
			businessKnowledgeMapper.updateById(knowledge);
			log.error("Failed to update vector store for knowledge id: {}, error: {}", id, errorMsg);
		}
		return businessKnowledgeConverter.toVo(knowledge);
	}

	/**
	 * 更新向量库中的知识向量
	 */
	private void syncToVectorStore(BusinessKnowledge knowledge) {
		Document newDocument = DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge);
		agentVectorStoreService.replaceDocumentsByMetadata(vectorMetadata(knowledge), List.of(newDocument));

		log.info("Successfully updated vector store for knowledge id: {}", knowledge.getId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteKnowledge(Long id) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			log.warn("Knowledge not found with id: " + id);
			return;
		}

		doDelVector(knowledge);

		if (businessKnowledgeMapper.logicalDelete(id, 1) <= 0) {
			// 重新添加修复被删除的记录
			agentVectorStoreService.addDocuments(knowledge.getAgentId().toString(),
					List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(knowledge)));
			throw new RuntimeException("Failed to logically delete knowledge from database");
		}
	}

	private void doDelVector(BusinessKnowledge knowledge) {
		agentVectorStoreService.deleteDocumentsByMetadata(knowledge.getAgentId().toString(), vectorMetadata(knowledge));
	}

	private Map<String, Object> vectorMetadata(BusinessKnowledge knowledge) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(Constant.AGENT_ID, knowledge.getAgentId().toString());
		metadata.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, knowledge.getId());
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		return metadata;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recallKnowledge(Long id, Boolean isRecall) {
		// 从数据库获取原始数据
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("Knowledge not found with id: " + id);
		}

		// 更新数据库即可，不需要更新向量库，混合检索的的时候DynamicFilterService会根据 isRecall 字段过滤了
		knowledge.setIsRecall(isRecall ? 1 : 0);
		businessKnowledgeMapper.updateById(knowledge);

		// 从 未召回 -> 召回 时，需要确保向量存在（未召回的知识在创建/更新时跳过了向量化）
		if (isRecall) {
			try {
				syncToVectorStore(knowledge);
				knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
				knowledge.setErrorMsg(null);
				businessKnowledgeMapper.updateById(knowledge);
			}
			catch (Exception e) {
				knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
				knowledge.setErrorMsg(
						e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage());
				businessKnowledgeMapper.updateById(knowledge);
				log.error("Failed to vectorize knowledge on recall, id: {}, error: {}", id, e.getMessage());
			}
		}

	}

	@Override
	public void refreshAllKnowledgeToVectorStore(String agentId) throws Exception {
		// 获取所有 isRecall 等于 1 且未逻辑删除的 BusinessKnowledge
		List<BusinessKnowledge> allKnowledge = businessKnowledgeMapper.selectAll();
		List<BusinessKnowledge> recalledKnowledge = allKnowledge.stream()
			.filter(knowledge -> knowledge.getIsRecall() != null && knowledge.getIsRecall() == 1)
			.filter(knowledge -> knowledge.getIsDeleted() == null || knowledge.getIsDeleted() == 0)
			.filter(knowledge -> agentId.equals(knowledge.getAgentId().toString()))
			.toList();

		// 转换为 Document 并插入到 vectorStore
		if (!recalledKnowledge.isEmpty()) {
			List<Document> documents = recalledKnowledge.stream()
				.map(DocumentConverterUtil::convertBusinessKnowledgeToDocument)
				.toList();
			agentVectorStoreService.replaceDocumentsByMetadata(Map.of(Constant.AGENT_ID, agentId,
					DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM), documents);

			// 批量更新 embedding_status 为 COMPLETED，避免前端一直显示"等待中"
			for (BusinessKnowledge knowledge : recalledKnowledge) {
				knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
				knowledge.setErrorMsg(null);
				businessKnowledgeMapper.updateById(knowledge);
			}
			log.info("Updated {} business knowledge records to COMPLETED status", recalledKnowledge.size());
		}
		else {
			agentVectorStoreService.deleteDocumentsByVectorType(agentId, DocumentMetadataConstant.BUSINESS_TERM);
		}
	}

	@Override
	public void retryEmbedding(Long id) {
		BusinessKnowledge knowledge = businessKnowledgeMapper.selectById(id);
		if (knowledge == null) {
			throw new RuntimeException("BusinessKnowledge not found with id: " + id);
		}

		if (knowledge.getEmbeddingStatus().equals(EmbeddingStatus.PROCESSING)) {
			throw new RuntimeException("BusinessKnowledge is processing, please wait.");
		}

		// 非召回的不处理
		if (knowledge.getIsRecall() == null || knowledge.getIsRecall() == 0) {
			throw new RuntimeException("该业务知识未设为召回，请先设为召回后再重试");
		}

		try {
			syncToVectorStore(knowledge);
			knowledge.setEmbeddingStatus(EmbeddingStatus.COMPLETED);
			knowledge.setErrorMsg(null);
			businessKnowledgeMapper.updateById(knowledge);
		}
		catch (Exception e) {
			// 再次失败，更新错误信息
			knowledge.setEmbeddingStatus(EmbeddingStatus.FAILED);
			knowledge.setErrorMsg(e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage());
			businessKnowledgeMapper.updateById(knowledge);
			throw new RuntimeException("重试失败: " + e.getMessage());
		}

	}

}
