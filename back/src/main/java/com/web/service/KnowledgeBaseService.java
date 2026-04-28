package com.web.service;

import com.web.dto.CustomerServiceHitLog;
import com.web.dto.KnowledgeBaseRequests;
import com.web.dto.KnowledgeBaseResponses;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface KnowledgeBaseService {
    KnowledgeBaseResponses.DocumentDetailResponse createManualDocument(
            KnowledgeBaseRequests.CreateDocumentRequest request,
            String createdBy);

    KnowledgeBaseResponses.DocumentDetailResponse uploadDocument(
            MultipartFile file,
            String title,
            String category,
            String createdBy);

    List<KnowledgeBaseResponses.DocumentDetailResponse> getDocuments(String category, String status, String keyword);

    KnowledgeBaseResponses.DocumentDetailResponse getDocument(Long id);

    KnowledgeBaseResponses.DocumentDetailResponse updateDocument(Long id, KnowledgeBaseRequests.UpdateDocumentRequest request);

    void deleteDocument(Long id);

    void chunkDocument(Long id);

    List<KnowledgeBaseResponses.ChunkResponse> getChunks(Long id);

    void indexDocument(Long id);

    List<KnowledgeBaseResponses.BatchIndexItemResponse> batchIndexDocuments(Boolean allowLarge, Integer limit);

    List<KnowledgeBaseResponses.IndexRecordResponse> getIndexRecords(Long id);

    void recordHitLogs(String queryText, String conversationId, List<CustomerServiceHitLog> hitLogs);

    List<KnowledgeBaseResponses.HitLogResponse> getHitLogs(Long id);

    void recordMissedQuestion(String queryText, String conversationId, BigDecimal confidence, String fallbackReason);

    List<KnowledgeBaseResponses.MissLogResponse> getMissLogs(String status, String keyword);

    void recordCustomerServiceLog(
            String queryText,
            String conversationId,
            String route,
            String sourceType,
            String sourceId,
            BigDecimal confidence,
            String fallbackReason);

    List<KnowledgeBaseResponses.CustomerServiceLogResponse> getCustomerServiceLogs(
            String route,
            String sourceType,
            String keyword);
}
