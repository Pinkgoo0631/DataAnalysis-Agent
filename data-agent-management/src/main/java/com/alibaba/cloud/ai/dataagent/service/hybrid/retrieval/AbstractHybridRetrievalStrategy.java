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
package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval;

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

@Slf4j
public abstract class AbstractHybridRetrievalStrategy implements HybridRetrievalStrategy {

	protected final ExecutorService executorService;

	protected final VectorStore vectorStore;

	protected final FusionStrategy fusionStrategy;

	private final long timeoutMs;

	private final int vectorCandidateTopK;

	private final int keywordCandidateTopK;

	protected AbstractHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy) {
		this(executorService, vectorStore, fusionStrategy, 3000L);
	}

	protected AbstractHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy, long timeoutMs) {
		this(executorService, vectorStore, fusionStrategy, timeoutMs, 30, 30);
	}

	protected AbstractHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			FusionStrategy fusionStrategy, long timeoutMs, int vectorCandidateTopK, int keywordCandidateTopK) {
		this.executorService = executorService;
		this.vectorStore = vectorStore;
		this.fusionStrategy = fusionStrategy;
		this.timeoutMs = timeoutMs;
		this.vectorCandidateTopK = vectorCandidateTopK;
		this.keywordCandidateTopK = keywordCandidateTopK;
		log.info(
				"Initialized AbstractHybridRetrievalStrategy with executorService: {}, vectorStore: {}, fusionStrategy: {}",
				executorService, vectorStore, fusionStrategy);
	}

	// 模板方法：并行执行向量搜索和关键词搜索，再融合两路结果。
	// 任一路超时或失败时降级使用另一路，避免局部故障导致整次召回失败。
	@Override
	public List<Document> retrieve(HybridSearchRequest request) {

		int requestedTopK = request.getTopK() == null ? 1 : Math.max(1, request.getTopK());
		int vectorLimit = Math.max(requestedTopK, vectorCandidateTopK);
		int keywordLimit = Math.max(requestedTopK, keywordCandidateTopK);
		SearchRequest vectorSearchRequest = request.toVectorSearchRequest(vectorLimit);

		// 异步执行向量搜索
		CompletableFuture<List<Document>> vectorSearchFuture = CompletableFuture.supplyAsync(() -> {
			List<Document> vectorResults = vectorStore.similaritySearch(vectorSearchRequest);
			log.debug("Vector Search completed. Found {} documents for SearchRequest: {}", vectorResults.size(),
					vectorSearchRequest);
			return vectorResults;
		}, executorService)
			.completeOnTimeout(List.of(), timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
			.exceptionally(error -> {
				log.warn("Vector search failed; falling back to keyword results: {}", error.getMessage());
				return List.of();
			});

		// 异步执行关键词搜索
		CompletableFuture<List<Document>> keywordSearchFuture = CompletableFuture.supplyAsync(() -> {
			List<Document> results = getDocumentsByKeywords(request, keywordLimit);
			log.debug("Keyword Search completed. Found {} documents, with query: {}", results.size(),
					request.getQuery());
			return results;
		}, executorService)
			.completeOnTimeout(List.of(), timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
			.exceptionally(error -> {
				log.warn("Keyword search failed; falling back to vector results: {}", error.getMessage());
				return List.of();
			});

		try {
			List<Document> vectorResults = vectorSearchFuture.get();

			// 等待关键词搜索完成
			List<Document> keywordResults = keywordSearchFuture.get();

			// 融合结果
			List<Document> finalDocuments = fusionStrategy.fuseResults(requestedTopK, vectorResults,
					keywordResults);
			log.debug("Fusion completed. Found {} documents", finalDocuments.size());
			return finalDocuments;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Search operation interrupted", e);
		}
		catch (ExecutionException e) {
			throw new RuntimeException("Error during parallel search execution", e);
		}

	}

	public abstract List<Document> getDocumentsByKeywords(HybridSearchRequest request, int limit);

}
