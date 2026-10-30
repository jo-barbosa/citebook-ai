package io.github.jo_barbosa.citebook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SchemaMigrationTest {

	@Container
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));

	static JdbcTemplate jdbc;

	@BeforeAll
	static void migrate() {
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.load()
				.migrate();
		jdbc = new JdbcTemplate(
				new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
	}

	@Test
	void migrationAppliesOnEmptyDatabase() {
		Boolean success = jdbc.queryForObject(
				"select success from flyway_schema_history where version = '1'", Boolean.class);

		assertThat(success).isTrue();
	}

	@Test
	void createsTablesAndHnswIndex() {
		List<String> tables = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);
		List<String> hnswIndexes = jdbc.queryForList(
				"select indexdef from pg_indexes where tablename = 'vector_store' and indexdef like '%hnsw%vector_cosine_ops%'",
				String.class);

		assertThat(tables).contains("course", "document", "vector_store");
		assertThat(hnswIndexes).hasSize(1);
	}

	@Test
	void rejectsDuplicateFileNameInSameCourse() {
		UUID courseId = UUID.randomUUID();
		jdbc.update("insert into course (id, code) values (?, 'ESOF')", courseId);
		String insert = "insert into document (course_id, file_name, sha256, page_count, chunk_count) "
				+ "values (?, 'notes.pdf', ?, 10, 20)";
		jdbc.update(insert, courseId, "a".repeat(64));

		assertThatThrownBy(() -> jdbc.update(insert, courseId, "b".repeat(64)))
				.isInstanceOf(DuplicateKeyException.class);
	}

}
