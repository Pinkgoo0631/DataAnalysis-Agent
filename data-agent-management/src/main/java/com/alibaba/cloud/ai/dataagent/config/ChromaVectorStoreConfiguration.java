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

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chroma vector store configuration with Chroma 1.0 schema bootstrap compatibility. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChromaVectorStoreProperties.class)
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "chroma", matchIfMissing = true)
public class ChromaVectorStoreConfiguration {

	@Bean
	@ConditionalOnMissingBean(VectorStore.class)
	ChromaVectorStore vectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi,
			ChromaVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> observationConvention, BatchingStrategy batchingStrategy) {
		if (properties.isInitializeSchema()) {
			new ChromaSchemaInitializer(chromaApi, properties.getTenantName(), properties.getDatabaseName(),
					properties.getCollectionName())
				.initialize();
		}

		ChromaVectorStore.Builder builder = ChromaVectorStore.builder(chromaApi, embeddingModel)
			.collectionName(properties.getCollectionName())
			.databaseName(properties.getDatabaseName())
			.tenantName(properties.getTenantName())
			.initializeSchema(properties.isInitializeSchema());
		builder.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));
		builder.customObservationConvention(observationConvention.getIfAvailable(() -> null));
		builder.batchingStrategy(batchingStrategy);
		return builder.build();
	}

}
