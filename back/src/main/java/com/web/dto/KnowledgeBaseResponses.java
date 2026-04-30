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

    public static class SyncHealthResponse {
        private Integer totalDocuments;
        private Integer parsedDocuments;
        private Integer chunkedDocuments;
        private Integer indexedDocuments;
        private Integer failedDocuments;
        private Integer needsSyncDocuments;
        private Integer staleDocuments;
        private Integer missingChunkDocuments;
        private Integer latestFailedIndexDocuments;
        private java.util.List<SyncHealthItemResponse> items;

        public Integer getTotalDocuments() {
            return totalDocuments;
        }

        public void setTotalDocuments(Integer totalDocuments) {
            this.totalDocuments = totalDocuments;
        }

        public Integer getParsedDocuments() {
            return parsedDocuments;
        }

        public void setParsedDocuments(Integer parsedDocuments) {
            this.parsedDocuments = parsedDocuments;
        }

        public Integer getChunkedDocuments() {
            return chunkedDocuments;
        }

        public void setChunkedDocuments(Integer chunkedDocuments) {
            this.chunkedDocuments = chunkedDocuments;
        }

        public Integer getIndexedDocuments() {
            return indexedDocuments;
        }

        public void setIndexedDocuments(Integer indexedDocuments) {
            this.indexedDocuments = indexedDocuments;
        }

        public Integer getFailedDocuments() {
            return failedDocuments;
        }

        public void setFailedDocuments(Integer failedDocuments) {
            this.failedDocuments = failedDocuments;
        }

        public Integer getNeedsSyncDocuments() {
            return needsSyncDocuments;
        }

        public void setNeedsSyncDocuments(Integer needsSyncDocuments) {
            this.needsSyncDocuments = needsSyncDocuments;
        }

        public Integer getStaleDocuments() {
            return staleDocuments;
        }

        public void setStaleDocuments(Integer staleDocuments) {
            this.staleDocuments = staleDocuments;
        }

        public Integer getMissingChunkDocuments() {
            return missingChunkDocuments;
        }

        public void setMissingChunkDocuments(Integer missingChunkDocuments) {
            this.missingChunkDocuments = missingChunkDocuments;
        }

        public Integer getLatestFailedIndexDocuments() {
            return latestFailedIndexDocuments;
        }

        public void setLatestFailedIndexDocuments(Integer latestFailedIndexDocuments) {
            this.latestFailedIndexDocuments = latestFailedIndexDocuments;
        }

        public java.util.List<SyncHealthItemResponse> getItems() {
            return items;
        }

        public void setItems(java.util.List<SyncHealthItemResponse> items) {
            this.items = items;
        }
    }

    public static class SyncHealthItemResponse {
        private Long id;
        private String title;
        private String category;
        private String status;
        private Integer version;
        private Integer chunkCount;
        private String latestIndexStatus;
        private Integer latestIndexedChunkCount;
        private String latestIndexError;
        private Boolean needsSync;

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

        public Integer getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(Integer chunkCount) {
            this.chunkCount = chunkCount;
        }

        public String getLatestIndexStatus() {
            return latestIndexStatus;
        }

        public void setLatestIndexStatus(String latestIndexStatus) {
            this.latestIndexStatus = latestIndexStatus;
        }

        public Integer getLatestIndexedChunkCount() {
            return latestIndexedChunkCount;
        }

        public void setLatestIndexedChunkCount(Integer latestIndexedChunkCount) {
            this.latestIndexedChunkCount = latestIndexedChunkCount;
        }

        public String getLatestIndexError() {
            return latestIndexError;
        }

        public void setLatestIndexError(String latestIndexError) {
            this.latestIndexError = latestIndexError;
        }

        public Boolean getNeedsSync() {
            return needsSync;
        }

        public void setNeedsSync(Boolean needsSync) {
            this.needsSync = needsSync;
        }
    }
}
