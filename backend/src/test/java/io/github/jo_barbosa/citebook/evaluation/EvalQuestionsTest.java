package io.github.jo_barbosa.citebook.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvalQuestionsTest {

	private static final int CORPUS_PAGES = 334;

	@Test
	void loadsTheTwentyFiveQuestions() {
		assertThat(EvalQuestion.load()).hasSize(25);
	}

	@Test
	void everyQuestionPointsToPagesOfTheCorpus() {
		List<EvalQuestion> questions = EvalQuestion.load();

		assertThat(questions).allSatisfy(question -> {
			assertThat(question.question()).isNotBlank();
			assertThat(question.expectedDocument()).isEqualTo("ods-java.pdf");
			assertThat(question.expectedPages()).isNotEmpty().allSatisfy(
					page -> assertThat(page).isBetween(1, CORPUS_PAGES));
		});
	}

	@Test
	void loadsTheFiveOffTopicQuestions() {
		assertThat(EvalQuestion.loadOffTopic()).hasSize(5).allSatisfy(question -> assertThat(question).isNotBlank());
	}

}
