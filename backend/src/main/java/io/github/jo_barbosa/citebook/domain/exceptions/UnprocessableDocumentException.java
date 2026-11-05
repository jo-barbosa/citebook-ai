package io.github.jo_barbosa.citebook.domain.exceptions;

public abstract class UnprocessableDocumentException extends RuntimeException {

	protected UnprocessableDocumentException(String message) {
		super(message);
	}

}
