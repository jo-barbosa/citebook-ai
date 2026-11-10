package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.request;

import io.github.jo_barbosa.citebook.application.services.question.QuestionService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskQuestionRequest(
		@NotBlank @Size(max = QuestionService.MAX_QUESTION_LENGTH,
				message = "must be at most " + QuestionService.MAX_QUESTION_LENGTH + " characters") String question) {
}
