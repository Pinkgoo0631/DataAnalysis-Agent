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
package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl;

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.KeywordIndexService;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.AbstractHybridRetrievalStrategy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/** Application-side hybrid retrieval: Chroma dense vectors plus Lucene BM25. */
public final class ChromaHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

	private final KeywordIndexService keywordIndexService;

	public ChromaHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
			KeywordIndexService keywordIndexService, FusionStrategy fusionStrategy, long timeoutMs,
			int vectorCandidateTopK, int keywordCandidateTopK) {
		super(executorService, vectorStore, fusionStrategy, timeoutMs, vectorCandidateTopK, keywordCandidateTopK);
		this.keywordIndexService = keywordIndexService;
	}

	@Override
	public List<Document> getDocumentsByKeywords(HybridSearchRequest request, int limit) {
		return keywordIndexService.search(request, limit);
	}

}
