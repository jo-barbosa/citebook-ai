package io.github.jo_barbosa.citebook.application.services.question;

/**
 * @param defaultK excerpts returned when the caller does not ask for a number
 * @param maxK upper bound for a requested {@code k}
 * @param scoreThreshold minimum similarity score; 0.0 keeps every match
 */
public record SearchSettings(int defaultK, int maxK, double scoreThreshold) {
}
