package io.github.jo_barbosa.citebook.domain.model.question;

/**
 * A retrieved chunk as shown to the model and the user. {@code n} is its rank (1 = most similar); the prompt labels
 * the excerpt with it and citations refer back to it as {@code [n]}.
 */
public record Excerpt(int n, String fileName, int page, String text, double score) {
}
