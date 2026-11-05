package io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.document.DocumentRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.document.Document;

class DocumentRepositoryAdapterTest {

	private static final String HASH = "a".repeat(64);

	private final JdbcClient jdbc = TestDatabase.jdbcClient();
	private final CourseRepositoryAdapter courses = new CourseRepositoryAdapter(jdbc);
	private final DocumentRepositoryPort documents = new DocumentRepositoryAdapter(jdbc);

	private Course course;

	@BeforeEach
	void createCourse() {
		TestDatabase.clear();
		course = courses.create("esinf");
	}

	@Test
	void createsADocumentWithGeneratedIdAndTimestamp() {
		Document document = documents.create(course.id(), "slides.pdf", HASH, 12, 30);

		assertThat(document.id()).isNotNull();
		assertThat(document.courseId()).isEqualTo(course.id());
		assertThat(document.fileName()).isEqualTo("slides.pdf");
		assertThat(document.sha256()).isEqualTo(HASH);
		assertThat(document.pageCount()).isEqualTo(12);
		assertThat(document.chunkCount()).isEqualTo(30);
		assertThat(document.ingestedAt()).isNotNull();
	}

	@Test
	void findsADocumentByCourseAndFileName() {
		Document created = documents.create(course.id(), "slides.pdf", HASH, 12, 30);

		assertThat(documents.findByCourseIdAndFileName(course.id(), "slides.pdf")).contains(created);
	}

	@Test
	void findIsEmptyWhenTheFileIsUnknown() {
		assertThat(documents.findByCourseIdAndFileName(course.id(), "other.pdf")).isEmpty();
	}

	@Test
	void allowsTheSameFileNameInAnotherCourse() {
		Course other = courses.create("lprog");
		documents.create(course.id(), "slides.pdf", HASH, 12, 30);

		assertThatCode(() -> documents.create(other.id(), "slides.pdf", HASH, 12, 30))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsTheSameFileNameInTheSameCourse() {
		documents.create(course.id(), "slides.pdf", HASH, 12, 30);

		assertThatThrownBy(() -> documents.create(course.id(), "slides.pdf", "b".repeat(64), 5, 8))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void rejectsAnUnknownCourse() {
		assertThatThrownBy(() -> documents.create(UUID.randomUUID(), "slides.pdf", HASH, 12, 30))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletesADocumentById() {
		Document created = documents.create(course.id(), "slides.pdf", HASH, 12, 30);

		documents.deleteById(created.id());

		assertThat(documents.findByCourseIdAndFileName(course.id(), "slides.pdf")).isEmpty();
	}

}
