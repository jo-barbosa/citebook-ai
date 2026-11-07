package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jo_barbosa.citebook.FakeEmbeddingModel;
import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;

class ChunkStoreTransactionTest {

	private static final List<Chunk> CHUNKS = List.of(
			new Chunk(1, 0, "first chunk"),
			new Chunk(1, 1, "second chunk"),
			new Chunk(2, 2, "corrupt third chunk"),
			new Chunk(2, 3, "fourth chunk"));

	private final ChunkStorePort store = new ChunkStoreAdapter(
			TestDatabase.vectorStore(FakeEmbeddingModel.brokenVectorFor("corrupt"), 2));

	private final TransactionTemplate transaction = new TransactionTemplate(
			new DataSourceTransactionManager(TestDatabase.dataSource()));

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@Test
	void aFailureInTheMiddleOfASaveLeavesNothingBehindInsideATransaction() {
		assertThatThrownBy(() -> transaction.executeWithoutResult(
				status -> store.save("esinf", UUID.randomUUID(), "a.pdf", CHUNKS)))
				.isInstanceOf(RuntimeException.class);

		assertThat(chunkCount()).isZero();
	}

	@Test
	void withoutATransactionTheFirstBatchStaysBehind() {
		assertThatThrownBy(() -> store.save("esinf", UUID.randomUUID(), "a.pdf", CHUNKS))
				.isInstanceOf(RuntimeException.class);

		assertThat(chunkCount()).isEqualTo(2);
	}

	private static int chunkCount() {
		return TestDatabase.jdbcClient().sql("select count(*) from vector_store").query(Integer.class).single();
	}

}
