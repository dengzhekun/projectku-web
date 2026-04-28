package com.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class KnowledgeBaseResponses {

    public static class DocumentDetailResponse {
        private Long id;
        private String title;
        private String category;
        private String sourceType;
        private String status;
        private Integer version;
        private String storagePath;
        private String contentText;
        private String createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
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

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }

        public String getContentText() {
            return contentText;
        }

        public void setContentText(String contentText) {
            this.contentText = contentText;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    public static class ChunkResponse {
        private Long id;
        private Integer chunkIndex;
        private String content;
        private Integer charCount;
        private String status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getCharCount() {
            return charCount;
        }

        public void setCharCount(Integer charCount) {
            this.charCount = charCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class IndexRecordResponse {
        private Long id;
        private Integer version;
        private String embeddingProvider;
        private String vectorCollection;
        private Integer indexedChunkCount;
        private String status;
        private String errorMessage;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
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

        public Integer getIndexedChunkCount() {
            return indexedChunkCount;
        }

        public void setIndexedChunkCount(Integer indexedChunkCount) {
            this.indexedChunkCount = indexedChunkCount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class HitLogResponse {
        private Long id;
        private Long documentId;
        private Long chunkId;
        private String queryText;
        private String conversationId;
        private LocalDateTime hitTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public void setChunkId(Long chunkId) {
            this.chunkId = chunkId;
        }

        public String getQueryText() {
            return queryText;
        }

        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public LocalDateTime getHitTime() {
            return hitTime;
        }

        public void setHitTime(LocalDateTime hitTime) {
            this.hitTime = hitTime;
        }
    }

    public static class BatchIndexItemResponse {
        private Long documentId;
        private String title;
        private String status;
        private Integer chunkCount;
        private String action;
        private String result;
        private String error;

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(Integer chunkCount) {
            this.chunkCount = chunkCount;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    public static class MissLogResponse {
        private Long id;
        private String queryText;
        private String conversationId;
        private BigDecimal confidence;
        private String fallbackReason;
        private String status;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getQueryText() {
            return queryText;
        }

        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(BigDecimal confidence) {
            this.confidence = confidence;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public void setFallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class CustomerServiceLogResponse {
        private Long id;
        private String queryText;
        private String conversationId;
        private String route;
        private String sourceType;
        private String sourceId;
        private BigDecimal confidence;
        private String fallbackReason;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getQueryText() {
            return queryText;
        }

        public void setQueryText(String queryText) {
            this.queryText = queryText;
        }

        public String getConversationId() {
            return conversationId;
        }

        public void setConversationId(String conversationId) {
            this.conversationId = conversationId;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public BigDecimal getConfidence() {
            return confidence;
        }

        public void setConfidence(BigDecimal confidence) {
            this.confidence = confidence;
        }

        public String getFallbackReason() {
            return fallbackReason;
        }

        public void setFallbackReason(String fallbackReason) {
            this.fallbackReason = fallbackReason;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
}
