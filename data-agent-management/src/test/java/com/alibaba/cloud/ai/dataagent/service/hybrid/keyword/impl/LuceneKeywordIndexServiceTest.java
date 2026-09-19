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
package com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.impl;

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneKeywordIndexServiceTest {

	@TempDir
	Path tempDirectory;

	private LuceneKeywordIndexService index;

	@BeforeEach
	void setUp() {
		index = new LuceneKeywordIndexService(tempDirectory.resolve("index"), new ObjectMapper());
	}

	@AfterEach
	void tearDown() {
		index.close();
	}

	@Test
	void searchUsesBm25AndMetadataFilters() {
		Document matchingAgent = new Document("doc-1", "退款流程和退款时效说明",
				Map.of("agentId", "1", "vectorType", "agentKnowledge", "knowledgeId", 7));
		Document otherAgent = new Document("doc-2", "退款申请操作指南",
				Map.of("agentId", "2", "vectorType", "agentKnowledge", "knowledgeId", 8));
		index.upsert(List.of(matchingAgent, otherAgent));

		FilterExpressionBuilder filters = new FilterExpressionBuilder();
		Filter.Expression filter = filters.and(filters.eq("agentId", "1"), filters.in("knowledgeId", 7, 9)).build();
		HybridSearchRequest request = HybridSearchRequest.builder()
			.query("退款")
			.topK(5)
			.filterExpression(filter)
			.build();

		assertThat(index.search(request, 10)).extracting(Document::getId).containsExactly("doc-1");
	}

	@Test
	void upsertReplacesByIdAndFilterDeleteRemovesTheDocument() {
		index.upsert(List.of(new Document("doc-1", "旧版发票说明", Map.of("agentId", "1"))));
		index.upsert(List.of(new Document("doc-1", "新版发票申请流程", Map.of("agentId", "1"))));

		assertThat(index.count()).isEqualTo(1);
		HybridSearchRequest request = HybridSearchRequest.builder().query("新版发票").topK(5).build();
		assertThat(index.search(request, 5)).extracting(Document::getText).containsExactly("新版发票申请流程");

		index.deleteByFilter(new FilterExpressionBuilder().eq("agentId", "1").build());
		assertThat(index.count()).isZero();
	}

}
