package io.github.jo_barbosa.citebook.application.services.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.jo_barbosa.citebook.application.ports.in.question.AnswerView;
import io.github.jo_barbosa.citebook.application.ports.in.question.AskCommand;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chat.ChatPort;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.application.ports.out.metrics.MetricsPort;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.citation.Citation;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.course.CourseSummary;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;
import io.github.jo_barbosa.citebook.domain.model.question.PromptBuilder;

class QuestionServiceTest {

	private static final SearchSettings SETTINGS = new SearchSettings(5, 8, 0.3);

	private final StubCourses courses = new StubCourses(List.of("esinf", "lprog"));
	private final RecordingChunkStore chunkStore = new RecordingChunkStore();

	private final RecordingChat chat = new RecordingChat();

	private final RecordingMetrics metrics = new RecordingMetrics();

	private final QuestionUseCases service = new QuestionService(courses, chunkStore, SETTINGS, chat, metrics);

	@Test
	void numbersTheMatchesByRankInTheOrderTheStoreReturnsThem() {
		chunkStore.matches = List.of(
				new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91),
				new ChunkMatch("trees.pdf", 3, "A BST keeps keys ordered.", 0.74));

		List<Excerpt> excerpts = service.search(new SearchCommand("esinf", "What does Dijkstra do?", null));

		assertThat(excerpts).containsExactly(
				new Excerpt(1, "graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91),
				new Excerpt(2, "trees.pdf", 3, "A BST keeps keys ordered.", 0.74));
	}

