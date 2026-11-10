package io.github.jo_barbosa.citebook.domain.model.text;

import java.util.regex.Pattern;

/**
 * Removes characters that have no glyph but are read by the model, so the model sees the same text a person sees.
 * These are the Unicode format characters (category Cf: zero-width spaces and joiners, the byte order mark, soft
 * hyphens, bidi controls, and the tag characters U+E0000 to U+E007F that can hide a whole ASCII sentence) and the
 * variation selectors, which can also encode hidden bytes. Letters, digits, punctuation and spacing are kept.
 *
 * <p>The text is cleaned, not rejected: ordinary PDFs contain stray zero-width spaces and soft hyphens, and removing a
 * character nobody sees changes nothing a reader sees.
 */
public class InvisibleCharacterFilter {

	private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\x{FE00}-\\x{FE0F}\\x{E0100}-\\x{E01EF}]");

	public StrippedText strip(String text) {
		long removed = INVISIBLE.matcher(text).results().count();
		if (removed == 0) {
			return new StrippedText(text, 0);
		}
		return new StrippedText(INVISIBLE.matcher(text).replaceAll(""), (int) removed);
	}

}
