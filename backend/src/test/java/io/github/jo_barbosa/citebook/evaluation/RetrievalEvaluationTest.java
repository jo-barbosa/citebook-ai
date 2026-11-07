package io.github.jo_barbosa.citebook.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.application.services.document.DocumentService;
import io.github.jo_barbosa.citebook.application.services.question.QuestionService;
import io.github.jo_barbosa.citebook.application.services.question.SearchSettings;
import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfReaderAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector.ChunkStoreAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.CourseRepositoryAdapter;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence.DocumentRepositoryAdapter;

/**
 * Retrieval evaluation (spec section 9): ingests the corpus with the real embedding model and measures whether the
 * search use case returns an excerpt from the page that answers each question. Run with
 * {@code ./mvnw verify -Dgroups=eval -DexcludedGroups=}.
 */
@Tag("eval")
class RetrievalEvaluationTest {

	private static final String OLLAMA_IMAGE = "ollama/ollama:latest";
	private static final String CACHED_IMAGE = "citebook-ollama-bge-m3";
	private static final String MODEL = "bge-m3";

	private static final String COURSE = "ods";
	private static final String DOCUMENT = "ods-java.pdf";

	// Same values as application.yaml (citebook.*); step 16 tunes them.
	private static final int CHUNK_SIZE = 1600;
	private static final int CHUNK_OVERLAP = 240;
	private static final int K = 5;
	private static final double SCORE_THRESHOLD = 0.0;

	private static final Path REPORT = Path.of("target", "eval-report.md");

	// A little under the first measurement (2026-11-07, chunk size 1600, k 5): fails if retrieval regresses.
	private static final double MIN_HIT_RATE_AT_5 = 0.80;
	private static final double MIN_MRR = 0.54;

	private static OllamaContainer ollama;

	@BeforeAll
	static void startOllama() throws Exception {
		ollama = ollamaWithModel();
	}

	@AfterAll
	static void stopOllama() {
		if (ollama != null) {
			ollama.stop();
		}
	}

	@Test
	void measuresRetrievalOnTheEvaluationQuestions() throws IOException {
		TestDatabase.clear();
		JdbcClient jdbc = TestDatabase.jdbcClient();
		CourseRepositoryPort courses = new CourseRepositoryAdapter(jdbc);
		ChunkStorePort chunkStore = new ChunkStoreAdapter(TestDatabase.vectorStore(embeddingModel(), 10_000));
		DocumentUseCases documents = new DocumentService(courses, new DocumentRepositoryAdapter(jdbc), chunkStore,
				new PdfReaderAdapter(), new PageChunker(CHUNK_SIZE, CHUNK_OVERLAP),
				new TransactionTemplate(new DataSourceTransactionManager(TestDatabase.dataSource())));
		QuestionUseCases questions = new QuestionService(courses, chunkStore,
				new SearchSettings(K, K, SCORE_THRESHOLD));

		IngestDocumentView ingested = documents.ingest(new IngestDocumentCommand(COURSE, DOCUMENT, corpus()));

		List<Result> results = EvalQuestion.load().stream()
				.map(question -> {
					List<Excerpt> excerpts = questions.search(new SearchCommand(COURSE, question.question(), K));
					int rank = RetrievalMetrics.firstRelevantRank(excerpts, question.expectedDocument(),
							question.expectedPages());
					return new Result(question, excerpts, rank);
				})
				.toList();
		List<Integer> ranks = results.stream().map(Result::rank).toList();
		double hitRateAt1 = RetrievalMetrics.hitRate(ranks, 1);
		double hitRateAt3 = RetrievalMetrics.hitRate(ranks, 3);
		double hitRateAt5 = RetrievalMetrics.hitRate(ranks, 5);
		double mrr = RetrievalMetrics.meanReciprocalRank(ranks);

		String report = report(ingested, results, hitRateAt1, hitRateAt3, hitRateAt5, mrr);
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, report);
		System.out.println(report);

