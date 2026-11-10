package io.github.jo_barbosa.citebook.application.ports.in.question;

import java.util.List;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

public interface QuestionUseCases {

	/** Spec 5: questions are short; longer input is refused before any search or model call. */
	int MAX_QUESTION_LENGTH = 1000;

	List<Excerpt> search(SearchCommand command);

	AnswerView ask(AskCommand command);

}
