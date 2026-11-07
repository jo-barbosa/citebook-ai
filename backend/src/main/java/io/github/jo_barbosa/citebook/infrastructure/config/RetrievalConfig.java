package io.github.jo_barbosa.citebook.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.jo_barbosa.citebook.application.services.question.SearchSettings;

@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfig {

	@Bean
	SearchSettings searchSettings(RetrievalProperties properties) {
		return new SearchSettings(properties.k(), properties.maxK(), properties.scoreThreshold());
	}

}
