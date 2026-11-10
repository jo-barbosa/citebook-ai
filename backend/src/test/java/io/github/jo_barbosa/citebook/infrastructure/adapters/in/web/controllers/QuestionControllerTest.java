package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.in.question.AnswerView;
import io.github.jo_barbosa.citebook.application.ports.in.question.AskCommand;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.services.question.QuestionService;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.citation.Citation;

@WebMvcTest(QuestionController.class)
class QuestionControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private QuestionUseCases questionUseCases;

	@Test
	void answersWithTheCheckedCitations() throws Exception {
		when(questionUseCases.ask(new AskCommand("esinf", "O que é um heap?"))).thenReturn(new AnswerView(
				"Um heap é uma árvore binária completa [1].", true,
				List.of(new Citation(1, "ods.pdf", 223, "A heap is a complete binary tree."))));

		ask("esinf", "{\"question\": \"O que é um heap?\"}")
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "answer": "Um heap é uma árvore binária completa [1].",
						  "grounded": true,
						  "citations": [
						    { "n": 1, "fileName": "ods.pdf", "page": 223, "excerpt": "A heap is a complete binary tree." }
						  ]
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void noMaterialIsANormalAnswerThatIsNotGrounded() throws Exception {
		when(questionUseCases.ask(any())).thenReturn(
				new AnswerView(QuestionService.NO_MATERIAL_ANSWER, false, List.of()));

		ask("esinf", "{\"question\": \"Quem ganhou o Euro 2016?\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(QuestionService.NO_MATERIAL_ANSWER))
				.andExpect(jsonPath("$.grounded").value(false))
				.andExpect(jsonPath("$.citations").isEmpty());
	}

	@Test
	void aBlankQuestionIs400AndNothingIsAsked() throws Exception {
		ask("esinf", "{\"question\": \"   \"}")
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("/problems/validation"))
				.andExpect(jsonPath("$.detail").value("question: must not be blank"));

		verifyNoInteractions(questionUseCases);
	}

	@Test
	void aMissingQuestionIs400() throws Exception {
		ask("esinf", "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("question: must not be blank"));

		verifyNoInteractions(questionUseCases);
	}

	@Test
	void aQuestionOverTheLimitIs400() throws Exception {
		String question = "a".repeat(QuestionService.MAX_QUESTION_LENGTH + 1);

		ask("esinf", "{\"question\": \"" + question + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("question: must be at most 1000 characters"));

		verifyNoInteractions(questionUseCases);
	}

	@Test
	void anInvalidCourseCodeIs400() throws Exception {
		ask("ESINF", "{\"question\": \"O que é um heap?\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(
						"courseCode: must be 1 to 30 lowercase letters, digits or hyphens"));

		verifyNoInteractions(questionUseCases);
	}

	@Test
	void anUnknownCourseIs404() throws Exception {
		when(questionUseCases.ask(any())).thenThrow(new CourseNotFoundException("xpto", List.of("esinf")));

		ask("xpto", "{\"question\": \"O que é um heap?\"}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.type").value("/problems/not-found"));
	}

	@Test
	void anUnavailableChatModelIs503() throws Exception {
		when(questionUseCases.ask(any())).thenThrow(
				new ExternalServiceUnavailableException("chat model", new RuntimeException("rate limited")));

		ask("esinf", "{\"question\": \"O que é um heap?\"}")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value("The chat model is unavailable. Try again in a few minutes."));
	}

	private ResultActions ask(String courseCode, String json) throws Exception {
		return mvc.perform(post("/api/courses/{courseCode}/questions", courseCode)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

}
