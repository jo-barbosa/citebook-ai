package io.github.jo_barbosa.citebook.application.ports.in.document;

public interface DocumentUseCases {

	IngestDocumentView ingest(IngestDocumentCommand command);

}
