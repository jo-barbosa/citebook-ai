package io.github.jo_barbosa.citebook.domain.model.citation;

import java.util.List;

/**
 * The model's answer after the citation check: the text with invalid markers removed, the excerpts it cites, and how
 * many cited numbers matched no excerpt.
 */
public record CheckedAnswer(String text, List<Citation> citations, int rejectedCitations) {

	/** An answer is grounded when it cites at least one excerpt that was actually sent. */
	public boolean grounded() {
		return !citations.isEmpty();
	}

}
