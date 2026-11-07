package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.FakeEmbeddingModel;
import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;

class ChunkStoreAdapterTest {

	private final ChunkStorePort store = new ChunkStoreAdapter(
			TestDatabase.vectorStore(FakeEmbeddingModel.working(), 100));

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@Test
	void findsTheMatchingChunkWithItsSource() {
		store.save("esinf", UUID.randomUUID(), "trees.pdf", List.of(
				new Chunk(3, 0, "binary tree traversal in order"),
				new Chunk(4, 1, "graph shortest path dijkstra")));

		List<ChunkMatch> matches = store.search("esinf", "tree traversal", 5, 0.1);

		assertThat(matches).hasSize(1);
		ChunkMatch match = matches.getFirst();
		assertThat(match.fileName()).isEqualTo("trees.pdf");
		assertThat(match.page()).isEqualTo(3);
		assertThat(match.text()).isEqualTo("binary tree traversal in order");
		assertThat(match.score()).isGreaterThan(0.1);
	}

	@Test
	void ordersMatchesBySimilarityAndRespectsK() {
		store.save("esinf", UUID.randomUUID(), "trees.pdf", List.of(
				new Chunk(1, 0, "tree height balance"),
				new Chunk(2, 1, "tree traversal"),
				new Chunk(3, 2, "tree traversal visits every node")));

		List<ChunkMatch> matches = store.search("esinf", "tree traversal", 2, 0.0);

		assertThat(matches).extracting(ChunkMatch::page).containsExactly(2, 3);
		assertThat(matches.getFirst().score()).isCloseTo(1.0, within(0.001));
	}

	@Test
	void dropsMatchesBelowTheMinimumScore() {
		store.save("esinf", UUID.randomUUID(), "trees.pdf", List.of(new Chunk(1, 0, "tree height balance")));

		assertThat(store.search("esinf", "tree traversal", 5, 0.3)).hasSize(1);
		assertThat(store.search("esinf", "tree traversal", 5, 0.5)).isEmpty();
	}

	@Test
	void onlySearchesTheGivenCourse() {
		store.save("esinf", UUID.randomUUID(), "a.pdf", List.of(new Chunk(1, 0, "tree traversal")));
		store.save("lprog", UUID.randomUUID(), "b.pdf", List.of(new Chunk(1, 0, "tree traversal")));

		assertThat(store.search("esinf", "tree traversal", 5, 0.1))
				.extracting(ChunkMatch::fileName).containsExactly("a.pdf");
	}

}
