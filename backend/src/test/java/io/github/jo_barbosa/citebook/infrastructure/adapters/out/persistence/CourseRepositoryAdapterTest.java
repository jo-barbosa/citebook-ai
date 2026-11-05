package io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.course.Course;

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
	void rejectsADuplicateCode() {
		repository.create("esinf");

		assertThatThrownBy(() -> repository.create("esinf")).isInstanceOf(DuplicateKeyException.class);
	}

}
