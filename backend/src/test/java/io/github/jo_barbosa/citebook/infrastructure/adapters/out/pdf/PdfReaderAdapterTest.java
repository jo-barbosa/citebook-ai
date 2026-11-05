package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.application.ports.out.document.PdfReaderPort;
import io.github.jo_barbosa.citebook.domain.exceptions.NotAPdfException;
import io.github.jo_barbosa.citebook.domain.exceptions.PdfWithoutTextException;
import io.github.jo_barbosa.citebook.domain.model.chunk.Page;

class PdfReaderAdapterTest {

	private final PdfReaderPort reader = new PdfReaderAdapter();

	@Test
	void readsPagesWithTheirNumbersAndSkipsBlankPages() {
		byte[] pdf = PdfFixtures.pdf("First page text here", "", "Third page text here");

		assertThat(reader.readPages("notes.pdf", pdf)).containsExactly(
				new Page(1, "First page text here"),
				new Page(3, "Third page text here"));
	}

	@Test
	void rejectsContentThatIsNotAPdf() {
		assertThatThrownBy(() -> reader.readPages("notes.txt", "hello".getBytes(UTF_8)))
				.isInstanceOf(NotAPdfException.class)
				.hasMessage("The file 'notes.txt' is not a PDF.");
	}

	@Test
	void rejectsAPdfWithoutText() {
		assertThatThrownBy(() -> reader.readPages("scan.pdf", PdfFixtures.pdf("", "")))
				.isInstanceOf(PdfWithoutTextException.class)
				.hasMessage("The PDF 'scan.pdf' has no extractable text. It may be a scanned document.");
	}

}
