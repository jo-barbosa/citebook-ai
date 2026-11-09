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
import org.testcontainers.containers.BindMode;
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
 * search use case returns an excerpt from the page that answers each question. Off-topic questions show what a score
 * threshold would filter out. Run with {@code ./mvnw verify -Dgroups=eval -DexcludedGroups=}; add
 * {@code -Deval.chunk-size=1600} or {@code -Deval.score-threshold=0.0} to try other settings. The floors below fit the
 * default settings; other settings may fail them, but the report is still written.
 */
@Tag("eval")
class RetrievalEvaluationTest {

	private static final String OLLAMA_IMAGE = "ollama/ollama:latest";
	private static final String CACHED_IMAGE = "citebook-ollama-bge-m3";
	private static final String MODEL = "bge-m3";
	private static final String MODELS_DIR_VARIABLE = "CITEBOOK_OLLAMA_MODELS";

	private static final String COURSE = "ods";
	private static final String DOCUMENT = "ods-java.pdf";

	// Defaults are the values in application.yaml (citebook.*). Overlap is 15% of the chunk size, as there.
	private static final int CHUNK_SIZE = Integer.getInteger("eval.chunk-size", 800);
	private static final int CHUNK_OVERLAP = CHUNK_SIZE * 15 / 100;
	private static final double SCORE_THRESHOLD = Double
			.parseDouble(System.getProperty("eval.score-threshold", "0.53"));

	// As deep as citebook.max-k, so one run measures hit rate at every k the app may use.
	private static final int SEARCH_DEPTH = 8;

	// citebook.k: the number of excerpts the app puts in a prompt; the regression floor is checked at this depth.
	private static final int K = 5;

	private static final Path REPORT = Path.of("target", "eval-report.md");

	// A little under the step 16 measurement (2026-11-09, chunk size 800, k 5, score threshold 0.53): fails if
	// retrieval regresses. Hit rate: two questions under; MRR: 0.05 under.
	private static final double MIN_HIT_RATE_AT_K = 0.80;
	private static final double MIN_MRR = 0.61;

