package io.github.jo_barbosa.citebook.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** One evaluation question and where its answer is: a document and its physical PDF pages. */
record EvalQuestion(String question, String expectedDocument, List<Integer> expectedPages) {

	private static final String RESOURCE = "/eval/questions.json";
	private static final String OFF_TOPIC_RESOURCE = "/eval/off-topic.json";

	static List<EvalQuestion> load() {
		return read(RESOURCE, new TypeReference<List<EvalQuestion>>() {
		});
	}

	/** Questions the corpus does not answer: their scores show what a score threshold would filter out. */
	static List<String> loadOffTopic() {
		return read(OFF_TOPIC_RESOURCE, new TypeReference<List<String>>() {
		});
	}

	private static <T> T read(String resource, TypeReference<T> type) {
		try (InputStream json = EvalQuestion.class.getResourceAsStream(resource)) {
			if (json == null) {
				throw new IllegalStateException("Missing test resource " + resource);
			}
			return JsonMapper.builder().build().readValue(json, type);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
