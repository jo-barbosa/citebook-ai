package io.github.jo_barbosa.citebook;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.zaxxer.hikari.HikariDataSource;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class CitebookAiApplicationTests {

	@Autowired
	DataSource dataSource;

	@Test
	void contextLoads() {
	}

	@Test
	void everyPoolConnectionTurnsOnIterativeIndexScans() throws SQLException {
		// V2's database default only reaches connections opened after it, and the pool opens connections while
		// Flyway runs. That race does not show reliably in a test, so the pool's configuration is checked instead.
		String initSql = dataSource.unwrap(HikariDataSource.class).getConnectionInitSql();

		assertThat(initSql).isEqualTo("SET hnsw.iterative_scan = strict_order");
	}

}
