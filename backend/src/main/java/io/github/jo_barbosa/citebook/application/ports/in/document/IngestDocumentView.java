package io.github.jo_barbosa.citebook.application.ports.in.document;

import java.util.UUID;

public record IngestDocumentView(UUID documentId, int pages, int chunks, IngestStatus status) {
}
