package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestStatus;
import io.github.jo_barbosa.citebook.domain.exceptions.NotAPdfException;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

	private static final byte[] PDF = "%PDF-fake".getBytes();

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private DocumentUseCases documentUseCases;

	@Test
	void indexingANewDocumentReturns201() throws Exception {
		UUID id = UUID.randomUUID();
		when(documentUseCases.ingest(any())).thenReturn(new IngestDocumentView(id, 12, 30, IngestStatus.INDEXED));

		mvc.perform(multipart("/api/courses/esinf/documents").file(file("notes.pdf")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.documentId").value(id.toString()))
				.andExpect(jsonPath("$.pages").value(12))
				.andExpect(jsonPath("$.chunks").value(30))
				.andExpect(jsonPath("$.status").value("INDEXED"));

		ArgumentCaptor<IngestDocumentCommand> command = ArgumentCaptor.forClass(IngestDocumentCommand.class);
		verify(documentUseCases).ingest(command.capture());
		assertThat(command.getValue().courseCode()).isEqualTo("esinf");
		assertThat(command.getValue().fileName()).isEqualTo("notes.pdf");
		assertThat(command.getValue().content()).isEqualTo(PDF);
	}

	@Test
	void replacingADocumentReturns201() throws Exception {
		when(documentUseCases.ingest(any()))
				.thenReturn(new IngestDocumentView(UUID.randomUUID(), 3, 4, IngestStatus.REPLACED));

		mvc.perform(multipart("/api/courses/esinf/documents").file(file("notes.pdf")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REPLACED"));
	}

	@Test
	void anAlreadyIndexedDocumentReturns200() throws Exception {
		when(documentUseCases.ingest(any()))
				.thenReturn(new IngestDocumentView(UUID.randomUUID(), 3, 4, IngestStatus.ALREADY_INDEXED));

		mvc.perform(multipart("/api/courses/esinf/documents").file(file("notes.pdf")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ALREADY_INDEXED"));
	}

	@Test
	void onlyTheFileNameIsKeptWhenTheClientSendsAPath() throws Exception {
		when(documentUseCases.ingest(any()))
				.thenReturn(new IngestDocumentView(UUID.randomUUID(), 1, 1, IngestStatus.INDEXED));

		mvc.perform(multipart("/api/courses/esinf/documents").file(file("C:\\docs\\..\\notes.pdf")))
				.andExpect(status().isCreated());

		ArgumentCaptor<IngestDocumentCommand> command = ArgumentCaptor.forClass(IngestDocumentCommand.class);
		verify(documentUseCases).ingest(command.capture());
		assertThat(command.getValue().fileName()).isEqualTo("notes.pdf");
	}

	@Test
	void anInvalidCourseCodeIs400AndNothingIsIngested() throws Exception {
		mvc.perform(multipart("/api/courses/ESINF/documents").file(file("notes.pdf")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("/problems/validation"))
				.andExpect(jsonPath("$.detail").value(
						"courseCode: must be 1 to 30 lowercase letters, digits or hyphens"));

		verifyNoInteractions(documentUseCases);
	}

	@Test
	void aMissingFileIs400() throws Exception {
		mvc.perform(multipart("/api/courses/esinf/documents"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("/problems/validation"));

		verify(documentUseCases, never()).ingest(any());
	}

	@Test
	void aFileWithoutANameIs400() throws Exception {
		mvc.perform(multipart("/api/courses/esinf/documents").file(file("")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("/problems/validation"))
				.andExpect(jsonPath("$.detail").value("The uploaded file has no name."));

		verify(documentUseCases, never()).ingest(any());
	}

	@Test
	void anUnprocessableDocumentIs422() throws Exception {
		when(documentUseCases.ingest(any())).thenThrow(new NotAPdfException("notes.pdf"));

		mvc.perform(multipart("/api/courses/esinf/documents").file(file("notes.pdf")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.type").value("/problems/unprocessable-document"))
				.andExpect(jsonPath("$.detail").value("The file 'notes.pdf' is not a PDF."));
	}

	private static MockMultipartFile file(String name) {
		return new MockMultipartFile("file", name, "application/pdf", PDF);
	}

}