	// The step 16 threshold gives no excerpt to 5 of the 5 off-topic questions. If this fails, rerun with
	// -Deval.score-threshold=0.0 to see the off-topic scores and choose the threshold again.
	private static final int MIN_OFF_TOPIC_WITHOUT_EXCERPTS = 5;

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
				new SearchSettings(SEARCH_DEPTH, SEARCH_DEPTH, SCORE_THRESHOLD), (system, user) -> {
					throw new UnsupportedOperationException("the retrieval evaluation never calls the chat model");
				});

		IngestDocumentView ingested = documents.ingest(new IngestDocumentCommand(COURSE, DOCUMENT, corpus()));

		List<Result> results = EvalQuestion.load().stream()
				.map(question -> {
					List<Excerpt> excerpts = search(questions, question.question());
					int rank = RetrievalMetrics.firstRelevantRank(excerpts, question.expectedDocument(),
							question.expectedPages());
					return new Result(question, excerpts, rank);
				})
				.toList();
		List<OffTopicResult> offTopic = EvalQuestion.loadOffTopic().stream()
				.map(question -> new OffTopicResult(question, search(questions, question)))
				.toList();
		List<Integer> ranks = results.stream().map(Result::rank).toList();
		Metrics metrics = Metrics.of(ranks);

		String report = report(ingested, results, offTopic, metrics);
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, report);
		System.out.println(report);

		assertThat(results).hasSize(25);
		assertThat(RetrievalMetrics.hitRate(ranks, K)).as("hit rate@" + K).isGreaterThanOrEqualTo(MIN_HIT_RATE_AT_K);
		assertThat(metrics.mrr()).as("MRR").isGreaterThanOrEqualTo(MIN_MRR);
		assertThat(offTopic.stream().filter(result -> result.excerpts().isEmpty()).count())
				.as("off-topic questions with no excerpt")
				.isGreaterThanOrEqualTo(MIN_OFF_TOPIC_WITHOUT_EXCERPTS);
	}

	private static List<Excerpt> search(QuestionUseCases questions, String query) {
		return questions.search(new SearchCommand(COURSE, query, SEARCH_DEPTH));
	}

	private record Result(EvalQuestion question, List<Excerpt> excerpts, int rank) {

		/** Score of the first relevant excerpt; NaN on a miss. */
		double relevantScore() {
			return rank == 0 ? Double.NaN : excerpts.get(rank - 1).score();
		}

	}

	private record OffTopicResult(String question, List<Excerpt> excerpts) {

		/** Score of the best excerpt; NaN when the threshold left none. */
		double topScore() {
			return excerpts.isEmpty() ? Double.NaN : excerpts.getFirst().score();
		}

	}

	private record Metrics(double hitRateAt1, double hitRateAt3, double hitRateAt5, double hitRateAtDepth,
			double mrr) {

		static Metrics of(List<Integer> ranks) {
			return new Metrics(RetrievalMetrics.hitRate(ranks, 1), RetrievalMetrics.hitRate(ranks, 3),
					RetrievalMetrics.hitRate(ranks, 5), RetrievalMetrics.hitRate(ranks, SEARCH_DEPTH),
					RetrievalMetrics.meanReciprocalRank(ranks));
		}

	}

	/**
	 * Starts Ollama with bge-m3. Locally, the first run pulls the model and saves the container as an image, so later
	 * runs skip the ~1.2 GB download. In CI, {@value #MODELS_DIR_VARIABLE} names a host directory that the workflow
	 * caches: it is mounted as Ollama's model store and no image is saved (the image would be too large to cache).
	 */
	private static OllamaContainer ollamaWithModel() throws Exception {
		String modelsDir = System.getenv(MODELS_DIR_VARIABLE);
		if (modelsDir != null && !modelsDir.isBlank()) {
			Files.createDirectories(Path.of(modelsDir));
			OllamaContainer container = new OllamaContainer(OLLAMA_IMAGE)
					.withFileSystemBind(modelsDir, "/root/.ollama", BindMode.READ_WRITE);
			container.start();
			pullModel(container);
			return container;
		}
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
		pullModel(container);
		container.commitToImage(CACHED_IMAGE);
		return container;
	}

	/** Fast when the model is already in the model store: Ollama only checks the manifest. */
	private static void pullModel(OllamaContainer container) throws Exception {
		ExecResult pull = container.execInContainer("ollama", "pull", MODEL);
		if (pull.getExitCode() != 0) {
			throw new IllegalStateException("ollama pull " + MODEL + " failed: " + pull.getStderr());
		}
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

	private static String report(IngestDocumentView ingested, List<Result> results, List<OffTopicResult> offTopic,
			Metrics metrics) {
		double lowestRelevantScore = results.stream()
				.filter(result -> result.rank() > 0)
				.mapToDouble(Result::relevantScore)
				.min()
				.orElse(Double.NaN);
		double highestOffTopicScore = offTopic.stream()
				.filter(result -> !result.excerpts().isEmpty())
				.mapToDouble(OffTopicResult::topScore)
				.max()
				.orElse(Double.NaN);
		long offTopicWithoutExcerpts = offTopic.stream().filter(result -> result.excerpts().isEmpty()).count();

		StringBuilder md = new StringBuilder();
		md.append("# Retrieval evaluation\n\n");
		md.append(String.format(Locale.ROOT,
				"Corpus `%s`: %d pages with text, %d chunks. Embedding model `%s`. "
						+ "Chunk size %d, overlap %d, search depth %d, score threshold %.2f. "
						+ "%d questions, %d off-topic questions.%n%n",
				DOCUMENT, ingested.pages(), ingested.chunks(), MODEL, CHUNK_SIZE, CHUNK_OVERLAP, SEARCH_DEPTH,
				SCORE_THRESHOLD, results.size(), offTopic.size()));
		md.append("| Metric | Value |\n| --- | --- |\n");
		md.append(String.format(Locale.ROOT, "| hit rate@1 | %.2f |%n", metrics.hitRateAt1()));
		md.append(String.format(Locale.ROOT, "| hit rate@3 | %.2f |%n", metrics.hitRateAt3()));
		md.append(String.format(Locale.ROOT, "| hit rate@5 | %.2f |%n", metrics.hitRateAt5()));
		md.append(String.format(Locale.ROOT, "| hit rate@%d | %.2f |%n", SEARCH_DEPTH, metrics.hitRateAtDepth()));
		md.append(String.format(Locale.ROOT, "| MRR | %.3f |%n%n", metrics.mrr()));
		md.append("Scores (cosine similarity, higher is closer):\n\n");
		md.append(String.format(Locale.ROOT, "- Lowest score of a relevant excerpt: %s%n", score(lowestRelevantScore)));
		md.append(String.format(Locale.ROOT, "- Highest top score of an off-topic question: %s%n",
				score(highestOffTopicScore)));
		md.append(String.format(Locale.ROOT, "- Off-topic questions with no excerpt: %d of %d%n%n",
				offTopicWithoutExcerpts, offTopic.size()));
		md.append("| # | Question | Expected pages | Rank | Relevant score | Top score | Retrieved pages |\n");
		md.append("| --- | --- | --- | --- | --- | --- | --- |\n");
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			double topScore = result.excerpts().isEmpty() ? Double.NaN : result.excerpts().getFirst().score();
			md.append(String.format(Locale.ROOT, "| %d | %s | %s | %s | %s | %s | %s |%n", i + 1,
					result.question().question(), result.question().expectedPages(),
					result.rank() == 0 ? "miss" : result.rank(), score(result.relevantScore()), score(topScore),
					pages(result.excerpts())));
		}
		md.append("\n## Off-topic questions\n\n| Question | Top score | Retrieved pages |\n| --- | --- | --- |\n");
		for (OffTopicResult result : offTopic) {
			md.append(String.format(Locale.ROOT, "| %s | %s | %s |%n", result.question(), score(result.topScore()),
					pages(result.excerpts())));
		}
		return md.toString();
	}

	private static String score(double score) {
		return Double.isNaN(score) ? "-" : String.format(Locale.ROOT, "%.3f", score);
	}

	private static String pages(List<Excerpt> excerpts) {
		return excerpts.stream().map(excerpt -> String.valueOf(excerpt.page())).collect(Collectors.joining(", "));
	}

}
