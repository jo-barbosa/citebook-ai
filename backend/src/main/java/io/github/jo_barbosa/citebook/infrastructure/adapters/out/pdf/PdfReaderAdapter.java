package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import io.github.jo_barbosa.citebook.application.ports.out.document.PdfReaderPort;
import io.github.jo_barbosa.citebook.domain.exceptions.NotAPdfException;
import io.github.jo_barbosa.citebook.domain.exceptions.PdfWithoutTextException;
import io.github.jo_barbosa.citebook.domain.model.chunk.Page;

@Component
public class PdfReaderAdapter implements PdfReaderPort {

	@Override
	public List<Page> readPages(String fileName, byte[] content) {
		List<Document> documents;
		try {
			documents = new PagePdfDocumentReader(new ByteArrayResource(content)).get();
		}
		catch (RuntimeException e) {
			throw new NotAPdfException(fileName);
		}
		List<Page> pages = documents.stream()
				.map(document -> new Page(pageNumber(document), normalise(document.getText())))
				.filter(page -> !page.text().isEmpty())
				.toList();
		if (pages.isEmpty()) {
			throw new PdfWithoutTextException(fileName);
		}
		return pages;
	}

	private static int pageNumber(Document document) {
		return ((Number) document.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER)).intValue();
	}

	private static String normalise(String text) {
		return text.lines()
				.map(line -> line.replaceAll("[ \\t]+", " ").strip())
				.collect(Collectors.joining("\n"))
				.strip();
	}

}
