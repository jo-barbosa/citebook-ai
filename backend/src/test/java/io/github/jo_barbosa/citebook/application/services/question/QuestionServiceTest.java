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

import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

class QuestionServiceTest {

	private static final SearchSettings SETTINGS = new SearchSettings(5, 8, 0.3);

	private final StubCourses courses = new StubCourses(List.of("esinf", "lprog"));
	private final RecordingChunkStore chunkStore = new RecordingChunkStore();

	private final QuestionUseCases service = new QuestionService(courses, chunkStore, SETTINGS);

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

}
