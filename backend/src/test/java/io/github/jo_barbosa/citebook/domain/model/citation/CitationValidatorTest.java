package io.github.jo_barbosa.citebook.domain.model.citation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

class CitationValidatorTest {

	private static final List<Excerpt> SENT = List.of(
			new Excerpt(1, "ods.pdf", 223, "A heap is a complete binary tree.", 0.71),
			new Excerpt(2, "ods.pdf", 224, "It is stored in an array.", 0.66),
			new Excerpt(3, "notes.pdf", 4, "remove() takes O(log n).", 0.60));

	private final CitationValidator validator = new CitationValidator();

	@Test
	void keepsValidMarkersAndListsOnlyTheCitedExcerpts() {
		CheckedAnswer answer = validator.check("A heap is a tree [1]. It lives in an array [2].", SENT);

		assertThat(answer.text()).isEqualTo("A heap is a tree [1]. It lives in an array [2].");
		assertThat(answer.citations()).containsExactly(
				new Citation(1, "ods.pdf", 223, "A heap is a complete binary tree."),
				new Citation(2, "ods.pdf", 224, "It is stored in an array."));
		assertThat(answer.rejectedCitations()).isZero();
		assertThat(answer.grounded()).isTrue();
	}

	@Test
	void removesAMarkerThatPointsToNoExcerpt() {
		CheckedAnswer answer = validator.check("Heaps are fast [9]. They are trees [1].", SENT);

		assertThat(answer.text()).isEqualTo("Heaps are fast. They are trees [1].");
		assertThat(answer.rejectedCitations()).isEqualTo(1);
		assertThat(answer.citations()).extracting(Citation::n).containsExactly(1);
	}

	@Test
	void keepsTheValidNumbersOfAGroupedMarker() {
		CheckedAnswer answer = validator.check("A heap is a tree in an array [1, 9, 3].", SENT);

		assertThat(answer.text()).isEqualTo("A heap is a tree in an array [1, 3].");
		assertThat(answer.rejectedCitations()).isEqualTo(1);
		assertThat(answer.citations()).extracting(Citation::n).containsExactly(1, 3);
	}

	@Test
	void checksAdjacentMarkersOneByOne() {
		CheckedAnswer answer = validator.check("A heap is a tree [2][9][1].", SENT);

		assertThat(answer.text()).isEqualTo("A heap is a tree [2][1].");
		// Listed in the order they appear in the answer.
		assertThat(answer.citations()).extracting(Citation::n).containsExactly(2, 1);
	}

	@Test
	void anAnswerWithNoValidCitationIsNotGrounded() {
		CheckedAnswer answer = validator.check("Heaps are trees [7]. And fast [8].", SENT);

		assertThat(answer.text()).isEqualTo("Heaps are trees. And fast.");
		assertThat(answer.citations()).isEmpty();
		assertThat(answer.rejectedCitations()).isEqualTo(2);
		assertThat(answer.grounded()).isFalse();
	}

	@Test
	void anAnswerWithoutMarkersIsNotGrounded() {
		CheckedAnswer answer = validator.check("The material does not cover this question.", SENT);

		assertThat(answer.text()).isEqualTo("The material does not cover this question.");
		assertThat(answer.rejectedCitations()).isZero();
		assertThat(answer.grounded()).isFalse();
	}

	@Test
	void citesEachExcerptOnce() {
		CheckedAnswer answer = validator.check("Tree [1]. Array [1, 1]. Again [1].", SENT);

		assertThat(answer.text()).isEqualTo("Tree [1]. Array [1]. Again [1].");
		assertThat(answer.citations()).hasSize(1);
	}

	@Test
	void bracketsInCodeAreNotCitations() {
		String text = """
				The root is `a[0]` and the children of i are at a[2*i+1] and a[2 * i + 2] [2].
				```java
				int[] a = new int[10];
				List<Integer> xs = List.of(4, 5); // [4, 5]
				```
				So remove() is fast [3].""";

		CheckedAnswer answer = validator.check(text, SENT);

		assertThat(answer.text()).isEqualTo(text);
		assertThat(answer.rejectedCitations()).isZero();
		assertThat(answer.citations()).extracting(Citation::n).containsExactly(2, 3);
	}

	@Test
	void indexingIsNotACitation() {
		CheckedAnswer answer = validator.check("The minimum is a[0] and f(x)[1] is the next [1].", SENT);

		assertThat(answer.text()).isEqualTo("The minimum is a[0] and f(x)[1] is the next [1].");
		assertThat(answer.rejectedCitations()).isZero();
	}

	@Test
	void longNumbersAreNotCitations() {
		CheckedAnswer answer = validator.check("As published in [2016], heaps are trees [1].", SENT);

		assertThat(answer.text()).isEqualTo("As published in [2016], heaps are trees [1].");
		assertThat(answer.rejectedCitations()).isZero();
	}

	@Test
	void keepsTheNumbersGivenBySearch() {
		List<Excerpt> sent = List.of(new Excerpt(5, "ods.pdf", 30, "text", 0.5));

		CheckedAnswer answer = validator.check("Claim [5]. Other [1].", sent);

		assertThat(answer.text()).isEqualTo("Claim [5]. Other.");
		assertThat(answer.citations()).extracting(Citation::n).containsExactly(5);
	}

}
