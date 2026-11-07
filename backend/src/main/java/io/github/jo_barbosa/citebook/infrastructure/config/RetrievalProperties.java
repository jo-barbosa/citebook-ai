package io.github.jo_barbosa.citebook.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "citebook")
public record RetrievalProperties(int k, int maxK, double scoreThreshold) {
}
