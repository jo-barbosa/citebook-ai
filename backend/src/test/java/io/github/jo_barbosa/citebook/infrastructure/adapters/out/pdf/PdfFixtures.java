package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public final class PdfFixtures {

	private PdfFixtures() {
	}

	public static byte[] pdf(String... pageTexts) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String text : pageTexts) {
				PDPage page = new PDPage();
				document.addPage(page);
				if (!text.isEmpty()) {
					try (PDPageContentStream content = new PDPageContentStream(document, page)) {
						content.beginText();
						content.setFont(font, 12);
						content.newLineAtOffset(50, 700);
						content.showText(text);
						content.endText();
					}
				}
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
