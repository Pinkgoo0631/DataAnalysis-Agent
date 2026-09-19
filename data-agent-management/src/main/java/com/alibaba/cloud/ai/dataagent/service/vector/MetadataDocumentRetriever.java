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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaFilterExpressionConverter;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest.Include.DOCUMENTS;
import static org.springframework.ai.chroma.vectorstore.ChromaApi.QueryRequest.Include.METADATAS;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_COLLECTION_NAME;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_DATABASE_NAME;
import static org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants.DEFAULT_TENANT_NAME;

/** Provider-specific exact metadata retrieval without creating a query embedding. */
@Component
public class MetadataDocumentRetriever {

	private final Environment environment;

	private final ChromaApi chromaApi;

	public MetadataDocumentRetriever(Environment environment) {
		this(environment, (ChromaApi) null);
	}

	MetadataDocumentRetriever(Environment environment, ChromaApi chromaApi) {
		this.environment = environment;
		this.chromaApi = chromaApi;
	}

	@Autowired
	public MetadataDocumentRetriever(Environment environment, ObjectProvider<ChromaApi> chromaApiProvider) {
		this(environment, chromaApiProvider.getIfAvailable());
	}

	public List<Document> find(VectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		if (vectorStore instanceof MetadataAwareSimpleVectorStore simpleVectorStore) {
			return simpleVectorStore.findByFilter(filterExpression, limit);
		}
		if (vectorStore instanceof MilvusVectorStore milvusVectorStore) {
			return findInMilvus(milvusVectorStore, filterExpression, limit);
		}
		if (vectorStore instanceof ChromaVectorStore) {
			return findInChroma(filterExpression, limit);
		}
		throw new IllegalArgumentException(
				"Exact metadata retrieval is not supported for " + vectorStore.getClass().getName());
	}

	private List<Document> findInChroma(Filter.Expression filterExpression, int limit) {
		if (chromaApi == null) {
			throw new IllegalStateException("Chroma API is unavailable");
		}
		String tenantName = environment.getProperty("spring.ai.vectorstore.chroma.tenant-name", DEFAULT_TENANT_NAME);
		String databaseName = environment.getProperty("spring.ai.vectorstore.chroma.database-name",
				DEFAULT_DATABASE_NAME);
		String collectionName = environment.getProperty("spring.ai.vectorstore.chroma.collection-name",
				DEFAULT_COLLECTION_NAME);
		ChromaApi.Collection collection = chromaApi.getCollection(tenantName, databaseName, collectionName);
		if (collection == null) {
			return List.of();
		}

		String convertedFilter = new ChromaFilterExpressionConverter().convertExpression(filterExpression);
		Map<String, Object> where = chromaApi.where(convertedFilter);
		ChromaApi.GetEmbeddingsRequest request = new ChromaApi.GetEmbeddingsRequest(null, where, limit, null,
				List.of(METADATAS, DOCUMENTS));
		ChromaApi.GetEmbeddingResponse response = chromaApi.getEmbeddings(tenantName, databaseName, collection.id(),
				request);
		return toDocuments(response);
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
				.metadata(new HashMap<>(sourceMetadata))
				.build());
		}
		return documents;
	}

	private <T> T valueAt(List<T> values, int index, T fallback) {
		return values != null && index < values.size() && values.get(index) != null ? values.get(index) : fallback;
	}

	private List<Document> findInMilvus(MilvusVectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		VectorStoreObservationContext context = vectorStore.createObservationContextBuilder("metadata-query").build();
		String idField = environment.getProperty("spring.ai.vectorstore.milvus.id-field-name",
				MilvusVectorStore.DOC_ID_FIELD_NAME);
		String contentField = environment.getProperty("spring.ai.vectorstore.milvus.content-field-name",
				MilvusVectorStore.CONTENT_FIELD_NAME);
		String metadataField = environment.getProperty("spring.ai.vectorstore.milvus.metadata-field-name",
				MilvusVectorStore.METADATA_FIELD_NAME);
		MilvusServiceClient client = vectorStore.<MilvusServiceClient>getNativeClient()
			.orElseThrow(() -> new IllegalStateException("Milvus native client is unavailable"));
		QueryParam query = QueryParam.newBuilder()
			.withDatabaseName(context.getNamespace())
			.withCollectionName(context.getCollectionName())
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withOutFields(List.of(idField, contentField, metadataField))
			.withExpr(vectorStore.filterExpressionConverter.convertExpression(filterExpression))
			.withLimit((long) limit)
			.build();
		R<QueryResults> response = client.query(query);
		if (response.getException() != null) {
			throw new IllegalStateException("Milvus metadata query failed", response.getException());
		}
		Gson gson = new Gson();
		Type metadataType = new TypeToken<Map<String, Object>>() {
		}.getType();
		return new QueryResultsWrapper(response.getData()).getRowRecords().stream().map(row -> {
			JsonObject metadata = (JsonObject) row.get(metadataField);
			return Document.builder()
				.id(String.valueOf(row.get(idField)))
				.text((String) row.get(contentField))
				.metadata(metadata == null ? Map.of() : gson.fromJson(metadata, metadataType))
				.build();
		}).toList();
	}

}
