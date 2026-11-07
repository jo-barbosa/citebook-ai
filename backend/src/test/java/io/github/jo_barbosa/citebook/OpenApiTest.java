package io.github.jo_barbosa.citebook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
@AutoConfigureMockMvc
class OpenApiTest {

	private static final String UPLOAD = "$.paths['/api/courses/{courseCode}/documents'].post";

	@Autowired
	private MockMvc mvc;

	@Test
	void apiDocsDescribeTheApiAndTheUploadEndpoint() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("citebook-ai API"))
				.andExpect(jsonPath(UPLOAD + ".summary").value("Upload a PDF to a course"))
				.andExpect(jsonPath(UPLOAD + ".responses['200']").exists())
				.andExpect(jsonPath(UPLOAD + ".responses['201']").exists())
				.andExpect(jsonPath(UPLOAD + ".responses['400']").exists())
				.andExpect(jsonPath(UPLOAD + ".responses['413']").exists())
				.andExpect(jsonPath(UPLOAD + ".responses['422']").exists())
				.andExpect(jsonPath(UPLOAD + ".responses['503']").exists());
	}

	@Test
	void swaggerUiIsServed() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

}
