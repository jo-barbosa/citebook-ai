package io.github.jo_barbosa.citebook.application.services.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.jo_barbosa.citebook.FakeEmbeddingModel;
import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.application.services.document.DocumentService;
import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfFixtures;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfReaderAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector.ChunkStoreAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.CourseRepositoryAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.DocumentRepositoryAdapter;

class QuestionServiceIntegrationTest {

	private final JdbcClient jdbc = TestDatabase.jdbcClient();
	private final CourseRepositoryPort courses = new CourseRepositoryAdapter(jdbc);
	private final ChunkStorePort chunkStore = new ChunkStoreAdapter(
			TestDatabase.vectorStore(FakeEmbeddingModel.working(), 2));

	private final DocumentUseCases documents = new DocumentService(courses, new DocumentRepositoryAdapter(jdbc),
			chunkStore, new PdfReaderAdapter(), new PageChunker(100, 20),
			new TransactionTemplate(new DataSourceTransactionManager(TestDatabase.dataSource())));

	private final QuestionUseCases questions = new QuestionService(courses, chunkStore, new SearchSettings(5, 8, 0.0));

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@Test
	void findsOnlyTheMaterialOfTheSearchedCourse() {
		documents.ingest(new IngestDocumentCommand("esinf", "trees.pdf",
				PdfFixtures.pdf("Binary trees keep keys in sorted order.")));
		documents.ingest(new IngestDocumentCommand("lprog", "grammars.pdf",
				PdfFixtures.pdf("Binary trees keep keys in sorted order, but this is a grammar course.")));

		List<Excerpt> excerpts = questions.search(new SearchCommand("esinf", "sorted binary trees", null));

		assertThat(excerpts).singleElement().satisfies(excerpt -> {
			assertThat(excerpt.n()).isEqualTo(1);
			assertThat(excerpt.fileName()).isEqualTo("trees.pdf");
			assertThat(excerpt.page()).isEqualTo(1);
		});
	}

}
