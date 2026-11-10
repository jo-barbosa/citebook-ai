package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response;

import java.util.List;

public record AnswerResponse(String answer, boolean grounded, List<CitationResponse> citations) {
}
