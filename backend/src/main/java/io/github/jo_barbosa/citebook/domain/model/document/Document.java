package io.github.jo_barbosa.citebook.domain.model.document;

import java.time.Instant;
import java.util.UUID;

public record Document(UUID id, UUID courseId, String fileName, String sha256, int pageCount, int chunkCount,
		Instant ingestedAt) {
}
