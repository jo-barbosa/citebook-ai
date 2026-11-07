package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

@RestController
@Tag(name = "Documents", description = "Upload course material")
@RequestMapping("/api/courses/{courseCode}/documents")
public class DocumentController {

	private final DocumentUseCases documentUseCases;

	public DocumentController(DocumentUseCases documentUseCases) {
		this.documentUseCases = documentUseCases;
	}

	@Operation(summary = "Upload a PDF to a course",
			description = "Creates the course on first upload. The same file again changes nothing; a changed file "
					+ "with the same name replaces the old version.")
	@ApiResponse(responseCode = "201", description = "The document was indexed or replaced")
	@ApiResponse(responseCode = "200", description = "The same file was already indexed; nothing changed")
	@ApiResponse(responseCode = "400", description = "Invalid course code, missing file or file without a name",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "413", description = "The file exceeds the upload limit",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Not a PDF, or a PDF without extractable text",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "The embedding service is unavailable",
			content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
					schema = @Schema(implementation = ProblemDetail.class)))
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
