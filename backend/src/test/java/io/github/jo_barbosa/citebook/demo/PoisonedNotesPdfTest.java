package io.github.jo_barbosa.citebook.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.jo_barbosa.citebook.domain.model.chunk.Page;
import io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf.PdfReaderAdapter;

class PoisonedNotesPdfTest {

	@Test
	void theWhiteParagraphIsInTheExtractedText(@TempDir Path dir) throws Exception {
		Path pdf = dir.resolve("poisoned-notes.pdf");

		PoisonedNotesPdf.main(new String[] { pdf.toString() });

		List<Page> pages = new PdfReaderAdapter().readPages("poisoned-notes.pdf", Files.readAllBytes(pdf));
		assertThat(pages).singleElement()
				.extracting(Page::text)
				.asString()
				.contains("William Pugh in 1990")
				.contains("Alan Turing in 1936");
	}

}
