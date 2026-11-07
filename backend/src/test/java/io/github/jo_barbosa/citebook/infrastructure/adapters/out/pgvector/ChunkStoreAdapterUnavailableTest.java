package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.client.ResourceAccessException;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;

class ChunkStoreAdapterUnavailableTest {

	private static final String MESSAGE = "The embedding service is unavailable. Try again in a few minutes.";

	private final VectorStore vectorStore = mock(VectorStore.class);

	private final ChunkStorePort store = new ChunkStoreAdapter(vectorStore);

	@Test
	void savingWhileTheEmbeddingServiceIsUnreachableIsReportedAsUnavailable() {
		doThrow(new ResourceAccessException("Connection refused")).when(vectorStore).add(anyList());

		assertThatThrownBy(() -> store.save("esinf", UUID.randomUUID(), "a.pdf", List.of(new Chunk(1, 0, "tree"))))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(MESSAGE)
				.hasCauseInstanceOf(ResourceAccessException.class);
	}

	@Test
	void searchingWhileTheEmbeddingServiceFailsTemporarilyIsReportedAsUnavailable() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new TransientAiException("503"));

		assertThatThrownBy(() -> store.search("esinf", "tree", 5, 0.1))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage(MESSAGE)
				.hasCauseInstanceOf(TransientAiException.class);
	}

	@Test
	void otherFailuresAreNotHidden() {
		doThrow(new IllegalStateException("bug")).when(vectorStore).add(anyList());

		assertThatThrownBy(() -> store.save("esinf", UUID.randomUUID(), "a.pdf", List.of(new Chunk(1, 0, "tree"))))
				.isInstanceOf(IllegalStateException.class);
	}

}
