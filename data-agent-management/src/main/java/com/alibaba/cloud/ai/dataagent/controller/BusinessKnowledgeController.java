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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.CreateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge.UpdateBusinessKnowledgeDTO;
import com.alibaba.cloud.ai.dataagent.service.business.BusinessKnowledgeService;
import com.alibaba.cloud.ai.dataagent.service.auth.ResourceOwnershipService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.BatchImportResult;
import com.alibaba.cloud.ai.dataagent.vo.BusinessKnowledgeVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/business-knowledge")
@AllArgsConstructor
public class BusinessKnowledgeController {

	private final BusinessKnowledgeService businessKnowledgeService;

	private final ResourceOwnershipService ownershipService;

	@GetMapping
	public ApiResponse<List<BusinessKnowledgeVO>> list(@RequestParam(value = "agentId") String agentIdStr,
			@RequestParam(value = "keyword", required = false) String keyword, Authentication authentication) {
		List<BusinessKnowledgeVO> result;
		Long agentId = Long.parseLong(agentIdStr);
		ownershipService.requireAgent(agentId, authentication);

		if (StringUtils.hasText(keyword)) {
			result = businessKnowledgeService.searchKnowledge(agentId, keyword);
		}
		else {
			result = businessKnowledgeService.getKnowledge(agentId);
		}
		return ApiResponse.success("success list businessKnowledge", result);
	}

	@GetMapping("/{id}")
	public ApiResponse<BusinessKnowledgeVO> get(@PathVariable(value = "id") Long id, Authentication authentication) {
		ownershipService.requireBusinessKnowledge(id, authentication);
		BusinessKnowledgeVO vo = businessKnowledgeService.getKnowledgeById(id);
		if (vo == null) {
			return ApiResponse.error("businessKnowledge not found");
		}
		return ApiResponse.success("success get businessKnowledge", vo);
	}

	@PostMapping
	public ApiResponse<BusinessKnowledgeVO> create(@RequestBody @Validated CreateBusinessKnowledgeDTO knowledge,
			Authentication authentication) {
		ownershipService.requireAgent(knowledge.getAgentId(), authentication);
		return ApiResponse.success("success create businessKnowledge",
				businessKnowledgeService.addKnowledge(knowledge));
	}

	@PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<ApiResponse<BatchImportResult>> importCsv(@RequestPart("file") FilePart file,
			@RequestPart("agentId") String agentId, Authentication authentication) {
		Long parsedAgentId = Long.valueOf(agentId);
		ownershipService.requireAgent(parsedAgentId, authentication);
		String filename = file.filename();

		return DataBufferUtils.join(file.content()).flatMap(dataBuffer -> {
			byte[] bytes = new byte[dataBuffer.readableByteCount()];
			dataBuffer.read(bytes);
			DataBufferUtils.release(dataBuffer);

			return Mono.fromCallable(() -> {
				BatchImportResult result = businessKnowledgeService
					.importFromCsv(new ByteArrayInputStream(bytes), filename, parsedAgentId);
				return ApiResponse.success("CSV导入完成", result);
			}).subscribeOn(Schedulers.boundedElastic());
		}).onErrorResume(IllegalArgumentException.class, e -> {
			log.warn("CSV import rejected: {}", e.getMessage());
			return Mono.just(ApiResponse.error("CSV导入失败：" + e.getMessage()));
		}).onErrorResume(Exception.class, e -> {
			log.error("CSV import failed", e);
			return Mono.just(ApiResponse.error("CSV导入失败，请检查文件后重试"));
		});
	}

	@GetMapping("/template/csv")
	public ResponseEntity<byte[]> downloadCsvTemplate() {
		String csv = "\uFEFF业务名词,描述,同义词,是否召回\r\nGMV,商品交易总额,\"成交总额,交易额\",true\r\n";
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
		headers.setContentDisposition(ContentDisposition.attachment()
			.filename("business_knowledge_template.csv", StandardCharsets.UTF_8)
			.build());
		return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
	}

	@PutMapping("/{id}")
	public ApiResponse<BusinessKnowledgeVO> update(@PathVariable(value = "id") Long id,
			@RequestBody UpdateBusinessKnowledgeDTO knowledge, Authentication authentication) {
		ownershipService.requireBusinessKnowledge(id, authentication);

		return ApiResponse.success("success update businessKnowledge",
				businessKnowledgeService.updateKnowledge(id, knowledge));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Boolean> delete(@PathVariable(value = "id") Long id, Authentication authentication) {
		ownershipService.requireBusinessKnowledge(id, authentication);
		if (businessKnowledgeService.getKnowledgeById(id) == null) {
			return ApiResponse.error("businessKnowledge not found");
		}
		businessKnowledgeService.deleteKnowledge(id);
		return ApiResponse.success("success delete businessKnowledge");
	}

	@PostMapping("/recall/{id}")
	public ApiResponse<Boolean> recallKnowledge(@PathVariable(value = "id") Long id,
			@RequestParam(value = "isRecall") Boolean isRecall, Authentication authentication) {
		ownershipService.requireBusinessKnowledge(id, authentication);
		businessKnowledgeService.recallKnowledge(id, isRecall);
		return ApiResponse.success("success update recall businessKnowledge");
	}

	@PostMapping("/refresh-vector-store")
	public ApiResponse<Boolean> refreshAllKnowledgeToVectorStore(@RequestParam(value = "agentId") String agentId,
			Authentication authentication) {
		// 校验 agentId 不为空和空字符串
		if (!StringUtils.hasText(agentId)) {
			return ApiResponse.error("agentId cannot be empty");
		}

		try {
			ownershipService.requireAgent(Long.valueOf(agentId), authentication);
			businessKnowledgeService.refreshAllKnowledgeToVectorStore(agentId);
			return ApiResponse.success("success refresh vector store");
		}
		catch (Exception e) {
			log.error("Failed to refresh vector store for agentId: {}", agentId, e);
			return ApiResponse.error("Failed to refresh vector store");
		}
	}

	@PostMapping("/retry-embedding/{id}")
	public ApiResponse<Boolean> retryEmbedding(@PathVariable(value = "id") Long id,
			Authentication authentication) {
		ownershipService.requireBusinessKnowledge(id, authentication);
		businessKnowledgeService.retryEmbedding(id);
		return ApiResponse.success("success retry embedding");
	}

}
