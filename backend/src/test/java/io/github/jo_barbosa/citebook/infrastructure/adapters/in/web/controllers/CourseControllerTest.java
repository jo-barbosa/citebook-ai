package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jo_barbosa.citebook.application.ports.in.course.CourseUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.course.CourseView;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CourseUseCases courseUseCases;

	@Test
	void listsTheCoursesWithTheirDocumentCounts() throws Exception {
		when(courseUseCases.list()).thenReturn(List.of(new CourseView("arqcp", 1), new CourseView("esinf", 2)));

		mvc.perform(get("/api/courses"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						[{ "code": "arqcp", "documents": 1 }, { "code": "esinf", "documents": 2 }]
						""", JsonCompareMode.STRICT));
	}

	@Test
	void noCoursesIsAnEmptyList() throws Exception {
		when(courseUseCases.list()).thenReturn(List.of());

		mvc.perform(get("/api/courses"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]", JsonCompareMode.STRICT));
	}

}
