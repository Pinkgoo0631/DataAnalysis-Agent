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
package com.alibaba.cloud.ai.dataagent.service.hybrid.keyword;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.document.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;

import static org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest.Include.DOCUMENTS;
import static org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest.Include.METADATAS;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_COLLECTION_NAME;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_DATABASE_NAME;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_TENANT_NAME;

/** Rebuilds the persisted Lucene keyword index from Chroma after an upgrade or restart. */
@Slf4j
public final class ChromaKeywordIndexInitializer implements ApplicationRunner {

	private static final int PAGE_SIZE = 500;

	private final ChromaApi chromaApi;

	private final KeywordIndexService keywordIndexService;

	private final DataAgentProperties properties;

	private final Environment environment;

	public ChromaKeywordIndexInitializer(ChromaApi chromaApi, KeywordIndexService keywordIndexService,
			DataAgentProperties properties, Environment environment) {
		this.chromaApi = chromaApi;
		this.keywordIndexService = keywordIndexService;
		this.properties = properties;
		this.environment = environment;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.getVectorStore().isKeywordIndexRebuildOnStart()) {
			return;
		}

		String tenant = environment.getProperty("spring.ai.vectorstore.chroma.tenant-name", DEFAULT_TENANT_NAME);
		String database = environment.getProperty("spring.ai.vectorstore.chroma.database-name", DEFAULT_DATABASE_NAME);
		String collectionName = environment.getProperty("spring.ai.vectorstore.chroma.collection-name",
				DEFAULT_COLLECTION_NAME);
		try {
			ChromaApi.Collection collection = chromaApi.getCollection(tenant, database, collectionName);
			if (collection == null) {
				log.info("Chroma collection {} does not exist yet; Lucene keyword index rebuild skipped", collectionName);
				return;
			}

			long documentCount = chromaApi.countEmbeddings(tenant, database, collection.id());
			keywordIndexService.clear();
			for (int offset = 0; offset < documentCount; offset += PAGE_SIZE) {
				ChromaApi.GetEmbeddingsRequest request = new ChromaApi.GetEmbeddingsRequest(null, null, PAGE_SIZE, offset,
						List.of(METADATAS, DOCUMENTS));
				ChromaApi.GetEmbeddingResponse response = chromaApi.getEmbeddings(tenant, database, collection.id(),
						request);
				List<Document> batch = toDocuments(response);
				if (batch.isEmpty()) {
					break;
				}
				keywordIndexService.upsert(batch);
			}
			log.info("Rebuilt Lucene keyword index from Chroma with {} documents", keywordIndexService.count());
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to rebuild Lucene keyword index from Chroma", ex);
		}
	}

	private List<Document> toDocuments(ChromaApi.GetEmbeddingResponse response) {
		if (response == null || response.ids() == null) {
			return List.of();
		}
		List<Document> documents = new ArrayList<>(response.ids().size());
		for (int index = 0; index < response.ids().size(); index++) {
			String text = valueAt(response.documents(), index, "");
			Map<String, String> sourceMetadata = valueAt(response.metadata(), index, Map.of());
			documents.add(Document.builder()
				.id(response.ids().get(index))
				.text(text)
				.metadata(normalizeMetadata(sourceMetadata))
				.build());
		}
		return documents;
	}

	private Map<String, Object> normalizeMetadata(Map<String, String> metadata) {
		Map<String, Object> normalized = new HashMap<>(metadata);
		normalizeNumber(normalized, DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
		normalizeNumber(normalized, DocumentMetadataConstant.DB_BUSINESS_TERM_ID);
		return normalized;
	}

	private void normalizeNumber(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		if (value instanceof String stringValue) {
			try {
				metadata.put(key, Long.valueOf(stringValue));
			}
			catch (NumberFormatException ignored) {
				// Keep non-numeric metadata unchanged.
			}
		}
	}

	private <T> T valueAt(List<T> values, int index, T fallback) {
		return values != null && index < values.size() && values.get(index) != null ? values.get(index) : fallback;
	}

}
