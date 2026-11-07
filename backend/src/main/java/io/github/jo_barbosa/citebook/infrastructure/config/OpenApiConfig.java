package io.github.jo_barbosa.citebook.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI citebookOpenApi() {
		return new OpenAPI().info(new Info()
				.title("citebook-ai API")
				.description("Ask questions about a course's PDFs and get answers that cite the source (file and page).")
				.version("v1"));
	}

}
