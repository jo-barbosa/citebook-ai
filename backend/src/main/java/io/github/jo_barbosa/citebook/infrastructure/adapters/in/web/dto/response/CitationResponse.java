package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response;

public record CitationResponse(int n, String fileName, int page, String excerpt) {
}
