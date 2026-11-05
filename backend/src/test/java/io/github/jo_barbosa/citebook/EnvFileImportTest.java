package io.github.jo_barbosa.citebook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class EnvFileImportTest {

	@Test
	void applicationYamlImportsAnOptionalDotEnv() throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader()
				.load("application", new ClassPathResource("application.yaml"));

		assertThat(sources.getFirst().getProperty("spring.config.import"))
				.isEqualTo("optional:file:.env[.properties]");
	}

	@Test
	void dotEnvFileProvidesTheOpenRouterKey(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve(".env"), "OPENROUTER_API_KEY=from-env-file\n");

		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfig.class)
				.web(WebApplicationType.NONE)
				.run("--spring.config.name=none",
						"--spring.config.import=optional:file:" + dir + "/.env[.properties]")) {
			assertThat(context.getEnvironment().resolvePlaceholders("${OPENROUTER_API_KEY}"))
					.isEqualTo("from-env-file");
		}
	}

	@Configuration
	static class EmptyConfig {
	}

}
