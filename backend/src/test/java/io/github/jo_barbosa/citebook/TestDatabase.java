package io.github.jo_barbosa.citebook;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestDatabase {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg16"));

	private static final DataSource DATA_SOURCE;

	private static final JdbcClient JDBC;

	static {
		POSTGRES.start();
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.load()
				.migrate();
		DATA_SOURCE = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
				POSTGRES.getPassword());
		JDBC = JdbcClient.create(DATA_SOURCE);
	}

	private TestDatabase() {
	}

	public static DataSource dataSource() {
		return DATA_SOURCE;
	}

	public static JdbcClient jdbcClient() {
		return JDBC;
	}

	public static PgVectorStore vectorStore(EmbeddingModel embeddingModel, int maxDocumentBatchSize) {
		return PgVectorStore.builder(new JdbcTemplate(DATA_SOURCE), embeddingModel)
				.dimensions(FakeEmbeddingModel.DIMENSIONS)
				.initializeSchema(false)
				.maxDocumentBatchSize(maxDocumentBatchSize)
				.build();
	}

	public static void clear() {
		JDBC.sql("delete from vector_store").update();
		JDBC.sql("delete from document").update();
		JDBC.sql("delete from course").update();
	}

}
