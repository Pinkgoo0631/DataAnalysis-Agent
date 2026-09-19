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
package com.alibaba.cloud.ai.dataagent.service.vector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataDocumentRetrieverChromaTest {

	@Test
	void find_readsDocumentsDirectlyFromChromaByMetadata() {
		ChromaApi chromaApi = mock(ChromaApi.class);
		ChromaVectorStore vectorStore = mock(ChromaVectorStore.class);
		MockEnvironment environment = new MockEnvironment()
			.withProperty("spring.ai.vectorstore.chroma.tenant-name", "tenant")
			.withProperty("spring.ai.vectorstore.chroma.database-name", "database")
			.withProperty("spring.ai.vectorstore.chroma.collection-name", "data_agent");
		Map<String, Object> where = Map.of("agentId", Map.of("$eq", "1"));
		when(chromaApi.getCollection("tenant", "database", "data_agent"))
			.thenReturn(new ChromaApi.Collection("collection-id", "data_agent", Map.of()));
		when(chromaApi.where(any(String.class))).thenReturn(where);
		when(chromaApi.getEmbeddings(eq("tenant"), eq("database"), eq("collection-id"),
				any(ChromaApi.GetEmbeddingsRequest.class)))
			.thenReturn(new ChromaApi.GetEmbeddingResponse(List.of("doc-1"), null, List.of("document text"),
					List.of(Map.of("agentId", "1"))));

		var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
		var retriever = new MetadataDocumentRetriever(environment, chromaApi);

		assertThat(retriever.find(vectorStore, filter, 10)).singleElement().satisfies(document -> {
			assertThat(document.getId()).isEqualTo("doc-1");
			assertThat(document.getText()).isEqualTo("document text");
			assertThat(document.getMetadata()).containsEntry("agentId", "1");
		});
		verify(chromaApi).getEmbeddings(eq("tenant"), eq("database"), eq("collection-id"),
				any(ChromaApi.GetEmbeddingsRequest.class));
	}

}
