package io.github.jo_barbosa.citebook.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;

@Configuration
@EnableConfigurationProperties(ChunkingProperties.class)
public class ChunkingConfig {

	@Bean
	PageChunker pageChunker(ChunkingProperties properties) {
		return new PageChunker(properties.chunkSize(), properties.chunkOverlap());
	}

}
