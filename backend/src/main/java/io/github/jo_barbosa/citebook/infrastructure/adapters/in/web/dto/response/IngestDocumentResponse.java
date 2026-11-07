package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response;

import java.util.UUID;

public record IngestDocumentResponse(UUID documentId, int pages, int chunks, String status) {
}
