package io.github.jo_barbosa.citebook.evaluation;

import java.util.List;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

/**
 * Retrieval metrics over ranks. A rank is the 1-based position ({@code Excerpt.n}) of the first relevant excerpt for
 * a question, or 0 when none of the retrieved excerpts is relevant.
 */
final class RetrievalMetrics {

	private RetrievalMetrics() {
	}

	/**
	 * An excerpt is relevant when it comes from the expected document and from one of the expected pages: hits are
	 * counted per page, so the questions do not depend on the chunk size.
	 */
	static int firstRelevantRank(List<Excerpt> excerpts, String expectedDocument, List<Integer> expectedPages) {
		return excerpts.stream()
				.filter(excerpt -> excerpt.fileName().equals(expectedDocument)
						&& expectedPages.contains(excerpt.page()))
				.mapToInt(Excerpt::n)
				.findFirst()
				.orElse(0);
	}

	/** Share of questions whose first relevant excerpt is within the top {@code k}. */
	static double hitRate(List<Integer> ranks, int k) {
		requireNotEmpty(ranks);
		long hits = ranks.stream().filter(rank -> rank >= 1 && rank <= k).count();
		return (double) hits / ranks.size();
	}

	/** Mean of 1/rank over all questions; a miss (rank 0) contributes 0. */
	static double meanReciprocalRank(List<Integer> ranks) {
		requireNotEmpty(ranks);
		return ranks.stream()
				.mapToDouble(rank -> rank == 0 ? 0.0 : 1.0 / rank)
				.average()
				.orElseThrow();
	}

	private static void requireNotEmpty(List<Integer> ranks) {
		if (ranks.isEmpty()) {
			throw new IllegalArgumentException("no ranks to measure");
		}
	}

}
