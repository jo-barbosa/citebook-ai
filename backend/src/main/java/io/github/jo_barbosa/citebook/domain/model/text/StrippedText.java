package io.github.jo_barbosa.citebook.domain.model.text;

/** Text after {@link InvisibleCharacterFilter#strip}, and how many characters (code points) were removed. */
public record StrippedText(String text, int removed) {
}
