package io.github.jo_barbosa.citebook.infrastructure.adapters.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.jo_barbosa.citebook.TestcontainersConfiguration;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

/** The wiring: what an MCP client sees when it lists the server's tools. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class McpServerTest {

	@Autowired
	McpSyncServer server;

	@Test
	void serverIsNamedAfterTheApplication() {
		assertThat(server.getServerInfo().name()).isEqualTo("citebook-ai");
	}

	@Test
	void exposesExactlyTheTwoToolsAndBothAreReadOnly() {
		List<McpSchema.Tool> tools = server.listTools();

		assertThat(tools).extracting(McpSchema.Tool::name)
				.containsExactlyInAnyOrder("list_courses", "search_course_material");
		assertThat(tools).allSatisfy(tool -> assertThat(tool.annotations().readOnlyHint()).isTrue());
	}

	@Test
	void searchRequiresACourseCodeAndAQuery() {
		McpSchema.Tool search = server.listTools().stream()
				.filter(tool -> tool.name().equals("search_course_material"))
				.findFirst()
				.orElseThrow();

		assertThat(search.inputSchema().get("required")).asInstanceOf(LIST)
				.containsExactlyInAnyOrder("courseCode", "query");
	}

}
