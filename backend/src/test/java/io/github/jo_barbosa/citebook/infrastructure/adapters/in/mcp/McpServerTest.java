package io.github.jo_barbosa.citebook.infrastructure.adapters.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jo_barbosa.citebook.TestcontainersConfiguration;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;

/** The wiring: what an MCP client sees when it lists the server's tools. */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
class McpServerTest {

	@Autowired
	McpSyncServer server;

	@Autowired
	MockMvc mockMvc;

	@Test
	void serverIsNamedAfterTheApplication() {
		assertThat(server.getServerInfo().name()).isEqualTo("citebook-ai");
	}

	@Test
	void advertisesOnlyTheToolsCapability() {
		McpSchema.ServerCapabilities capabilities = server.getServerCapabilities();

		assertThat(capabilities.tools()).isNotNull();
		assertThat(capabilities.prompts()).isNull();
		assertThat(capabilities.resources()).isNull();
		assertThat(capabilities.completions()).isNull();
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

	@Test
	void initializeOverStreamableHttpStartsASession() throws Exception {
		String initialize = """
				{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
				"capabilities":{},"clientInfo":{"name":"test","version":"0"}}}""";

		mockMvc.perform(post("/mcp")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
				.content(initialize))
				.andExpect(status().isOk())
				.andExpect(header().exists("Mcp-Session-Id"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("citebook-ai")));
	}

}
