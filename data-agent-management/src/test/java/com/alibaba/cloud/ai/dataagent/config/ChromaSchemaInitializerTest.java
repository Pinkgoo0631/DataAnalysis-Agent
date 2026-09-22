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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChromaSchemaInitializerTest {

	@Mock
	private ChromaApi chromaApi;

	@Test
	void initialize_createsMissingTenantDatabaseAndCollectionWhenChromaReturns404() {
		RuntimeException notFound = wrappedHttpError(HttpStatus.NOT_FOUND);
		when(chromaApi.getTenant("tenant")).thenThrow(notFound);
		when(chromaApi.getDatabase("tenant", "database")).thenThrow(wrappedHttpError(HttpStatus.NOT_FOUND));
		when(chromaApi.getCollection("tenant", "database", "collection"))
			.thenThrow(wrappedHttpError(HttpStatus.NOT_FOUND));

		new ChromaSchemaInitializer(chromaApi, "tenant", "database", "collection").initialize();

		InOrder order = inOrder(chromaApi);
		order.verify(chromaApi).getTenant("tenant");
		order.verify(chromaApi).createTenant("tenant");
		order.verify(chromaApi).getDatabase("tenant", "database");
		order.verify(chromaApi).createDatabase("tenant", "database");
		order.verify(chromaApi).getCollection("tenant", "database", "collection");
		ArgumentCaptor<ChromaApi.CreateCollectionRequest> request = ArgumentCaptor
			.forClass(ChromaApi.CreateCollectionRequest.class);
		order.verify(chromaApi).createCollection(eq("tenant"), eq("database"), request.capture());
		assertEquals("collection", request.getValue().name());
	}

	@Test
	void initialize_keepsExistingSchema() {
		when(chromaApi.getTenant("tenant")).thenReturn(new ChromaApi.Tenant("tenant"));
		when(chromaApi.getDatabase("tenant", "database")).thenReturn(new ChromaApi.Database("database"));
		when(chromaApi.getCollection("tenant", "database", "collection"))
			.thenReturn(new ChromaApi.Collection("id", "collection", null));

		new ChromaSchemaInitializer(chromaApi, "tenant", "database", "collection").initialize();

		verify(chromaApi, never()).createTenant("tenant");
		verify(chromaApi, never()).createDatabase("tenant", "database");
		verify(chromaApi, never()).createCollection("tenant", "database",
				new ChromaApi.CreateCollectionRequest("collection"));
	}

	private RuntimeException wrappedHttpError(HttpStatus status) {
		return new RuntimeException("Chroma request failed", new HttpClientErrorException(status));
	}

}
