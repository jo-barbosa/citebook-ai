package io.github.jo_barbosa.citebook.application.exceptions;

public class ExternalServiceUnavailableException extends RuntimeException {

	public ExternalServiceUnavailableException(String service, Throwable cause) {
		super("The " + service + " is unavailable. Try again in a few minutes.", cause);
	}

}
