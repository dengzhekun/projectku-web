package com.web.service.impl;

import com.web.config.AiServiceProperties;
import com.web.exception.BusinessException;
import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import com.web.service.AiKnowledgeBaseClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AiKnowledgeBaseClientImpl implements AiKnowledgeBaseClient {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;

    public AiKnowledgeBaseClientImpl(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = Math.max(properties.getTimeoutSeconds(), 1) * 1000;
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restTemplate = new RestTemplate(requestFactory);
        this.properties = properties;
    }

    @Override
    public IndexResult indexDocumentChunks(KbDocument document, List<KbChunk> chunks, boolean recoverMapping) {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/internal/index";
        IndexRequest request = new IndexRequest();
        request.setDocumentId(document.getId());
        request.setVersion(document.getVersion());
        request.setTitle(document.getTitle());
        request.setCategory(document.getCategory());
        request.setRecoverMapping(recoverMapping);
        request.setChunks(chunks.stream()
                .map(chunk -> new IndexChunk(chunk.getId(), chunk.getChunkIndex(), chunk.getContent(), chunk.getCharCount()))
                .collect(Collectors.toList()));
        try {
            IndexResponse response = restTemplate.postForObject(url, request, IndexResponse.class);
            if (response == null) {
                throw new BusinessException("AI_SERVICE_UNAVAILABLE", "Indexing service returned empty response");
            }
            return new IndexResult(
                    coalesce(response.getEmbeddingProvider(), properties.getEmbeddingProvider()),
                    coalesce(response.getVectorCollection(), properties.getVectorCollection()),
                    response.getIndexedChunkCount() == null ? chunks.size() : response.getIndexedChunkCount());
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "Failed to call indexing service");
        } catch (Exception e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "Invalid indexing service response");
        }
    }

    @Override
    public void deleteDocument(Long documentId) {
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/internal/delete";
        DeleteRequest request = new DeleteRequest();
        request.setDocumentId(documentId);
        try {
            restTemplate.postForEntity(url, request, Void.class);
        } catch (RestClientException e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "Failed to call delete service");
        } catch (Exception e) {
            throw new BusinessException("AI_SERVICE_UNAVAILABLE", "Invalid delete service response");
        }
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class IndexRequest {
        private Long documentId;
        private Integer version;
        private String title;
        private String category;
        private Boolean recoverMapping;
        private List<IndexChunk> chunks;

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Boolean getRecoverMapping() {
            return recoverMapping;
        }

        public void setRecoverMapping(Boolean recoverMapping) {
            this.recoverMapping = recoverMapping;
        }

        public List<IndexChunk> getChunks() {
            return chunks;
        }

        public void setChunks(List<IndexChunk> chunks) {
            this.chunks = chunks;
        }
    }

    private static class IndexChunk {
        private Long chunkId;
        private Integer chunkIndex;
        private String content;
        private Integer charCount;

        public IndexChunk(Long chunkId, Integer chunkIndex, String content, Integer charCount) {
            this.chunkId = chunkId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.charCount = charCount;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public Integer getCharCount() {
            return charCount;
        }
    }

    private static class IndexResponse {
        private Long documentId;
        private Integer indexedChunkCount;
        private String embeddingProvider;
        private String vectorCollection;

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public Integer getIndexedChunkCount() {
            return indexedChunkCount;
        }

        public void setIndexedChunkCount(Integer indexedChunkCount) {
            this.indexedChunkCount = indexedChunkCount;
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

    private static class DeleteRequest {
        private Long documentId;

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
    }
}
