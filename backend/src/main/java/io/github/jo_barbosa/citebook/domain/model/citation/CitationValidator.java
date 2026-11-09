package io.github.jo_barbosa.citebook.domain.model.citation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.jo_barbosa.citebook.domain.model.question.Excerpt;

/**
 * Checks the [n] markers of an answer against the excerpts that were sent to the model. A number with no excerpt is
 * removed from the text and counted; the excerpts that are cited become the answer's citations.
 */
public class CitationValidator {

	/** Inline code and fenced code blocks: brackets there are code ("a[0]", "[1, 2, 3]"), not citations. */
	private static final Pattern CODE = Pattern.compile("```[\\s\\S]*?```|`[^`\\n]*`");

	/**
	 * A marker ([1], [1, 3], [ 2 ]) with the spaces before it. Not right after a letter, digit, "_" or ")", where
	 * brackets index ("a[0]", "f(x)[1]"). Up to 3 digits, so a year like [2016] is not a citation.
	 */
	private static final Pattern MARKER = Pattern
			.compile("([ \\t]*)(?<![\\w)])\\[(\\s*\\d{1,3}\\s*(?:,\\s*\\d{1,3}\\s*)*)]");

	public CheckedAnswer check(String answer, List<Excerpt> excerpts) {
		return new Check(excerpts).run(answer);
	}

	/** The state of one check: which excerpts were sent, which are cited, how many numbers were rejected. */
	private static final class Check {

		private final Map<Integer, Excerpt> sent;
		// In the order they first appear in the answer.
		private final Set<Integer> cited = new LinkedHashSet<>();
		private int rejected;

		Check(List<Excerpt> excerpts) {
			this.sent = excerpts.stream().collect(Collectors.toMap(Excerpt::n, Function.identity()));
		}

		CheckedAnswer run(String answer) {
			StringBuilder text = new StringBuilder();
			Matcher code = CODE.matcher(answer);
			int start = 0;
			while (code.find()) {
				text.append(checkMarkers(answer.substring(start, code.start()))).append(code.group());
				start = code.end();
			}
			text.append(checkMarkers(answer.substring(start)));
			List<Citation> citations = cited.stream()
					.map(sent::get)
					.map(excerpt -> new Citation(excerpt.n(), excerpt.fileName(), excerpt.page(), excerpt.text()))
					.toList();
			return new CheckedAnswer(text.toString(), citations, rejected);
		}

		/** Keeps the numbers of excerpts that were sent; drops the others, and the whole marker if none is left. */
		private String checkMarkers(String prose) {
			return MARKER.matcher(prose).replaceAll(marker -> {
				List<Integer> kept = new ArrayList<>();
				for (String number : marker.group(2).split(",")) {
					int n = Integer.parseInt(number.strip());
					if (!sent.containsKey(n)) {
						rejected++;
					}
					else if (!kept.contains(n)) {
						kept.add(n);
						cited.add(n);
					}
				}
				String replacement = kept.isEmpty() ? ""
						: marker.group(1) + kept.stream().map(String::valueOf)
								.collect(Collectors.joining(", ", "[", "]"));
				return Matcher.quoteReplacement(replacement);
			});
		}

	}

}
