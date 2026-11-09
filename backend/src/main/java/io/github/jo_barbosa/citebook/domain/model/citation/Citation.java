package io.github.jo_barbosa.citebook.domain.model.citation;

/** An excerpt the answer cites as [n]: its number, source and text, as shown to the user. */
public record Citation(int n, String fileName, int page, String excerpt) {
}
