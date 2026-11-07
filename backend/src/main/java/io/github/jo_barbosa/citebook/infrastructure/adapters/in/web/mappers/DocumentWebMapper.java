package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.IngestDocumentResponse;

public final class DocumentWebMapper {

	private DocumentWebMapper() {
	}

	public static IngestDocumentCommand toCommand(String courseCode, MultipartFile file) throws IOException {
		return new IngestDocumentCommand(courseCode, fileName(file), file.getBytes());
	}

	public static IngestDocumentResponse toResponse(IngestDocumentView view) {
		return new IngestDocumentResponse(view.documentId(), view.pages(), view.chunks(), view.status().name());
	}

	private static String fileName(MultipartFile file) {
		String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
		if (name.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file has no name.");
		}
		return name;
	}

}
