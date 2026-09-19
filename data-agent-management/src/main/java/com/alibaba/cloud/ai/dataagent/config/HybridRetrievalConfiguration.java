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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.ChromaKeywordIndexInitializer;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.KeywordIndexService;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.impl.LuceneKeywordIndexService;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.ChromaHybridRetrievalStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "chroma", matchIfMissing = true)
public class HybridRetrievalConfiguration {

	@Bean
	@ConditionalOnMissingBean(FusionStrategy.class)
	@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search", havingValue = "true")
	FusionStrategy rrfFusionStrategy(DataAgentProperties properties) {
		DataAgentProperties.VectorStoreProperties vectorStore = properties.getVectorStore();
		return new RrfFusionStrategy(vectorStore.getRrfK(), vectorStore.getDenseWeight(),
				vectorStore.getKeywordWeight());
	}

	@Bean
	@ConditionalOnMissingBean(KeywordIndexService.class)
	@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search", havingValue = "true")
	KeywordIndexService keywordIndexService(DataAgentProperties properties, ObjectMapper objectMapper) {
		return new LuceneKeywordIndexService(Path.of(properties.getVectorStore().getKeywordIndexPath()), objectMapper);
	}

	@Bean
	@ConditionalOnMissingBean(HybridRetrievalStrategy.class)
	@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search", havingValue = "true")
	HybridRetrievalStrategy chromaHybridRetrievalStrategy(
			@Qualifier("dbOperationExecutor") ExecutorService executorService, VectorStore vectorStore,
			KeywordIndexService keywordIndexService, FusionStrategy fusionStrategy, DataAgentProperties properties) {
		DataAgentProperties.VectorStoreProperties config = properties.getVectorStore();
		return new ChromaHybridRetrievalStrategy(executorService, vectorStore, keywordIndexService, fusionStrategy,
				config.getHybridSearchTimeoutMs(), config.getVectorCandidateTopK(), config.getKeywordCandidateTopK());
	}

	@Bean
	@ConditionalOnProperty(name = "spring.ai.alibaba.data-agent.vector-store.enable-hybrid-search", havingValue = "true")
	ChromaKeywordIndexInitializer chromaKeywordIndexInitializer(ChromaApi chromaApi,
			KeywordIndexService keywordIndexService, DataAgentProperties properties, Environment environment) {
		return new ChromaKeywordIndexInitializer(chromaApi, keywordIndexService, properties, environment);
	}

}
