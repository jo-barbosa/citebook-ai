package io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.course.CourseSummary;

class CourseRepositoryAdapterTest {

	private final CourseRepositoryPort repository = new CourseRepositoryAdapter(TestDatabase.jdbcClient());

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@Test
	void createsACourseWithGeneratedIdAndTimestamp() {
		Course course = repository.create("esinf");

		assertThat(course.id()).isNotNull();
		assertThat(course.code()).isEqualTo("esinf");
		assertThat(course.createdAt()).isNotNull();
	}

	@Test
	void findsACourseByCode() {
		Course created = repository.create("esinf");

		assertThat(repository.findByCode("esinf")).contains(created);
	}

	@Test
	void findByCodeIsEmptyForAnUnknownCode() {
		assertThat(repository.findByCode("xpto")).isEmpty();
	}

	@Test
	void findsAllCodesInAlphabeticalOrder() {
		repository.create("lprog");
		repository.create("esinf");

		assertThat(repository.findAllCodes()).containsExactly("esinf", "lprog");
	}

	@Test
	void summarisesEveryCourseWithItsDocumentCountOrderedByCode() {
		Course esinf = repository.create("esinf");
		repository.create("lprog");
		Course arqcp = repository.create("arqcp");
		DocumentRepositoryAdapter documents = new DocumentRepositoryAdapter(TestDatabase.jdbcClient());
		documents.create(esinf.id(), "graphs.pdf", "a".repeat(64), 10, 20);
		documents.create(esinf.id(), "trees.pdf", "b".repeat(64), 8, 15);
		documents.create(arqcp.id(), "assembly.pdf", "c".repeat(64), 5, 9);

		assertThat(repository.findAllSummaries()).containsExactly(
				new CourseSummary("arqcp", 1),
				new CourseSummary("esinf", 2),
				new CourseSummary("lprog", 0));
	}

	@Test
	void summariesAreEmptyWhenThereAreNoCourses() {
		assertThat(repository.findAllSummaries()).isEmpty();
	}

	@Test
	void rejectsADuplicateCode() {
		repository.create("esinf");

		assertThatThrownBy(() -> repository.create("esinf")).isInstanceOf(DuplicateKeyException.class);
	}

}
