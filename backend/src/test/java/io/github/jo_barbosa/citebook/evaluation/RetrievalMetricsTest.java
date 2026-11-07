package io.github.jo_barbosa.citebook.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

class RetrievalMetricsTest {

	private static final List<Excerpt> EXCERPTS = List.of(
			new Excerpt(1, "ods-java.pdf", 40, "...", 0.81),
			new Excerpt(2, "other.pdf", 45, "...", 0.79),
			new Excerpt(3, "ods-java.pdf", 45, "...", 0.77),
			new Excerpt(4, "ods-java.pdf", 46, "...", 0.70));

	@Test
	void firstRelevantRankIsTheNumberOfTheFirstExcerptFromAnExpectedPage() {
		assertThat(RetrievalMetrics.firstRelevantRank(EXCERPTS, "ods-java.pdf", List.of(45, 46))).isEqualTo(3);
	}

	@Test
	void anExcerptFromTheRightPageOfAnotherDocumentIsNotRelevant() {
		assertThat(RetrievalMetrics.firstRelevantRank(EXCERPTS, "ods-java.pdf", List.of(45))).isEqualTo(3);
	}

	@Test
	void firstRelevantRankIsZeroWhenNoExcerptIsRelevant() {
		assertThat(RetrievalMetrics.firstRelevantRank(EXCERPTS, "ods-java.pdf", List.of(99))).isZero();
		assertThat(RetrievalMetrics.firstRelevantRank(List.of(), "ods-java.pdf", List.of(40))).isZero();
	}

	@Test
	void hitRateIsTheShareOfQuestionsAnsweredWithinTheTopK() {
		List<Integer> ranks = List.of(1, 2, 0, 4);

		assertThat(RetrievalMetrics.hitRate(ranks, 1)).isEqualTo(0.25);
		assertThat(RetrievalMetrics.hitRate(ranks, 3)).isEqualTo(0.5);
		assertThat(RetrievalMetrics.hitRate(ranks, 5)).isEqualTo(0.75);
	}

	@Test
	void meanReciprocalRankCountsAMissAsZero() {
		// (1/1 + 1/2 + 0 + 1/4) / 4
		assertThat(RetrievalMetrics.meanReciprocalRank(List.of(1, 2, 0, 4))).isCloseTo(0.4375, within(1e-9));
	}

	@Test
	void emptyRanksAreRejected() {
		assertThatThrownBy(() -> RetrievalMetrics.hitRate(List.of(), 5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("no ranks to measure");
		assertThatThrownBy(() -> RetrievalMetrics.meanReciprocalRank(List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("no ranks to measure");
	}

}