	@Test
	void passesCourseQueryDefaultKAndThresholdToTheStore() {
		service.search(new SearchCommand("esinf", "What does Dijkstra do?", null));

		assertThat(chunkStore.searches).containsExactly(
				new RecordingChunkStore.Search("esinf", "What does Dijkstra do?", 5, 0.3));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 8 })
	void usesTheRequestedKWithinOneToMaxK(int k) {
		service.search(new SearchCommand("esinf", "What does Dijkstra do?", k));

		assertThat(chunkStore.searches).singleElement()
				.extracting(RecordingChunkStore.Search::k)
				.isEqualTo(k);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 9 })
	void rejectsAKOutsideOneToMaxK(int k) {
		assertThatThrownBy(() -> service.search(new SearchCommand("esinf", "What does Dijkstra do?", k)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("k must be between 1 and 8, got " + k);
		assertThat(chunkStore.searches).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void rejectsABlankQuery(String query) {
		assertThatThrownBy(() -> service.search(new SearchCommand("esinf", query, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("query must not be blank");
		assertThat(chunkStore.searches).isEmpty();
	}

	@Test
	void rejectsAnUnknownCourseAndListsTheAvailableOnes() {
		assertThatThrownBy(() -> service.search(new SearchCommand("xpto", "What does Dijkstra do?", null)))
				.isInstanceOf(CourseNotFoundException.class)
				.hasMessageContaining("xpto")
				.hasMessageContaining("esinf, lprog");
		assertThat(chunkStore.searches).isEmpty();
	}

	@Test
	void returnsAnEmptyListWhenNothingMatches() {
		assertThat(service.search(new SearchCommand("esinf", "What does Dijkstra do?", null))).isEmpty();
	}

	@Test
	void asksTheModelWithTheFoundExcerptsAndReturnsTheCheckedAnswer() {
		chunkStore.matches = List.of(
				new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91),
				new ChunkMatch("trees.pdf", 3, "A BST keeps keys ordered.", 0.74));
		chat.answer = "Dijkstra finds shortest paths [1]. It is greedy [7].";

		AnswerView answer = service.ask(new AskCommand("esinf", "What does Dijkstra do?"));

		PromptBuilder prompts = new PromptBuilder();
		List<Excerpt> sent = List.of(
				new Excerpt(1, "graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91),
				new Excerpt(2, "trees.pdf", 3, "A BST keeps keys ordered.", 0.74));
		assertThat(chat.calls).containsExactly(new RecordingChat.Call(prompts.systemMessage(),
				prompts.userMessage("What does Dijkstra do?", sent)));
		assertThat(answer).isEqualTo(new AnswerView("Dijkstra finds shortest paths [1]. It is greedy.", true,
				List.of(new Citation(1, "graphs.pdf", 12, "Dijkstra finds shortest paths."))));
	}

	@Test
	void withoutMaterialItAnswersWithoutCallingTheModel() {
		chunkStore.matches = List.of();

		AnswerView answer = service.ask(new AskCommand("esinf", "Who won the 2016 European Championship?"));

		assertThat(answer).isEqualTo(new AnswerView(QuestionService.NO_MATERIAL_ANSWER, false, List.of()));
		assertThat(chat.calls).isEmpty();
	}

	@Test
	void countsTheCitationsTheCheckRejected() {
		chunkStore.matches = List.of(new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91));
		chat.answer = "Dijkstra finds shortest paths [1]. It is greedy [7].";

		service.ask(new AskCommand("esinf", "What does Dijkstra do?"));

		assertThat(metrics.rejectedCitations).containsExactly(1);
		assertThat(metrics.questionsWithoutMaterial).isZero();
	}

	@Test
	void reportsZeroRejectedCitationsWhenEveryCitationIsValid() {
		chunkStore.matches = List.of(new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91));
		chat.answer = "Dijkstra finds shortest paths [1].";

		service.ask(new AskCommand("esinf", "What does Dijkstra do?"));

		assertThat(metrics.rejectedCitations).containsExactly(0);
	}

	@Test
	void countsAQuestionWithoutMaterial() {
		chunkStore.matches = List.of();

		service.ask(new AskCommand("esinf", "Who won the 2016 European Championship?"));

		assertThat(metrics.questionsWithoutMaterial).isEqualTo(1);
		assertThat(metrics.rejectedCitations).isEmpty();
	}

	@Test
	void searchAloneCountsNothing() {
		chunkStore.matches = List.of();

		service.search(new SearchCommand("esinf", "What does Dijkstra do?", null));

		assertThat(metrics.questionsWithoutMaterial).isZero();
		assertThat(metrics.rejectedCitations).isEmpty();
	}

	@Test
	void removesInvisibleCharactersFromTheQuestionBeforeSearchingAndAsking() {
		chunkStore.matches = List.of(new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91));
		chat.answer = "It finds shortest paths [1].";

		service.ask(new AskCommand("esinf", "What does\u200B Dijkstra do?" + Character.toString(0xE0041)));

		assertThat(chunkStore.searches).containsExactly(
				new RecordingChunkStore.Search("esinf", "What does Dijkstra do?", 5, 0.3));
		assertThat(chat.calls).singleElement()
				.extracting(RecordingChat.Call::userMessage)
				.asString()
				.endsWith("Question: What does Dijkstra do?");
	}

	@Test
	void aQuestionOfOnlyInvisibleCharactersGetsTheNoMaterialAnswer() {
		AnswerView answer = service.ask(new AskCommand("esinf", "\u200B\u200B"));

		assertThat(answer).isEqualTo(new AnswerView(QuestionService.NO_MATERIAL_ANSWER, false, List.of()));
		assertThat(chunkStore.searches).isEmpty();
		assertThat(chat.calls).isEmpty();
	}

	@Test
	void anAnswerWithoutValidCitationsIsNotGrounded() {
		chunkStore.matches = List.of(new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91));
		chat.answer = "Dijkstra is a well-known algorithm.";

		AnswerView answer = service.ask(new AskCommand("esinf", "What does Dijkstra do?"));

		assertThat(answer.grounded()).isFalse();
		assertThat(answer.citations()).isEmpty();
		assertThat(answer.answer()).isEqualTo("Dijkstra is a well-known algorithm.");
	}

	@Test
	void searchesWithTheDefaultK() {
		service.ask(new AskCommand("esinf", "What does Dijkstra do?"));

		assertThat(chunkStore.searches).containsExactly(
				new RecordingChunkStore.Search("esinf", "What does Dijkstra do?", 5, 0.3));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void rejectsABlankQuestion(String question) {
		assertThatThrownBy(() -> service.ask(new AskCommand("esinf", question)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("question must not be blank");
		assertThat(chunkStore.searches).isEmpty();
		assertThat(chat.calls).isEmpty();
	}

	@Test
	void rejectsAQuestionLongerThanTheLimit() {
		String question = "a".repeat(QuestionService.MAX_QUESTION_LENGTH + 1);

		assertThatThrownBy(() -> service.ask(new AskCommand("esinf", question)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("question must be at most 1000 characters, got 1001");
		assertThat(chunkStore.searches).isEmpty();
		assertThat(chat.calls).isEmpty();
	}

	@Test
	void acceptsAQuestionOfExactlyTheLimit() {
		service.ask(new AskCommand("esinf", "a".repeat(QuestionService.MAX_QUESTION_LENGTH)));

		assertThat(chunkStore.searches).hasSize(1);
	}

	@Test
	void anUnknownCourseIsRejectedBeforeTheModelIsCalled() {
		// Material is available, so an empty chat log proves the course check stopped the call.
		chunkStore.matches = List.of(new ChunkMatch("graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91));

		assertThatThrownBy(() -> service.ask(new AskCommand("xpto", "What does Dijkstra do?")))
				.isInstanceOf(CourseNotFoundException.class);
		assertThat(chunkStore.searches).isEmpty();
		assertThat(chat.calls).isEmpty();
	}

	private static class StubCourses implements CourseRepositoryPort {

		private final List<String> codes;

		StubCourses(List<String> codes) {
			this.codes = codes;
		}

		@Override
		public Optional<Course> findByCode(String code) {
			return codes.contains(code) ? Optional.of(new Course(UUID.randomUUID(), code, Instant.now()))
					: Optional.empty();
		}

		@Override
		public Course create(String code) {
			throw new UnsupportedOperationException("search never creates courses");
		}

		@Override
		public List<String> findAllCodes() {
			return codes;
		}

		@Override
		public List<CourseSummary> findAllSummaries() {
			throw new UnsupportedOperationException("search never lists courses");
		}

	}

	private static class RecordingChunkStore implements ChunkStorePort {

		record Search(String courseCode, String query, int k, double minScore) {
		}

		final List<Search> searches = new ArrayList<>();
		List<ChunkMatch> matches = List.of();

		@Override
		public void save(String courseCode, UUID documentId, String fileName, List<Chunk> chunks) {
			throw new UnsupportedOperationException("search never saves");
		}

		@Override
		public void deleteByDocumentId(UUID documentId) {
			throw new UnsupportedOperationException("search never deletes");
		}

		@Override
		public List<ChunkMatch> search(String courseCode, String query, int k, double minScore) {
			searches.add(new Search(courseCode, query, k, minScore));
			return matches;
		}

	}

	private static class RecordingChat implements ChatPort {

		record Call(String systemMessage, String userMessage) {
		}

		final List<Call> calls = new ArrayList<>();
		String answer = "";

		@Override
		public String complete(String systemMessage, String userMessage) {
			calls.add(new Call(systemMessage, userMessage));
			return answer;
		}

	}

	private static class RecordingMetrics implements MetricsPort {

		final List<Integer> rejectedCitations = new ArrayList<>();
		int questionsWithoutMaterial;

		@Override
		public void citationsRejected(int count) {
			rejectedCitations.add(count);
		}

		@Override
		public void questionWithoutMaterial() {
			questionsWithoutMaterial++;
		}

	}

}
