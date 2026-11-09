package io.github.jo_barbosa.citebook.infrastructure.adapters.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.jo_barbosa.citebook.TestcontainersConfiguration;
import io.github.jo_barbosa.citebook.application.ports.out.chat.ChatPort;

/**
 * One real call to OpenRouter with the configured base URL and model, to check what the stub test cannot: that
 * OpenRouter accepts them. Needs OPENROUTER_API_KEY (environment or backend/.env); skipped without it. Run with
 * {@code ./mvnw verify -Dgroups=live -DexcludedGroups=}.
 */
@Tag("live")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.ai.openai.api-key=${OPENROUTER_API_KEY:missing}")
class OpenRouterLiveTest {

	@Value("${OPENROUTER_API_KEY:}")
	private String apiKey;

	@Autowired
	private ChatPort chat;

	@Test
	void openRouterAnswersWithTheConfiguredModel() {
		assumeThat(apiKey).as("OPENROUTER_API_KEY").isNotBlank();

		String answer = chat.complete("Answer with one word.", "What is the capital of France?");

		assertThat(answer).containsIgnoringCase("Paris");
	}

}
