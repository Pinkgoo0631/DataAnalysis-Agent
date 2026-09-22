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

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.util.Assert;

/**
 * Creates the Chroma tenant, database and collection before Spring AI initializes its
 * vector store. Spring AI 1.1.2 expects missing resources to be returned as {@code null},
 * while Chroma 1.0 responds with HTTP 404.
 */
@Slf4j
public final class ChromaSchemaInitializer {

	private final ChromaApi chromaApi;

	private final String tenantName;

	private final String databaseName;

	private final String collectionName;

	public ChromaSchemaInitializer(ChromaApi chromaApi, String tenantName, String databaseName, String collectionName) {
		Assert.notNull(chromaApi, "ChromaApi cannot be null");
		Assert.hasText(tenantName, "Chroma tenant name cannot be empty");
		Assert.hasText(databaseName, "Chroma database name cannot be empty");
		Assert.hasText(collectionName, "Chroma collection name cannot be empty");
		this.chromaApi = chromaApi;
		this.tenantName = tenantName;
		this.databaseName = databaseName;
		this.collectionName = collectionName;
	}

	public void initialize() {
		ensureExists("tenant " + tenantName, () -> chromaApi.getTenant(tenantName),
				() -> chromaApi.createTenant(tenantName));
		ensureExists("database " + databaseName, () -> chromaApi.getDatabase(tenantName, databaseName),
				() -> chromaApi.createDatabase(tenantName, databaseName));
		ensureExists("collection " + collectionName,
				() -> chromaApi.getCollection(tenantName, databaseName, collectionName),
				() -> chromaApi.createCollection(tenantName, databaseName,
						new ChromaApi.CreateCollectionRequest(collectionName)));
	}

	private void ensureExists(String resource, Supplier<?> finder, Runnable creator) {
		if (!isMissing(finder)) {
			return;
		}
		try {
			creator.run();
			log.info("Created Chroma {}", resource);
		}
		catch (RuntimeException ex) {
			if (!hasStatus(ex, HttpStatus.CONFLICT)) {
				throw ex;
			}
			log.info("Chroma {} was created concurrently", resource);
		}
	}

	private boolean isMissing(Supplier<?> finder) {
		try {
			return finder.get() == null;
		}
		catch (RuntimeException ex) {
			if (hasStatus(ex, HttpStatus.NOT_FOUND)) {
				return true;
			}
			throw ex;
		}
	}

	private boolean hasStatus(Throwable error, HttpStatus status) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof HttpClientErrorException httpError && httpError.getStatusCode() == status) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

}
