package io.github.jo_barbosa.citebook.application.services.question;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

@Service
public class QuestionService implements QuestionUseCases {

	private final CourseRepositoryPort courses;
	private final ChunkStorePort chunkStore;
	private final SearchSettings settings;

	public QuestionService(CourseRepositoryPort courses, ChunkStorePort chunkStore, SearchSettings settings) {
		this.courses = courses;
		this.chunkStore = chunkStore;
		this.settings = settings;
	}

	@Override
	public List<Excerpt> search(SearchCommand command) {
		if (command.query() == null || command.query().isBlank()) {
			throw new IllegalArgumentException("query must not be blank");
		}
		int k = command.k() == null ? settings.defaultK() : command.k();
		if (k < 1 || k > settings.maxK()) {
			throw new IllegalArgumentException("k must be between 1 and " + settings.maxK() + ", got " + k);
		}
		if (courses.findByCode(command.courseCode()).isEmpty()) {
			throw new CourseNotFoundException(command.courseCode(), courses.findAllCodes());
		}

		List<ChunkMatch> matches = chunkStore.search(command.courseCode(), command.query(), k,
				settings.scoreThreshold());
		return IntStream.range(0, matches.size())
				.mapToObj(i -> toExcerpt(i + 1, matches.get(i)))
				.toList();
	}

	private static Excerpt toExcerpt(int n, ChunkMatch match) {
		return new Excerpt(n, match.fileName(), match.page(), match.text(), match.score());
	}

}
