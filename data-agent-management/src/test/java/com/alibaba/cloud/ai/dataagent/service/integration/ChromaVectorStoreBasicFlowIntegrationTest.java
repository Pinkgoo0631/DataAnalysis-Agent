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
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chroma 最基础的真实集成流程：创建集合、写入文档、相似度检索、删除文档。
 *
 * <p>
 * 运行前先启动 Chroma，然后执行：
 * </p>
 *
 * <pre>
 * mvn -pl data-agent-management `
 *   '-Dtest=ChromaVectorStoreBasicFlowIntegrationTest' `
 *   '-Ddataagent.chroma.integration=true' test
 * </pre>
 *
 * <p>
 * 默认连接 {@code http://localhost:9000}。可以通过
 * {@code -Ddataagent.chroma.url=http://host:port} 覆盖。
 * </p>
 */
@EnabledIfSystemProperty(named = "dataagent.chroma.integration", matches = "true")
class ChromaVectorStoreBasicFlowIntegrationTest {

	private static final String DEFAULT_TENANT = "SpringAiTenant";

	private static final String DEFAULT_DATABASE = "SpringAiDatabase";

	private ChromaApi chromaApi;

	private ChromaVectorStore vectorStore;

	private String collectionName;

	@BeforeEach
	void setUp() throws Exception {
		String chromaUrl = System.getProperty("dataagent.chroma.url", "http://localhost:9000");
		collectionName = "data_agent_test_" + UUID.randomUUID().toString().replace("-", "");

		chromaApi = ChromaApi.builder().baseUrl(chromaUrl).build();
		new ChromaSchemaInitializer(chromaApi, DEFAULT_TENANT, DEFAULT_DATABASE, collectionName).initialize();
		vectorStore = ChromaVectorStore.builder(chromaApi, new KeywordEmbeddingModel())
			.tenantName(DEFAULT_TENANT)
			.databaseName(DEFAULT_DATABASE)
			.collectionName(collectionName)
			.initializeSchema(true)
			.build();

		// 手动创建 VectorStore 时显式触发初始化，自动配置场景由 Spring 完成。
		vectorStore.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() {
		if (chromaApi == null || collectionName == null) {
			return;
		}
		try {
			chromaApi.deleteCollection(DEFAULT_TENANT, DEFAULT_DATABASE, collectionName);
		}
		catch (RuntimeException ignored) {
			// 测试失败或集合已删除时，不让清理逻辑覆盖原始测试结果。
		}
	}

	@Test
	void addSearchAndDeleteDocumentsUsingChroma() {
		Document order = new Document("订单销售数据", Map.of("category", "order"));
		Document user = new Document("用户注册信息", Map.of("category", "user"));

		// 1. 写入：ChromaVectorStore 会先调用 EmbeddingModel 生成向量，再保存文档。
		vectorStore.add(List.of(order, user));

		// 2. 检索：查询“订单”应命中订单文档。
		SearchRequest searchRequest = SearchRequest.builder()
			.query("查询订单")
			.topK(1)
			.similarityThreshold(0.8)
			.build();
		assertThat(vectorStore.similaritySearch(searchRequest)).extracting(Document::getText)
			.containsExactly("订单销售数据");

		// 3. 删除：按文档 ID 删除，再检索时不应返回该文档。
		vectorStore.delete(List.of(order.getId()));
		assertThat(vectorStore.similaritySearch(searchRequest)).isEmpty();
	}

}
