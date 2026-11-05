package io.github.jo_barbosa.citebook.domain.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DomainExceptionsTest {

	@Test
	void courseNotFoundListsTheAvailableCourses() {
		CourseNotFoundException exception = new CourseNotFoundException("xpto", List.of("esinf", "lprog"));

		assertThat(exception)
				.isInstanceOf(NotFoundException.class)
				.hasMessage("No course with code 'xpto'. Available courses: esinf, lprog.");
	}

	@Test
	void courseNotFoundWhenThereAreNoCoursesYet() {
		CourseNotFoundException exception = new CourseNotFoundException("xpto", List.of());

		assertThat(exception).hasMessage("No course with code 'xpto'. There are no courses yet.");
	}

	@Test
	void notAPdfNamesTheFile() {
		NotAPdfException exception = new NotAPdfException("notes.txt");

		assertThat(exception)
				.isInstanceOf(UnprocessableDocumentException.class)
				.hasMessage("The file 'notes.txt' is not a PDF.");
	}

	@Test
	void pdfWithoutTextSuggestsAScannedDocument() {
		PdfWithoutTextException exception = new PdfWithoutTextException("Exame_2023.pdf");

		assertThat(exception)
				.isInstanceOf(UnprocessableDocumentException.class)
				.hasMessage("The PDF 'Exame_2023.pdf' has no extractable text. It may be a scanned document.");
	}

}
