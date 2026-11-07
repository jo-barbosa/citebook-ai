package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestStatus;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.IngestDocumentResponse;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers.DocumentWebMapper;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/courses/{courseCode}/documents")
public class DocumentController {

	private final DocumentUseCases documentUseCases;

	public DocumentController(DocumentUseCases documentUseCases) {
		this.documentUseCases = documentUseCases;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<IngestDocumentResponse> upload(
			@PathVariable @Pattern(regexp = "[a-z0-9-]{1,30}",
					message = "must be 1 to 30 lowercase letters, digits or hyphens") String courseCode,
			@RequestParam("file") MultipartFile file) throws IOException {
		IngestDocumentView view = documentUseCases.ingest(DocumentWebMapper.toCommand(courseCode, file));
		HttpStatus status = view.status() == IngestStatus.ALREADY_INDEXED ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(DocumentWebMapper.toResponse(view));
	}

}
