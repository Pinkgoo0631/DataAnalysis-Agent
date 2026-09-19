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

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientProperties;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaApiProperties;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaVectorStoreProperties;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreConfigurationTest {

	private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

	@Test
	void vectorStoreProfiles_declareExpectedTypes() throws Exception {
		assertThat(property("application.yml", "spring.ai.vectorstore.type")).isEqualTo("chroma");
		assertThat(property("application-simple.yml", "spring.ai.vectorstore.type")).isEqualTo("simple");
		assertThat(property("application-h2.yml", "spring.ai.vectorstore.type")).isNull();
		assertThat(property("application-milvus.yml", "spring.ai.vectorstore.type")).isEqualTo("milvus");
	}

	@Test
	void defaultProfile_bindsChromaConnectionAndStoreProperties() throws Exception {
		StandardEnvironment environment = environmentFor("application.yml");

		ChromaApiProperties client = Binder.get(environment)
			.bind("spring.ai.vectorstore.chroma.client", ChromaApiProperties.class)
			.orElseThrow(() -> new IllegalStateException("Chroma client properties did not bind"));
		ChromaVectorStoreProperties store = Binder.get(environment)
			.bind("spring.ai.vectorstore.chroma", ChromaVectorStoreProperties.class)
			.orElseThrow(() -> new IllegalStateException("Chroma vector-store properties did not bind"));

		assertThat(client.getHost()).isEqualTo("http://localhost");
		assertThat(client.getPort()).isEqualTo(8000);
		assertThat(store.getTenantName()).isEqualTo("SpringAiTenant");
		assertThat(store.getDatabaseName()).isEqualTo("SpringAiDatabase");
		assertThat(store.getCollectionName()).isEqualTo("data_agent");
		assertThat(store.isInitializeSchema()).isTrue();

		DataAgentProperties dataAgent = Binder.get(environment)
			.bind("spring.ai.alibaba.data-agent", DataAgentProperties.class)
			.orElseThrow(() -> new IllegalStateException("DataAgent properties did not bind"));
		assertThat(dataAgent.getVectorStore().isEnableHybridSearch()).isTrue();
		assertThat(dataAgent.getVectorStore().getVectorCandidateTopK()).isEqualTo(30);
		assertThat(dataAgent.getVectorStore().getKeywordCandidateTopK()).isEqualTo(30);
		assertThat(dataAgent.getVectorStore().getDenseWeight()).isEqualTo(0.7);
		assertThat(dataAgent.getVectorStore().getKeywordWeight()).isEqualTo(0.3);
		assertThat(dataAgent.getVectorStore().getRerankTopN()).isEqualTo(8);
	}

	@Test
	void milvusProfile_bindsConnectionPropertiesUsedBySpringAi() throws Exception {
		StandardEnvironment environment = new StandardEnvironment();
		List<PropertySource<?>> sources = loader.load("application-milvus.yml",
				new ClassPathResource("application-milvus.yml"));
		for (PropertySource<?> source : sources) {
			environment.getPropertySources().addFirst(source);
		}

		MilvusServiceClientProperties properties = Binder.get(environment)
			.bind("spring.ai.vectorstore.milvus.client", MilvusServiceClientProperties.class)
			.orElseThrow(() -> new IllegalStateException("Milvus client properties did not bind"));

		assertThat(properties.getHost()).isEqualTo("127.0.0.1");
		assertThat(properties.getPort()).isEqualTo(19530);
	}

	@Test
	void replaceableRuntimeServices_areConditionalOnMissingBean() throws Exception {
		assertConditional("llmService", AiModelRegistry.class);
		assertConditional("fileStorageService", FileStorageProperties.class, OssStorageProperties.class);
	}

	private Object property(String resource, String name) throws Exception {
		List<PropertySource<?>> sources = loader.load(resource, new ClassPathResource(resource));
		return sources.stream()
			.map(source -> source.getProperty(name))
			.filter(value -> value != null)
			.findFirst()
			.orElse(null);
	}

	private StandardEnvironment environmentFor(String resource) throws Exception {
		StandardEnvironment environment = new StandardEnvironment();
		List<PropertySource<?>> sources = loader.load(resource, new ClassPathResource(resource));
		for (PropertySource<?> source : sources) {
			environment.getPropertySources().addFirst(source);
		}
		return environment;
	}

	private void assertConditional(String methodName, Class<?>... parameterTypes) throws Exception {
		Method method = DataAgentConfiguration.class.getDeclaredMethod(methodName, parameterTypes);
		ConditionalOnMissingBean condition = method.getAnnotation(ConditionalOnMissingBean.class);
		assertThat(condition).isNotNull();
		assertThat(condition.value()).isNotEmpty();
	}

}
