package com.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.service")
public class AiServiceProperties {

    private String baseUrl = "http://127.0.0.1:9000";
    private int timeoutSeconds = 120;
    private String embeddingProvider = "local_bge_m3";
    private String vectorCollection = "ecommerce_kb_v1";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public String getVectorCollection() {
        return vectorCollection;
    }

    public void setVectorCollection(String vectorCollection) {
        this.vectorCollection = vectorCollection;
    }
}
