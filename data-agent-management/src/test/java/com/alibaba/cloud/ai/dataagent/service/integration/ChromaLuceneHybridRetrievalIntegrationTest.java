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
package com.alibaba.cloud.ai.dataagent.service.integration;

import com.alibaba.cloud.ai.dataagent.config.ChromaSchemaInitializer;
import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.impl.LuceneKeywordIndexService;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.ChromaHybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "dataagent.chroma.integration", matches = "true")
class ChromaLuceneHybridRetrievalIntegrationTest {

	private static final String TENANT = "SpringAiTenant";

	private static final String DATABASE = "SpringAiDatabase";

	@TempDir
	Path tempDirectory;

	private ChromaApi chromaApi;

	private ChromaVectorStore vectorStore;

	private LuceneKeywordIndexService keywordIndex;

	private ExecutorService executorService;

	private String collectionName;

	@BeforeEach
	void setUp() throws Exception {
		String chromaUrl = System.getProperty("dataagent.chroma.url", "http://localhost:9000");
		collectionName = "hybrid_test_" + UUID.randomUUID().toString().replace("-", "");
		chromaApi = ChromaApi.builder().baseUrl(chromaUrl).build();
		new ChromaSchemaInitializer(chromaApi, TENANT, DATABASE, collectionName).initialize();
		vectorStore = ChromaVectorStore.builder(chromaApi, new KeywordEmbeddingModel())
			.tenantName(TENANT)
			.databaseName(DATABASE)
			.collectionName(collectionName)
			.initializeSchema(true)
			.build();
		vectorStore.afterPropertiesSet();
		keywordIndex = new LuceneKeywordIndexService(tempDirectory.resolve("lucene"), new ObjectMapper());
		executorService = Executors.newFixedThreadPool(2);
	}

	@AfterEach
	void tearDown() {
		if (executorService != null) {
			executorService.shutdownNow();
		}
		if (keywordIndex != null) {
			keywordIndex.close();
		}
		if (chromaApi != null && collectionName != null) {
			try {
				chromaApi.deleteCollection(TENANT, DATABASE, collectionName);
			}
			catch (RuntimeException ignored) {
				// Preserve the original test result when cleanup encounters a missing collection.
			}
		}
	}

	@Test
	void denseAndKeywordCandidatesAreFusedByRrf() {
		Document semantic = new Document("semantic", "order shipment status", Map.of("agentId", "1"));
		Document lexical = new Document("lexical", "invoice code ZX-900 payment policy", Map.of("agentId", "1"));
		Document otherAgent = new Document("other", "invoice code ZX-900 private policy", Map.of("agentId", "2"));
		List<Document> documents = List.of(semantic, lexical, otherAgent);
		vectorStore.add(documents);
		keywordIndex.upsert(documents);

		ChromaHybridRetrievalStrategy strategy = new ChromaHybridRetrievalStrategy(executorService, vectorStore,
				keywordIndex, new RrfFusionStrategy(60, 0.7, 0.3), 3000, 10, 10);
		HybridSearchRequest request = HybridSearchRequest.builder()
			.query("order ZX-900")
			.topK(2)
			.similarityThreshold(0.8)
			.filterExpression(new FilterExpressionBuilder().eq("agentId", "1").build())
			.build();

		assertThat(strategy.retrieve(request)).extracting(Document::getId)
			.containsExactlyInAnyOrder("semantic", "lexical");
	}

}
