package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers;

import io.github.jo_barbosa.citebook.application.ports.in.question.AnswerView;
import io.github.jo_barbosa.citebook.application.ports.in.question.AskCommand;
import io.github.jo_barbosa.citebook.domain.model.citation.Citation;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.request.AskQuestionRequest;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.AnswerResponse;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.CitationResponse;

public final class QuestionWebMapper {

	private QuestionWebMapper() {
	}

	public static AskCommand toCommand(String courseCode, AskQuestionRequest request) {
		return new AskCommand(courseCode, request.question());
	}

	public static AnswerResponse toResponse(AnswerView view) {
		return new AnswerResponse(view.answer(), view.grounded(),
				view.citations().stream().map(QuestionWebMapper::toResponse).toList());
	}

	private static CitationResponse toResponse(Citation citation) {
		return new CitationResponse(citation.n(), citation.fileName(), citation.page(), citation.excerpt());
	}

}
