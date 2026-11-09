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
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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
	void migrationsApplyOnEmptyDatabase() {
		List<String> versions = jdbc.queryForList(
				"select version from flyway_schema_history where success order by installed_rank", String.class);

		assertThat(versions).containsExactly("1", "2");
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
	void indexesMetadataForTheVectorStoreFilters() {
		List<String> indexes = jdbc.queryForList(
				"select indexname from pg_indexes where tablename = 'vector_store'", String.class);

		assertThat(indexes).contains("vector_store_metadata_idx")
				.doesNotContain("vector_store_document_id_idx", "vector_store_course_code_idx");
	}

	@Test
	void deleteByDocumentCanUseTheMetadataIndex() {
		// SET and EXPLAIN must run on the same connection.
		SingleConnectionDataSource connection = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(),
				POSTGRES.getUsername(), POSTGRES.getPassword(), true);
		try {
			JdbcTemplate session = new JdbcTemplate(connection);
			// An empty table is always scanned sequentially; disabling that shows whether the index can serve
			// the filter at all. This is the filter PgVectorStore sends for delete(document_id == ...).
			session.execute("set enable_seqscan = off");
			String plan = String.join("\n", session.queryForList(
					"explain delete from vector_store where metadata::jsonb @@ '$.document_id == \"abc\"'::jsonpath",
					String.class));

			assertThat(plan).contains("vector_store_metadata_idx");
		}
		finally {
			connection.destroy();
		}
	}

	@Test
	void turnsOnIterativeIndexScansForNewConnections() {
		// jdbc opens a new connection per call, so it gets the database default set by V2.
		assertThat(jdbc.queryForObject("show hnsw.iterative_scan", String.class)).isEqualTo("strict_order");
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
