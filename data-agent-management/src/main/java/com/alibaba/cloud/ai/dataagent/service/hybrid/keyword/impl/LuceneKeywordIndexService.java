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
package com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.impl;

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.keyword.KeywordIndexService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/** Persistent Lucene BM25 index for Chinese and mixed Chinese-English documents. */
public final class LuceneKeywordIndexService implements KeywordIndexService, AutoCloseable {

	private static final String ID_FIELD = "_document_id";

	private static final String CONTENT_FIELD = "_content";

	private static final String METADATA_FIELD = "_metadata";

	private static final String METADATA_KEY_FIELD = "_metadata_key";

	private static final String METADATA_PREFIX = "metadata.";

	private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	private final Analyzer analyzer;

	private final Directory directory;

	private final IndexWriter writer;

	public LuceneKeywordIndexService(Path indexPath, ObjectMapper objectMapper) {
		Assert.notNull(indexPath, "Lucene index path cannot be null");
		Assert.notNull(objectMapper, "ObjectMapper cannot be null");
		this.objectMapper = objectMapper;
		this.analyzer = new SmartChineseAnalyzer();
		try {
			Path normalizedPath = indexPath.toAbsolutePath().normalize();
			Files.createDirectories(normalizedPath);
			this.directory = FSDirectory.open(normalizedPath);
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
			config.setSimilarity(new BM25Similarity());
			this.writer = new IndexWriter(directory, config);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to open Lucene keyword index at " + indexPath, ex);
		}
	}

	@Override
	public synchronized void upsert(List<Document> documents) {
		if (documents == null || documents.isEmpty()) {
			return;
		}
		try {
			for (Document document : documents) {
				Assert.hasText(document.getId(), "Document ID cannot be empty");
				writer.updateDocument(new Term(ID_FIELD, document.getId()), toLuceneDocument(document));
			}
			writer.commit();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to update Lucene keyword index", ex);
		}
	}

	@Override
	public synchronized void deleteByIds(List<String> documentIds) {
		if (documentIds == null || documentIds.isEmpty()) {
			return;
		}
		try {
			Term[] terms = documentIds.stream().filter(StringUtils::hasText).map(id -> new Term(ID_FIELD, id))
				.toArray(Term[]::new);
			if (terms.length > 0) {
				writer.deleteDocuments(terms);
				writer.commit();
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to delete documents from Lucene keyword index", ex);
		}
	}

	@Override
	public synchronized void deleteByFilter(Filter.Expression filterExpression) {
		Assert.notNull(filterExpression, "Filter expression cannot be null");
		try {
			writer.deleteDocuments(toFilterQuery(filterExpression));
			writer.commit();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to delete filtered documents from Lucene keyword index", ex);
		}
	}

	@Override
	public List<Document> search(HybridSearchRequest request, int limit) {
		Assert.notNull(request, "Hybrid search request cannot be null");
		if (!StringUtils.hasText(request.getQuery()) || limit <= 0) {
			return List.of();
		}

		try (DirectoryReader reader = DirectoryReader.open(writer)) {
			IndexSearcher searcher = new IndexSearcher(reader);
			searcher.setSimilarity(new BM25Similarity());
			QueryParser parser = new QueryParser(CONTENT_FIELD, analyzer);
			Query contentQuery = parser.parse(QueryParser.escape(request.getQuery()));
			BooleanQuery.Builder query = new BooleanQuery.Builder().add(contentQuery, BooleanClause.Occur.MUST);
			if (request.getFilterExpression() != null) {
				query.add(toFilterQuery(request.getFilterExpression()), BooleanClause.Occur.FILTER);
			}

			ScoreDoc[] hits = searcher.search(query.build(), limit).scoreDocs;
			List<Document> results = new ArrayList<>(hits.length);
			for (ScoreDoc hit : hits) {
				org.apache.lucene.document.Document stored = searcher.storedFields().document(hit.doc);
				results.add(fromLuceneDocument(stored));
			}
			return results;
		}
		catch (IOException | ParseException ex) {
			throw new IllegalStateException("Lucene keyword search failed", ex);
		}
	}

	@Override
	public long count() {
		return writer.getDocStats().numDocs;
	}

	@Override
	public synchronized void clear() {
		try {
			writer.deleteAll();
			writer.commit();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to clear Lucene keyword index", ex);
		}
	}

	@Override
	public void close() {
		try {
			writer.close();
			directory.close();
			analyzer.close();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to close Lucene keyword index", ex);
		}
	}

	private org.apache.lucene.document.Document toLuceneDocument(Document source) throws JsonProcessingException {
		org.apache.lucene.document.Document target = new org.apache.lucene.document.Document();
		target.add(new StringField(ID_FIELD, source.getId(), Field.Store.YES));
		target.add(new TextField(CONTENT_FIELD, source.getText() == null ? "" : source.getText(), Field.Store.YES));
		Map<String, Object> metadata = source.getMetadata() == null ? Map.of() : source.getMetadata();
		target.add(new StoredField(METADATA_FIELD, objectMapper.writeValueAsString(metadata)));
		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			target.add(new StringField(METADATA_KEY_FIELD, entry.getKey(), Field.Store.NO));
			for (Object value : values(entry.getValue())) {
				target.add(new StringField(metadataField(entry.getKey()), canonicalValue(value), Field.Store.NO));
			}
		}
		return target;
	}

