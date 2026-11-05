package io.github.jo_barbosa.citebook.domain.model.course;

import java.time.Instant;
import java.util.UUID;

public record Course(UUID id, String code, Instant createdAt) {
}
