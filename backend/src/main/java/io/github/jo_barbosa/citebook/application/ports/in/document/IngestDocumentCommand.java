package io.github.jo_barbosa.citebook.application.ports.in.document;

public record IngestDocumentCommand(String courseCode, String fileName, byte[] content) {
}
