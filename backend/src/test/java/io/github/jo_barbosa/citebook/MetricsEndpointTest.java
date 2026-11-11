package io.github.jo_barbosa.citebook;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** The wiring: what Prometheus sees when it scrapes the app. Metrics export is off in tests unless asked for. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
@AutoConfigureMetrics
class MetricsEndpointTest {

	@Autowired
	private MockMvc mvc;

	@Test
	void prometheusEndpointExposesTheCustomCounters() throws Exception {
		mvc.perform(get("/actuator/prometheus"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("citebook_citations_rejected_total")))
				.andExpect(content().string(containsString("citebook_questions_no_material_total")))
				.andExpect(content().string(containsString("citebook_mcp_tool_calls_total")))
				.andExpect(content().string(containsString("tool=\"list_courses\"")))
				.andExpect(content().string(containsString("tool=\"search_course_material\"")));
	}

}
