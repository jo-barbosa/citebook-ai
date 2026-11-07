package io.github.jo_barbosa.citebook.application.ports.in.question;

/**
 * @param k number of excerpts to return; {@code null} uses the configured default
 */
public record SearchCommand(String courseCode, String query, Integer k) {
}
