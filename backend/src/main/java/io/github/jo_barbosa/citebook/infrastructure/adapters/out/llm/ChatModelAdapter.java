package io.github.jo_barbosa.citebook.infrastructure.adapters.out.llm;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.out.chat.ChatPort;

@Component
public class ChatModelAdapter implements ChatPort {

	private final ChatModel chatModel;

	private final OpenAiChatOptions options;

	public ChatModelAdapter(ChatModel chatModel, @Value("${spring.ai.openai.chat.timeout}") Duration timeout) {
		this.chatModel = chatModel;
		// Spring AI 2.0.1 takes the per-request timeout from the prompt options and does not copy
		// spring.ai.openai.chat.timeout into them; prompt options also replace the defaults, so start from the
		// model's defaults (model, temperature, max tokens) and set only the timeout.
		this.options = ((OpenAiChatOptions) chatModel.getDefaultOptions()).mutate().timeout(timeout).build();
	}

	@Override
	public String complete(String systemMessage, String userMessage) {
		Prompt prompt = new Prompt(List.of(new SystemMessage(systemMessage), new UserMessage(userMessage)),
				options);
		try {
			// No generation (an empty "choices" list) or no text both count as an empty answer.
			Generation result = chatModel.call(prompt).getResult();
			String text = result == null ? null : result.getOutput().getText();
			return Objects.requireNonNullElse(text, "");
		}
		// The openai-java SDK has already retried these (spring.ai.openai.chat.max-retries). Other OpenAIExceptions
		// (400, 401, 403, 404) are configuration errors, so they are not reported as "try again later".
		catch (RateLimitException | InternalServerException | OpenAIIoException e) {
			throw new ExternalServiceUnavailableException("chat model", e);
		}
	}

}
