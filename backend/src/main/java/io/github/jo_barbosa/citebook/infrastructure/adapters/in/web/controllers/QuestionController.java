package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.request.AskQuestionRequest;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.AnswerResponse;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers.QuestionWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

@RestController
@Tag(name = "Questions", description = "Ask questions about a course's material")
@RequestMapping("/api/courses/{courseCode}/questions")
public class QuestionController {

	private final QuestionUseCases questionUseCases;

	public QuestionController(QuestionUseCases questionUseCases) {
		this.questionUseCases = questionUseCases;
	}

	@Operation(summary = "Ask a question about a course",
			description = "Answers only from the most relevant excerpts of the course. Every [n] in the answer points "
					+ "to one of the returned citations; citations to excerpts that were not retrieved are removed. "
					+ "grounded is false when the answer cites nothing, including when no relevant material was "
					+ "found (then the model is not called).")
	@ApiResponse(responseCode = "200", description = "The answer and the excerpts it cites")
	@ApiResponse(responseCode = "400", description = "Invalid course code, or a blank or too long question",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No course with that code",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "The embedding service or the chat model is unavailable",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public AnswerResponse ask(
			@PathVariable @Pattern(regexp = "[a-z0-9-]{1,30}",
					message = "must be 1 to 30 lowercase letters, digits or hyphens") String courseCode,
			@Valid @RequestBody AskQuestionRequest request) {
		return QuestionWebMapper.toResponse(questionUseCases.ask(QuestionWebMapper.toCommand(courseCode, request)));
	}

}
