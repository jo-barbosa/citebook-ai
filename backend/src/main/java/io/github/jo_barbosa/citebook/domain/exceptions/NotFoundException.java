package io.github.jo_barbosa.citebook.domain.exceptions;

public abstract class NotFoundException extends RuntimeException {

	protected NotFoundException(String message) {
		super(message);
	}

}
