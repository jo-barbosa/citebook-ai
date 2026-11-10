package io.github.jo_barbosa.citebook.application.services.question;

import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.jo_barbosa.citebook.application.ports.in.question.AnswerView;
import io.github.jo_barbosa.citebook.application.ports.in.question.AskCommand;
import io.github.jo_barbosa.citebook.application.ports.in.question.QuestionUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.question.SearchCommand;
import io.github.jo_barbosa.citebook.application.ports.out.chat.ChatPort;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.citation.CheckedAnswer;
import io.github.jo_barbosa.citebook.domain.model.citation.CitationValidator;
import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;
import io.github.jo_barbosa.citebook.domain.model.question.PromptBuilder;
import io.github.jo_barbosa.citebook.domain.model.text.InvisibleCharacterFilter;
import io.github.jo_barbosa.citebook.domain.model.text.StrippedText;

@Service
public class QuestionService implements QuestionUseCases {

	private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

	public static final String NO_MATERIAL_ANSWER = "No relevant material was found in this course for this question.";

	private final CourseRepositoryPort courses;
	private final ChunkStorePort chunkStore;
	private final SearchSettings settings;
	private final ChatPort chat;
	// Stateless domain logic with no configuration: created here, not injected.
	private final PromptBuilder prompts = new PromptBuilder();
	private final CitationValidator citations = new CitationValidator();
	private final InvisibleCharacterFilter invisible = new InvisibleCharacterFilter();

	public QuestionService(CourseRepositoryPort courses, ChunkStorePort chunkStore, SearchSettings settings,
			ChatPort chat) {
		this.courses = courses;
		this.chunkStore = chunkStore;
		this.settings = settings;
		this.chat = chat;
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

	@Override
	public AnswerView ask(AskCommand command) {
		String raw = command.question();
		if (raw == null || raw.isBlank()) {
			throw new IllegalArgumentException("question must not be blank");
		}
		if (raw.length() > MAX_QUESTION_LENGTH) {
			throw new IllegalArgumentException("question must be at most " + MAX_QUESTION_LENGTH
					+ " characters, got " + raw.length());
		}
		StrippedText stripped = invisible.strip(raw);
		if (stripped.removed() > 0) {
			log.warn("Removed {} invisible characters from a question", stripped.removed());
		}
		String question = stripped.text();
		if (question.isBlank()) {
			// Only invisible characters: nothing to search for, so nothing the model may answer from.
			return new AnswerView(NO_MATERIAL_ANSWER, false, List.of());
		}

		List<Excerpt> excerpts = search(new SearchCommand(command.courseCode(), question, null));
		if (excerpts.isEmpty()) {
			// Without material the model could only answer from its own knowledge: no call.
			return new AnswerView(NO_MATERIAL_ANSWER, false, List.of());
		}

		String answer = chat.complete(prompts.systemMessage(), prompts.userMessage(question, excerpts));
		CheckedAnswer checked = citations.check(answer, excerpts);
		return new AnswerView(checked.text(), checked.grounded(), checked.citations());
	}

	private static Excerpt toExcerpt(int n, ChunkMatch match) {
		return new Excerpt(n, match.fileName(), match.page(), match.text(), match.score());
	}

}
