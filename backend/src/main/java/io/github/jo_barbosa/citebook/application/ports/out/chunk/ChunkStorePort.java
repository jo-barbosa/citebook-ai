package io.github.jo_barbosa.citebook.application.ports.out.chunk;

import java.util.List;
import java.util.UUID;

import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;

public interface ChunkStorePort {

	void save(String courseCode, UUID documentId, String fileName, List<Chunk> chunks);

	List<ChunkMatch> search(String courseCode, String query, int k, double minScore);

}
