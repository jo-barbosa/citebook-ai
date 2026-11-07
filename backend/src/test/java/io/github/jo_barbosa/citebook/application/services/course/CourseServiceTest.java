package io.github.jo_barbosa.citebook.application.services.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.application.ports.in.course.CourseUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.course.CourseView;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.course.CourseSummary;

class CourseServiceTest {

	private final StubCourses courses = new StubCourses();

	private final CourseUseCases service = new CourseService(courses);

	@Test
	void listsTheCoursesInTheOrderTheRepositoryReturnsThem() {
		courses.summaries = List.of(new CourseSummary("arqcp", 1), new CourseSummary("esinf", 2));

		assertThat(service.list()).containsExactly(new CourseView("arqcp", 1), new CourseView("esinf", 2));
	}

	@Test
	void listIsEmptyWhenThereAreNoCourses() {
		assertThat(service.list()).isEmpty();
	}

	private static class StubCourses implements CourseRepositoryPort {

		List<CourseSummary> summaries = List.of();

		@Override
		public List<CourseSummary> findAllSummaries() {
			return summaries;
		}

		@Override
		public Optional<Course> findByCode(String code) {
			throw new UnsupportedOperationException("listing never looks up one course");
		}

		@Override
		public Course create(String code) {
			throw new UnsupportedOperationException("listing never creates courses");
		}

		@Override
		public List<String> findAllCodes() {
			throw new UnsupportedOperationException("listing uses summaries, not codes");
		}

	}

}