	private Document fromLuceneDocument(org.apache.lucene.document.Document source) throws JsonProcessingException {
		String metadataJson = source.get(METADATA_FIELD);
		Map<String, Object> metadata = StringUtils.hasText(metadataJson)
				? objectMapper.readValue(metadataJson, METADATA_TYPE) : Map.of();
		return Document.builder()
			.id(source.get(ID_FIELD))
			.text(source.get(CONTENT_FIELD))
			.metadata(metadata)
			.build();
	}

	private Query toFilterQuery(Filter.Expression expression) {
		return switch (expression.type()) {
			case AND -> combine(expression, BooleanClause.Occur.MUST);
			case OR -> combine(expression, BooleanClause.Occur.SHOULD);
			case EQ -> equalityQuery(expression);
			case NE -> excluding(equalityQuery(expression));
			case IN -> inQuery(expression);
			case NIN -> excluding(inQuery(expression));
			case NOT -> excluding(toQuery(expression.left()));
			case ISNULL -> excluding(existsQuery(key(expression.left())));
			case ISNOTNULL -> existsQuery(key(expression.left()));
			default -> throw new IllegalArgumentException(
					"Lucene keyword index does not support filter operator " + expression.type());
		};
	}

	private Query combine(Filter.Expression expression, BooleanClause.Occur occur) {
		BooleanQuery.Builder query = new BooleanQuery.Builder().add(toQuery(expression.left()), occur)
			.add(toQuery(expression.right()), occur);
		if (occur == BooleanClause.Occur.SHOULD) {
			query.setMinimumNumberShouldMatch(1);
		}
		return query.build();
	}

	private Query equalityQuery(Filter.Expression expression) {
		String key = key(expression.left());
		Object value = value(expression.right());
		return new TermQuery(new Term(metadataField(key), canonicalValue(value)));
	}

	private Query inQuery(Filter.Expression expression) {
		String key = key(expression.left());
		List<BytesRef> terms = values(value(expression.right())).stream()
			.map(this::canonicalValue)
			.map(BytesRef::new)
			.toList();
		return new TermInSetQuery(metadataField(key), terms);
	}

	private Query existsQuery(String key) {
		return new TermQuery(new Term(METADATA_KEY_FIELD, key));
	}

	private Query excluding(Query excluded) {
		return new BooleanQuery.Builder().add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
			.add(excluded, BooleanClause.Occur.MUST_NOT)
			.build();
	}

	private Query toQuery(Filter.Operand operand) {
		if (operand instanceof Filter.Expression expression) {
			return toFilterQuery(expression);
		}
		if (operand instanceof Filter.Group group) {
			return toFilterQuery(group.content());
		}
		throw new IllegalArgumentException("Expected a filter expression but got " + operand);
	}

	private String key(Filter.Operand operand) {
		if (operand instanceof Filter.Key key) {
			return key.key();
		}
		throw new IllegalArgumentException("Expected a metadata key but got " + operand);
	}

	private Object value(Filter.Operand operand) {
		if (operand instanceof Filter.Value value) {
			return value.value();
		}
		throw new IllegalArgumentException("Expected a metadata value but got " + operand);
	}

	private List<Object> values(Object value) {
		if (value == null) {
			return List.of();
		}
		if (value instanceof Collection<?> collection) {
			return new ArrayList<>(collection);
		}
		if (value.getClass().isArray()) {
			List<Object> result = new ArrayList<>(Array.getLength(value));
			for (int index = 0; index < Array.getLength(value); index++) {
				result.add(Array.get(value, index));
			}
			return result;
		}
		return List.of(value);
	}

	private String metadataField(String key) {
		return METADATA_PREFIX + key;
	}

	private String canonicalValue(Object value) {
		if (value instanceof Number number) {
			return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
		}
		if (value instanceof Boolean bool) {
			return bool.toString().toLowerCase(Locale.ROOT);
		}
		return String.valueOf(value);
	}

}
