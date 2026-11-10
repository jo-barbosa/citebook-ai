package io.github.jo_barbosa.citebook.infrastructure.adapters.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.in.course.CourseUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.course.CourseView;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

class CourseMaterialToolsTest {

	private final CourseUseCases courseUseCases = mock(CourseUseCases.class);
	private final QuestionUseCases questionUseCases = mock(QuestionUseCases.class);

	private final CourseMaterialTools tools = new CourseMaterialTools(courseUseCases, questionUseCases);

	@Test
	void listCoursesListsEachCourseWithItsDocumentCount() {
		when(courseUseCases.list()).thenReturn(List.of(new CourseView("esinf", 3), new CourseView("lprog", 1)));

		assertThat(tools.listCourses()).isEqualTo("""
				Courses:
				- esinf (documents: 3)
				- lprog (documents: 1)""");
	}

	@Test
	void listCoursesSaysWhenThereAreNone() {
		when(courseUseCases.list()).thenReturn(List.of());

		assertThat(tools.listCourses()).isEqualTo(CourseMaterialTools.NO_COURSES);
	}

	@Test
	void listCoursesReturnsAGenericMessageForAnUnexpectedError() {
		when(courseUseCases.list()).thenThrow(new IllegalStateException("connection refused to 10.0.0.5"));

		assertThat(tools.listCourses()).isEqualTo(CourseMaterialTools.UNEXPECTED_ERROR);
	}

	@Test
	void searchReturnsTheExcerptsNumberedWithTheirSourceUnderTheDataNotice() {
		when(questionUseCases.search(any())).thenReturn(List.of(
				new Excerpt(1, "graphs.pdf", 12, "Dijkstra finds shortest paths.", 0.91),
				new Excerpt(2, "trees.pdf", 3, "A BST keeps keys ordered.", 0.74)));

		String result = tools.searchCourseMaterial("esinf", "What does Dijkstra do?");

		assertThat(result).isEqualTo(CourseMaterialTools.EXCERPTS_NOTICE + """


				[1] graphs.pdf, page 12
				Dijkstra finds shortest paths.

				[2] trees.pdf, page 3
				A BST keeps keys ordered.""");
	}

	@Test
	void searchUsesTheConfiguredDefaultKAndNeverAsksTheModel() {
		when(questionUseCases.search(any())).thenReturn(List.of());

		tools.searchCourseMaterial("esinf", "What does Dijkstra do?");

		// k = null: the service applies the configured default, so the caller cannot ask for more excerpts.
		verify(questionUseCases).search(new SearchCommand("esinf", "What does Dijkstra do?", null));
		verify(questionUseCases, never()).ask(any());
	}

	@Test
	void searchSaysWhenNothingRelevantWasFound() {
		when(questionUseCases.search(any())).thenReturn(List.of());

		assertThat(tools.searchCourseMaterial("esinf", "Who won the 2010 World Cup?"))
				.isEqualTo("No relevant material was found in course 'esinf' for this query.");
	}

	@Test
	void searchReturnsTheDomainMessageForAnUnknownCourse() {
		when(questionUseCases.search(any())).thenThrow(new CourseNotFoundException("esimf", List.of("esinf", "lprog")));

		assertThat(tools.searchCourseMaterial("esimf", "What does Dijkstra do?"))
				.isEqualTo("No course with code 'esimf'. Available courses: esinf, lprog.");
	}

	@Test
	void searchReturnsTheMessageForAnInvalidQuery() {
		when(questionUseCases.search(any())).thenThrow(new IllegalArgumentException("query must not be blank"));

		assertThat(tools.searchCourseMaterial("esinf", " ")).isEqualTo("query must not be blank");
	}

	@Test
	void searchRefusesAQueryOverTheLengthLimitWithoutSearching() {
		String query = "a".repeat(QuestionUseCases.MAX_QUESTION_LENGTH + 1);

		assertThat(tools.searchCourseMaterial("esinf", query))
				.isEqualTo("query must be at most 1000 characters, got 1001");
		verifyNoInteractions(questionUseCases);
	}

	@Test
	void searchReturnsTheMessageWhenTheEmbeddingServiceIsDown() {
		when(questionUseCases.search(any()))
				.thenThrow(new ExternalServiceUnavailableException("embedding service", new RuntimeException("timeout")));

		assertThat(tools.searchCourseMaterial("esinf", "What does Dijkstra do?"))
				.isEqualTo("The embedding service is unavailable. Try again in a few minutes.");
	}

	@Test
	void searchReturnsAGenericMessageForAnUnexpectedError() {
		when(questionUseCases.search(any())).thenThrow(new IllegalStateException("connection refused to 10.0.0.5"));

		assertThat(tools.searchCourseMaterial("esinf", "What does Dijkstra do?"))
				.isEqualTo(CourseMaterialTools.UNEXPECTED_ERROR);
	}

}
