package io.github.jo_barbosa.citebook;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class TestDatabase {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
			DockerImageName.parse("pgvector/pgvector:pg16"));

	private static final JdbcClient JDBC;

	static {
		POSTGRES.start();
		Flyway.configure()
				.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
				.load()
				.migrate();
		JDBC = JdbcClient.create(
				new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
	}

	private TestDatabase() {
	}

	public static JdbcClient jdbcClient() {
		return JDBC;
	}

	public static void clear() {
		JDBC.sql("delete from document").update();
		JDBC.sql("delete from course").update();
	}

}
