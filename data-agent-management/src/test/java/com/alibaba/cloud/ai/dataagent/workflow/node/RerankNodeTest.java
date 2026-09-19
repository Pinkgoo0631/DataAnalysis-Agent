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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.evidence.EvidenceContentBuilder;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RerankNodeTest {

	@Test
	void reorderIgnoresInvalidAndDuplicateIndicesAndKeepsUnrankedDocuments() {
		RerankNode node = nodeWithTopN(10);
		List<Document> candidates = List.of(document("a"), document("b"), document("c"));

		List<Document> reranked = node.reorder(candidates, List.of(2, 2, -1, 99, 0));

		assertThat(reranked).extracting(Document::getId).containsExactly("c", "a", "b");
	}

	@Test
	void limitKeepsOnlyConfiguredTopN() {
		RerankNode node = nodeWithTopN(2);

		assertThat(node.limit(List.of(document("a"), document("b"), document("c"))))
			.extracting(Document::getId)
			.containsExactly("a", "b");
	}

	private RerankNode nodeWithTopN(int topN) {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getVectorStore().setRerankTopN(topN);
		return new RerankNode(mock(LlmService.class), mock(JsonParseUtil.class), mock(EvidenceContentBuilder.class),
				properties);
	}

	private Document document(String id) {
		return Document.builder().id(id).text(id).build();
	}

}
