package io.github.jo_barbosa.citebook.domain.exceptions;

public class PdfWithoutTextException extends UnprocessableDocumentException {

	public PdfWithoutTextException(String fileName) {
		super("The PDF '" + fileName + "' has no extractable text. It may be a scanned document.");
	}

}
