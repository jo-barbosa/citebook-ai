package io.github.jo_barbosa.citebook.application.ports.out.document;

import java.util.Optional;
import java.util.UUID;

import io.github.jo_barbosa.citebook.domain.model.document.Document;

public interface DocumentRepositoryPort {

	Optional<Document> findByCourseIdAndFileName(UUID courseId, String fileName);

	Document create(UUID courseId, String fileName, String sha256, int pageCount, int chunkCount);

	void deleteById(UUID id);

}
