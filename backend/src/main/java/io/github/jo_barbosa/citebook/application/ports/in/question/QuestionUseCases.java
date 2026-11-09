package io.github.jo_barbosa.citebook.application.ports.in.question;

import java.util.List;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

public interface QuestionUseCases {

	List<Excerpt> search(SearchCommand command);

	AnswerView ask(AskCommand command);

}
