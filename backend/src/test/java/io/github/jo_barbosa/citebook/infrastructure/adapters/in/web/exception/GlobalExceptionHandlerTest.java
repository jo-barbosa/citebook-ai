package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.exception;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.domain.exceptions.CourseNotFoundException;
import io.github.jo_barbosa.citebook.domain.exceptions.NotAPdfException;
import io.github.jo_barbosa.citebook.domain.exceptions.PdfWithoutTextException;

class GlobalExceptionHandlerTest {

	private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
			.setControllerAdvice(new GlobalExceptionHandler(DataSize.ofMegabytes(50)))
			.build();

	@Test
	void notFoundIs404WithTheExceptionMessage() throws Exception {
		mvc.perform(get("/course-not-found"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("/problems/not-found"))
				.andExpect(jsonPath("$.title").value("Not found"))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("No course with code 'xpto'. Available courses: esinf."))
				.andExpect(jsonPath("$.instance").value("/course-not-found"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void unprocessableDocumentIs422WithTheExceptionMessage() throws Exception {
		mvc.perform(get("/not-a-pdf"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.type").value("/problems/unprocessable-document"))
				.andExpect(jsonPath("$.title").value("Unprocessable document"))
				.andExpect(jsonPath("$.detail").value("The file 'notes.txt' is not a PDF."));

		mvc.perform(get("/no-text"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.detail").value(
						"The PDF 'scan.pdf' has no extractable text. It may be a scanned document."));
	}

	@Test
	void externalServiceFailureIs503() throws Exception {
		mvc.perform(get("/unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("/problems/service-unavailable"))
				.andExpect(jsonPath("$.title").value("Service unavailable"))
				.andExpect(jsonPath("$.detail").value("The embedding service is unavailable. Try again in a few minutes."));
	}

	@Test
	void uploadTooLargeIs413WithTheConfiguredLimit() throws Exception {
		mvc.perform(get("/too-large"))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.type").value("/problems/payload-too-large"))
				.andExpect(jsonPath("$.detail").value("The file exceeds the 50 MB upload limit."))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void unexpectedErrorsGetAGenericMessage() throws Exception {
		mvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.type").value("/problems/internal-error"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void springsOwnErrorsUseTheSameFormat() throws Exception {
		mvc.perform(get("/only-post"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("/problems/method-not-allowed"))
				.andExpect(jsonPath("$.status").value(405))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@RestController
	static class ThrowingController {

		@GetMapping("/course-not-found")
		void courseNotFound() {
			throw new CourseNotFoundException("xpto", List.of("esinf"));
		}

		@GetMapping("/not-a-pdf")
		void notAPdf() {
			throw new NotAPdfException("notes.txt");
		}

		@GetMapping("/no-text")
		void noText() {
			throw new PdfWithoutTextException("scan.pdf");
		}

		@GetMapping("/unavailable")
		void unavailable() {
			throw new ExternalServiceUnavailableException("embedding service", new RuntimeException("connection refused"));
		}

		@GetMapping("/too-large")
		void tooLarge() {
			throw new MaxUploadSizeExceededException(DataSize.ofMegabytes(50).toBytes());
		}

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("secret internal detail");
		}

		@PostMapping("/only-post")
		void onlyPost() {
		}

	}

}
