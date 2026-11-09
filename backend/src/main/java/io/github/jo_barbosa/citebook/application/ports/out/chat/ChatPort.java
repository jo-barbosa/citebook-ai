package io.github.jo_barbosa.citebook.application.ports.out.chat;

/** The chat model, seen from the application: one system message, one user message, the answer text. */
public interface ChatPort {

	/**
	 * @return the model's answer; empty if the model returned no text
	 * @throws io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException if the model
	 * is rate limited, failing or too slow, after the configured retries
	 */
	String complete(String systemMessage, String userMessage);

}
