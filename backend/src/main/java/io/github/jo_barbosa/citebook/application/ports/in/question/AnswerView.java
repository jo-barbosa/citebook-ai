package io.github.jo_barbosa.citebook.application.ports.in.question;

import java.util.List;

import io.github.jo_barbosa.citebook.domain.model.citation.Citation;

/**
 * @param grounded whether the answer cites at least one excerpt that was sent to the model; false also when no
 * material was found and the model was not called
 * @param citations the cited excerpts, in the order they appear in the answer
 */
public record AnswerView(String answer, boolean grounded, List<Citation> citations) {
}
