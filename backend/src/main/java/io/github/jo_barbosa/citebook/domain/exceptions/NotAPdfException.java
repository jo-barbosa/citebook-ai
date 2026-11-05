package io.github.jo_barbosa.citebook.domain.exceptions;

public class NotAPdfException extends UnprocessableDocumentException {

	public NotAPdfException(String fileName) {
		super("The file '" + fileName + "' is not a PDF.");
	}

}