		assertThat(results).hasSize(25);
		assertThat(hitRateAt5).as("hit rate@5").isGreaterThanOrEqualTo(MIN_HIT_RATE_AT_5);
		assertThat(mrr).as("MRR").isGreaterThanOrEqualTo(MIN_MRR);
	}

	private record Result(EvalQuestion question, List<Excerpt> excerpts, int rank) {
	}

	/**
	 * Starts Ollama with bge-m3. The first run pulls the model and saves the container as a local image, so later
	 * runs skip the ~1.2 GB download.
	 */
	private static OllamaContainer ollamaWithModel() throws Exception {
		boolean cached = !DockerClientFactory.instance().client().listImagesCmd()
				.withReferenceFilter(CACHED_IMAGE)
				.exec()
				.isEmpty();
		if (cached) {
			OllamaContainer container = new OllamaContainer(
					DockerImageName.parse(CACHED_IMAGE).asCompatibleSubstituteFor("ollama/ollama"));
			container.start();
			return container;
		}
		OllamaContainer container = new OllamaContainer(OLLAMA_IMAGE);
		container.start();
		ExecResult pull = container.execInContainer("ollama", "pull", MODEL);
		if (pull.getExitCode() != 0) {
			throw new IllegalStateException("ollama pull " + MODEL + " failed: " + pull.getStderr());
		}
		container.commitToImage(CACHED_IMAGE);
		return container;
	}

	private static EmbeddingModel embeddingModel() {
		// bge-m3 on CPU needs longer than the default read timeout for one batch of ~8k tokens.
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(Duration.ofMinutes(5));
		return OllamaEmbeddingModel.builder()
				.ollamaApi(OllamaApi.builder()
						.baseUrl(ollama.getEndpoint())
						.restClientBuilder(RestClient.builder().requestFactory(requestFactory))
						.build())
				.options(OllamaEmbeddingOptions.builder().model(MODEL).build())
				.build();
	}

	private static byte[] corpus() throws IOException {
		try (InputStream pdf = RetrievalEvaluationTest.class.getResourceAsStream("/eval/" + DOCUMENT)) {
			if (pdf == null) {
				throw new IllegalStateException("Missing test resource /eval/" + DOCUMENT);
			}
			return pdf.readAllBytes();
		}
	}

	private static String report(IngestDocumentView ingested, List<Result> results, double hitRateAt1,
			double hitRateAt3, double hitRateAt5, double mrr) {
		StringBuilder md = new StringBuilder();
		md.append("# Retrieval evaluation\n\n");
		md.append(String.format(Locale.ROOT,
				"Corpus `%s`: %d pages with text, %d chunks. Embedding model `%s`. "
						+ "Chunk size %d, overlap %d, k %d, score threshold %.2f. %d questions.%n%n",
				DOCUMENT, ingested.pages(), ingested.chunks(), MODEL, CHUNK_SIZE, CHUNK_OVERLAP, K, SCORE_THRESHOLD,
				results.size()));
		md.append("| Metric | Value |\n| --- | --- |\n");
		md.append(String.format(Locale.ROOT, "| hit rate@1 | %.2f |%n", hitRateAt1));
		md.append(String.format(Locale.ROOT, "| hit rate@3 | %.2f |%n", hitRateAt3));
		md.append(String.format(Locale.ROOT, "| hit rate@5 | %.2f |%n", hitRateAt5));
		md.append(String.format(Locale.ROOT, "| MRR | %.3f |%n%n", mrr));
		md.append("| # | Question | Expected pages | Rank | Retrieved pages |\n| --- | --- | --- | --- | --- |\n");
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			String retrieved = result.excerpts().stream()
					.map(excerpt -> String.valueOf(excerpt.page()))
					.collect(Collectors.joining(", "));
			md.append(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s |%n", i + 1, result.question().question(),
					result.question().expectedPages(), result.rank() == 0 ? "miss" : result.rank(), retrieved));
		}
		return md.toString();
	}

}
