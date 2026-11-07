package io.github.jo_barbosa.citebook.application.services.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jo_barbosa.citebook.FakeEmbeddingModel;
import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestStatus;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfFixtures;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfReaderAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector.ChunkStoreAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.CourseRepositoryAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.DocumentRepositoryAdapter;

class DocumentServiceIntegrationTest {

	private static final byte[] TREES_AND_GRAPHS = PdfFixtures.pdf(
			"Binary trees keep keys in sorted order.",
			"Graphs model networks of nodes and edges.");

	private final JdbcClient jdbc = TestDatabase.jdbcClient();

	private final ChunkStorePort searchStore = new ChunkStoreAdapter(
			TestDatabase.vectorStore(FakeEmbeddingModel.working(), 2));

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@Test
	void indexesAPdfEndToEnd() {
		IngestDocumentView view = serviceWith(FakeEmbeddingModel.working()).ingest(command(TREES_AND_GRAPHS));

		assertThat(view.status()).isEqualTo(IngestStatus.INDEXED);
		assertThat(view.pages()).isEqualTo(2);
		assertThat(view.chunks()).isEqualTo(2);
		assertThat(count("course")).isEqualTo(1);
		assertThat(count("document")).isEqualTo(1);
		assertThat(count("vector_store")).isEqualTo(2);
		assertThat(searchStore.search("esinf", "sorted binary trees", 5, 0.1))
				.extracting(ChunkMatch::page).containsExactly(1);
	}

	@Test
	void sameFileTwiceIsAlreadyIndexed() {
		DocumentUseCases service = serviceWith(FakeEmbeddingModel.working());
		service.ingest(command(TREES_AND_GRAPHS));

		IngestDocumentView second = service.ingest(command(TREES_AND_GRAPHS));

		assertThat(second.status()).isEqualTo(IngestStatus.ALREADY_INDEXED);
		assertThat(count("document")).isEqualTo(1);
		assertThat(count("vector_store")).isEqualTo(2);
	}

	@Test
	void aChangedFileReplacesTheOldChunks() {
		DocumentUseCases service = serviceWith(FakeEmbeddingModel.working());
		service.ingest(command(TREES_AND_GRAPHS));

		IngestDocumentView second = service.ingest(command(PdfFixtures.pdf("Stacks and queues are linear structures.")));

		assertThat(second.status()).isEqualTo(IngestStatus.REPLACED);
		assertThat(count("document")).isEqualTo(1);
		assertThat(count("vector_store")).isEqualTo(1);
		assertThat(searchStore.search("esinf", "sorted binary trees", 5, 0.1)).isEmpty();
		assertThat(searchStore.search("esinf", "stacks queues", 5, 0.1)).hasSize(1);
	}

	@Test
	void anEmbeddingFailureLeavesNoCourseDocumentOrChunk() {
		DocumentUseCases service = serviceWith(FakeEmbeddingModel.unavailable());

		assertThatThrownBy(() -> service.ingest(command(TREES_AND_GRAPHS))).isInstanceOf(RuntimeException.class);

		assertThat(count("course")).isZero();
		assertThat(count("document")).isZero();
		assertThat(count("vector_store")).isZero();
	}

	private DocumentUseCases serviceWith(EmbeddingModel embeddingModel) {
		return new DocumentService(
				new CourseRepositoryAdapter(jdbc),
				new DocumentRepositoryAdapter(jdbc),
				new ChunkStoreAdapter(TestDatabase.vectorStore(embeddingModel, 2)),
				new PdfReaderAdapter(),
				new PageChunker(100, 20),
				new TransactionTemplate(new DataSourceTransactionManager(TestDatabase.dataSource())));
	}

	private static IngestDocumentCommand command(byte[] pdf) {
		return new IngestDocumentCommand("esinf", "notes.pdf", pdf);
	}

	private int count(String table) {
		return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
	}

}
