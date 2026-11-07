package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;

@Component
public class ChunkStoreAdapter implements ChunkStorePort {

	private static final FilterExpressionBuilder FILTER = new FilterExpressionBuilder();

	private final VectorStore vectorStore;

	public ChunkStoreAdapter(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	@Override
	public void save(String courseCode, UUID documentId, String fileName, List<Chunk> chunks) {
		List<Document> documents = chunks.stream()
				.map(chunk -> new Document(UUID.randomUUID().toString(), chunk.text(), Map.<String, Object>of(
						"course_code", courseCode,
						"document_id", documentId.toString(),
						"file_name", fileName,
						"page", chunk.page(),
						"chunk_index", chunk.index())))
				.toList();
		try {
			vectorStore.add(documents);
		}
		catch (ResourceAccessException | TransientAiException e) {
			throw unavailable(e);
		}
	}

	@Override
	public void deleteByDocumentId(UUID documentId) {
		vectorStore.delete(FILTER.eq("document_id", documentId.toString()).build());
	}

	@Override
	public List<ChunkMatch> search(String courseCode, String query, int k, double minScore) {
		SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(k)
				.similarityThreshold(minScore)
				.filterExpression(FILTER.eq("course_code", courseCode).build())
				.build();
		try {
			return vectorStore.similaritySearch(request).stream()
					.map(ChunkStoreAdapter::toMatch)
					.toList();
		}
		catch (ResourceAccessException | TransientAiException e) {
			throw unavailable(e);
		}
	}

	private static ExternalServiceUnavailableException unavailable(RuntimeException cause) {
		return new ExternalServiceUnavailableException("embedding service", cause);
	}

	private static ChunkMatch toMatch(Document document) {
		Map<String, Object> metadata = document.getMetadata();
		return new ChunkMatch((String) metadata.get("file_name"), ((Number) metadata.get("page")).intValue(),
				document.getText(), document.getScore());
	}

}
