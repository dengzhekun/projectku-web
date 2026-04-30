package com.web.service.impl;

import com.web.dto.CustomerServiceHitLog;
import com.web.dto.KnowledgeBaseRequests;
import com.web.dto.KnowledgeBaseResponses;
import com.web.exception.BusinessException;
import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.CustomerServiceLog;
import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import com.web.pojo.KbHitLog;
import com.web.pojo.KbIndexRecord;
import com.web.pojo.KbMissLog;
import com.web.service.AiKnowledgeBaseClient;
import com.web.service.KnowledgeBaseService;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private static final String STATUS_PARSED = "parsed";
    private static final String STATUS_CHUNKED = "chunked";
    private static final String STATUS_INDEXED = "indexed";
    private static final String STATUS_FAILED = "failed";
    private static final String CHUNK_STATUS_ACTIVE = "active";
    private static final int DEFAULT_BATCH_INDEX_MAX_CHUNK_COUNT = 50;
    private static final int MAX_UPLOAD_SIZE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CHUNK_LENGTH = 200;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiKnowledgeBaseClient aiKnowledgeBaseClient;
    private final Path storageRoot;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseMapper knowledgeBaseMapper,
            AiKnowledgeBaseClient aiKnowledgeBaseClient,
            @Value("${kb.storage.root-path:./storage/kb}") String storageRootPath) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.aiKnowledgeBaseClient = aiKnowledgeBaseClient;
        this.storageRoot = Path.of(storageRootPath).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public KnowledgeBaseResponses.DocumentDetailResponse createManualDocument(
            KnowledgeBaseRequests.CreateDocumentRequest request,
            String createdBy) {
        validateManualDocumentRequest(request);
        KbDocument document = newParsedDocument(
                request.getTitle(),
                request.getCategory(),
                "manual",
                request.getContentText(),
                createdBy);
        knowledgeBaseMapper.insertDocument(document);
        return toDocumentDetailResponse(document);
    }

    @Override
    @Transactional
    public KnowledgeBaseResponses.DocumentDetailResponse uploadDocument(
            MultipartFile file,
            String title,
            String category,
            String createdBy) {
        validateUploadRequest(file, title, category);
        String contentText = normalizeRequiredContent(extractText(file));
        KbDocument document = newParsedDocument(title, category, "upload", contentText, createdBy);
        knowledgeBaseMapper.insertDocument(document);
        document.setStoragePath(storeUploadedFile(document.getId(), file).toString());
        knowledgeBaseMapper.updateDocument(document);
        return toDocumentDetailResponse(document);
    }

    @Override
    public List<KnowledgeBaseResponses.DocumentDetailResponse> getDocuments(String category, String status, String keyword) {
        List<KbDocument> documents = knowledgeBaseMapper.getDocuments(
                normalizeOptional(category),
                normalizeOptional(status),
                normalizeOptional(keyword));
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
                .filter(Objects::nonNull)
                .map(this::toDocumentDetailResponse)
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgeBaseResponses.DocumentDetailResponse getDocument(Long id) {
        validateDocumentId(id);
        return toDocumentDetailResponse(getRequiredDocument(id));
    }

    @Override
    @Transactional
    public KnowledgeBaseResponses.DocumentDetailResponse updateDocument(Long id, KnowledgeBaseRequests.UpdateDocumentRequest request) {
        validateDocumentId(id);
        validateManualDocumentRequest(request);
        KbDocument existingDocument = getRequiredDocument(id);
        existingDocument.setTitle(request.getTitle().trim());
        existingDocument.setCategory(request.getCategory().trim());
        existingDocument.setContentText(normalizeRequiredContent(request.getContentText()));
        existingDocument.setStatus(STATUS_PARSED);
        existingDocument.setVersion((existingDocument.getVersion() == null ? 1 : existingDocument.getVersion()) + 1);
        knowledgeBaseMapper.deleteChunksByDocumentId(id);
        knowledgeBaseMapper.updateDocument(existingDocument);
        return toDocumentDetailResponse(existingDocument);
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        validateDocumentId(id);
        KbDocument document = getRequiredDocument(id);
        aiKnowledgeBaseClient.deleteDocument(id);
        knowledgeBaseMapper.deleteHitLogsByDocumentId(id);
        knowledgeBaseMapper.deleteIndexRecordsByDocumentId(id);
        knowledgeBaseMapper.deleteChunksByDocumentId(id);
        if (knowledgeBaseMapper.deleteDocument(id) == 0) {
            throw new BusinessException("NOT_FOUND", "Knowledge base document not found");
        }
        deleteStoredFile(document);
    }

    @Override
    @Transactional
    public void chunkDocument(Long id) {
        validateDocumentId(id);
        KbDocument document = getRequiredDocument(id);
        List<String> parts = splitContent(document.getContentText());
        if (parts.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "Document content is empty after normalization");
        }
        knowledgeBaseMapper.deleteChunksByDocumentId(id);
        for (int index = 0; index < parts.size(); index++) {
            String content = parts.get(index);
            KbChunk chunk = new KbChunk();
            chunk.setDocumentId(id);
            chunk.setChunkIndex(index);
            chunk.setContent(content);
            chunk.setCharCount(content.length());
            chunk.setStatus(CHUNK_STATUS_ACTIVE);
            knowledgeBaseMapper.insertChunk(chunk);
        }
        document.setStatus(STATUS_CHUNKED);
        knowledgeBaseMapper.updateDocument(document);
    }

    @Override
    public List<KnowledgeBaseResponses.ChunkResponse> getChunks(Long id) {
        validateDocumentId(id);
        getRequiredDocument(id);
        List<KbChunk> chunks = knowledgeBaseMapper.getChunksByDocumentId(id);
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        return chunks.stream()
                .map(this::toChunkResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void indexDocument(Long id) {
        indexDocument(id, null);
    }

    @Override
    @Transactional
    public void indexDocument(Long id, Boolean recoverMapping) {
        validateDocumentId(id);
        KbDocument document = getRequiredDocument(id);
        List<KbChunk> chunks = knowledgeBaseMapper.getChunksByDocumentId(id);
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "Please generate chunks before indexing");
        }
        indexDocumentWithChunks(document, chunks, shouldRecoverMapping(document, Boolean.TRUE.equals(recoverMapping)));
    }

    @Override
    @Transactional
    public List<KnowledgeBaseResponses.BatchIndexItemResponse> batchIndexDocuments(
            Boolean allowLarge,
            Integer limit,
            Boolean includeIndexed,
            Boolean recoverMapping) {
        Integer normalizedLimit = normalizeOptionalLimit(limit);
        boolean allowLargeDocument = Boolean.TRUE.equals(allowLarge);
        List<KbDocument> targetDocuments = new ArrayList<>();
        List<KbDocument> chunkedDocuments = knowledgeBaseMapper.getDocuments(null, STATUS_CHUNKED, null);
        if (chunkedDocuments != null) {
            targetDocuments.addAll(chunkedDocuments);
        }
        if (Boolean.TRUE.equals(includeIndexed)) {
            List<KbDocument> indexedDocuments = knowledgeBaseMapper.getDocuments(null, STATUS_INDEXED, null);
            if (indexedDocuments != null) {
                for (KbDocument indexedDocument : indexedDocuments) {
                    if (indexedDocument == null || indexedDocument.getId() == null) {
                        continue;
                    }
                    boolean exists = targetDocuments.stream()
                            .anyMatch(item -> item != null && indexedDocument.getId().equals(item.getId()));
                    if (!exists) {
                        targetDocuments.add(indexedDocument);
                    }
                }
            }
        }
        if (targetDocuments.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBaseResponses.BatchIndexItemResponse> responses = new ArrayList<>();
        int processed = 0;
        for (KbDocument document : targetDocuments) {
            if (document == null) {
                continue;
            }
            if (normalizedLimit != null && processed >= normalizedLimit) {
                break;
            }
            processed++;

            List<KbChunk> chunks = knowledgeBaseMapper.getChunksByDocumentId(document.getId());
            int chunkCount = chunks == null ? 0 : chunks.size();
            if (chunkCount == 0) {
                responses.add(toBatchIndexItemResponse(
                        document.getId(),
                        document.getTitle(),
                        document.getStatus(),
                        0,
                        "skip",
                        "skipped",
                        "No chunks found"));
                continue;
            }
            if (!allowLargeDocument && chunkCount > DEFAULT_BATCH_INDEX_MAX_CHUNK_COUNT) {
                responses.add(toBatchIndexItemResponse(
                        document.getId(),
                        document.getTitle(),
                        document.getStatus(),
                        chunkCount,
                        "skip",
                        "skipped",
                        "Chunk count exceeds threshold " + DEFAULT_BATCH_INDEX_MAX_CHUNK_COUNT));
                continue;
            }

            try {
                boolean recoverCurrentDocument = shouldRecoverMapping(document, Boolean.TRUE.equals(recoverMapping));
                indexDocumentWithChunks(document, chunks, recoverCurrentDocument);
                responses.add(toBatchIndexItemResponse(
                        document.getId(),
                        document.getTitle(),
                        STATUS_INDEXED,
                        chunkCount,
                        "index",
                        "success",
                        null));
            } catch (BusinessException e) {
                responses.add(toBatchIndexItemResponse(
                        document.getId(),
                        document.getTitle(),
                        STATUS_FAILED,
                        chunkCount,
                        "index",
                        "failed",
                        e.getMessage()));
            }
        }
        return responses;
    }

    private void indexDocumentWithChunks(KbDocument document, List<KbChunk> chunks, boolean recoverMapping) {
        try {
            AiKnowledgeBaseClient.IndexResult result = aiKnowledgeBaseClient.indexDocumentChunks(document, chunks, recoverMapping);
            KbIndexRecord record = new KbIndexRecord();
            record.setDocumentId(document.getId());
            record.setVersion(document.getVersion());
            record.setEmbeddingProvider(result.getEmbeddingProvider());
            record.setVectorCollection(result.getVectorCollection());
            record.setIndexedChunkCount(result.getIndexedChunkCount());
            record.setStatus("success");
            knowledgeBaseMapper.insertIndexRecord(record);

            document.setStatus(STATUS_INDEXED);
            knowledgeBaseMapper.updateDocument(document);
        } catch (BusinessException e) {
            persistFailedIndexRecord(document, chunks.size(), e.getMessage());
            throw new BusinessException("KB_INDEX_FAILED", e.getMessage());
        } catch (Exception e) {
            persistFailedIndexRecord(document, chunks.size(), e.getMessage());
            throw new BusinessException("KB_INDEX_FAILED", "Indexing failed");
        }
    }

    private boolean shouldRecoverMapping(KbDocument document, boolean requestedRecoverMapping) {
        if (requestedRecoverMapping) {
            return true;
        }
        if (document == null) {
            return false;
        }
        if (STATUS_INDEXED.equals(document.getStatus())) {
            return true;
        }
        Integer version = document.getVersion();
        return version != null && version > 1;
    }

    @Override
    public List<KnowledgeBaseResponses.IndexRecordResponse> getIndexRecords(Long id) {
        validateDocumentId(id);
        getRequiredDocument(id);
        List<KbIndexRecord> records = knowledgeBaseMapper.getIndexRecordsByDocumentId(id);
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        return records.stream()
                .map(this::toIndexRecordResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordHitLogs(String queryText, String conversationId, List<CustomerServiceHitLog> hitLogs) {
        if (normalizeOptional(queryText) == null || hitLogs == null || hitLogs.isEmpty()) {
            return;
        }
        for (CustomerServiceHitLog hitLog : hitLogs) {
            if (hitLog == null || hitLog.getDocumentId() == null || hitLog.getChunkId() == null) {
                continue;
            }
            KbHitLog entity = new KbHitLog();
            entity.setDocumentId(hitLog.getDocumentId());
            entity.setChunkId(hitLog.getChunkId());
            entity.setQueryText(queryText.trim());
            entity.setConversationId(normalizeOptional(conversationId));
            entity.setHitTime(LocalDateTime.now());
            knowledgeBaseMapper.insertHitLog(entity);
        }
    }

    @Override
    public List<KnowledgeBaseResponses.HitLogResponse> getHitLogs(Long id) {
        validateDocumentId(id);
        getRequiredDocument(id);
        List<KbHitLog> hitLogs = knowledgeBaseMapper.getHitLogsByDocumentId(id);
        if (hitLogs == null || hitLogs.isEmpty()) {
            return Collections.emptyList();
        }
        return hitLogs.stream()
                .map(this::toHitLogResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordMissedQuestion(String queryText, String conversationId, BigDecimal confidence, String fallbackReason) {
        String normalizedQuery = normalizeOptional(queryText);
        if (normalizedQuery == null) {
            return;
        }
        KbMissLog missLog = new KbMissLog();
        missLog.setQueryText(truncate(normalizedQuery, 1000));
        missLog.setConversationId(truncate(normalizeOptional(conversationId), 128));
        missLog.setConfidence(confidence);
        missLog.setFallbackReason(truncate(normalizeOptional(fallbackReason), 1000));
        missLog.setStatus("open");
        knowledgeBaseMapper.insertMissLog(missLog);
    }

    @Override
    public List<KnowledgeBaseResponses.MissLogResponse> getMissLogs(String status, String keyword) {
        List<KbMissLog> missLogs = knowledgeBaseMapper.getMissLogs(normalizeOptional(status), normalizeOptional(keyword));
        if (missLogs == null || missLogs.isEmpty()) {
            return Collections.emptyList();
        }
        return missLogs.stream()
                .filter(Objects::nonNull)
                .map(this::toMissLogResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordCustomerServiceLog(
            String queryText,
            String conversationId,
            String route,
            String sourceType,
            String sourceId,
            BigDecimal confidence,
            String fallbackReason) {
        String normalizedQuery = normalizeOptional(queryText);
        if (normalizedQuery == null || normalizeOptional(route) == null || normalizeOptional(sourceType) == null) {
            return;
        }
        CustomerServiceLog log = new CustomerServiceLog();
        log.setQueryText(truncate(normalizedQuery, 1000));
        log.setConversationId(truncate(normalizeOptional(conversationId), 128));
        log.setRoute(truncate(normalizeOptional(route), 64));
        log.setSourceType(truncate(normalizeOptional(sourceType), 64));
        log.setSourceId(truncate(normalizeOptional(sourceId), 128));
        log.setConfidence(confidence);
        log.setFallbackReason(truncate(normalizeOptional(fallbackReason), 1000));
        knowledgeBaseMapper.insertCustomerServiceLog(log);
    }

    @Override
    public List<KnowledgeBaseResponses.CustomerServiceLogResponse> getCustomerServiceLogs(
            String route,
            String sourceType,
            String keyword) {
        List<CustomerServiceLog> logs = knowledgeBaseMapper.getCustomerServiceLogs(
                normalizeOptional(route),
                normalizeOptional(sourceType),
                normalizeOptional(keyword));
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream()
                .filter(Objects::nonNull)
                .map(this::toCustomerServiceLogResponse)
                .collect(Collectors.toList());
    }

    @Override
    public KnowledgeBaseResponses.SyncHealthResponse getSyncHealth() {
        List<KbDocument> documents = knowledgeBaseMapper.getDocuments(null, null, null);
        if (documents == null || documents.isEmpty()) {
            KnowledgeBaseResponses.SyncHealthResponse empty = new KnowledgeBaseResponses.SyncHealthResponse();
            empty.setTotalDocuments(0);
            empty.setParsedDocuments(0);
            empty.setChunkedDocuments(0);
            empty.setIndexedDocuments(0);
            empty.setFailedDocuments(0);
            empty.setNeedsSyncDocuments(0);
            empty.setStaleDocuments(0);
            empty.setMissingChunkDocuments(0);
            empty.setLatestFailedIndexDocuments(0);
            empty.setItems(Collections.emptyList());
            return empty;
        }

        int parsedDocuments = 0;
        int chunkedDocuments = 0;
        int indexedDocuments = 0;
        int failedDocuments = 0;
        int needsSyncDocuments = 0;
        int staleDocuments = 0;
        int missingChunkDocuments = 0;
        int latestFailedIndexDocuments = 0;
        List<KnowledgeBaseResponses.SyncHealthItemResponse> items = new ArrayList<>();

        for (KbDocument document : documents) {
            if (document == null || document.getId() == null) {
                continue;
            }
            String status = normalizeOptional(document.getStatus());
            if (STATUS_PARSED.equals(status)) {
                parsedDocuments++;
            } else if (STATUS_CHUNKED.equals(status)) {
                chunkedDocuments++;
            } else if (STATUS_INDEXED.equals(status)) {
                indexedDocuments++;
            } else if (STATUS_FAILED.equals(status)) {
                failedDocuments++;
            }

            List<KbChunk> chunks = knowledgeBaseMapper.getChunksByDocumentId(document.getId());
            int chunkCount = chunks == null ? 0 : chunks.size();
            List<KbIndexRecord> indexRecords = knowledgeBaseMapper.getIndexRecordsByDocumentId(document.getId());
            KbIndexRecord latestIndexRecord = (indexRecords == null || indexRecords.isEmpty()) ? null : indexRecords.get(0);
            KbIndexRecord latestSuccessRecord = findLatestSuccessRecord(indexRecords);

            boolean latestIsFailed = latestIndexRecord != null && STATUS_FAILED.equals(normalizeOptional(latestIndexRecord.getStatus()));
            if (latestIsFailed) {
                latestFailedIndexDocuments++;
            }

            boolean missingChunks = chunkCount <= 0;
            if (missingChunks) {
                missingChunkDocuments++;
            }

            boolean stale = latestSuccessRecord != null
                    && !Objects.equals(latestSuccessRecord.getIndexedChunkCount(), chunkCount);
            if (stale) {
                staleDocuments++;
            }

            boolean needsSync = STATUS_PARSED.equals(status)
                    || STATUS_CHUNKED.equals(status)
                    || STATUS_FAILED.equals(status)
                    || missingChunks
                    || latestSuccessRecord == null
                    || stale;

            if (needsSync) {
                needsSyncDocuments++;
            }

            KnowledgeBaseResponses.SyncHealthItemResponse item = new KnowledgeBaseResponses.SyncHealthItemResponse();
            item.setId(document.getId());
            item.setTitle(document.getTitle());
            item.setCategory(document.getCategory());
            item.setStatus(document.getStatus());
            item.setVersion(document.getVersion());
            item.setChunkCount(chunkCount);
            item.setLatestIndexStatus(latestIndexRecord == null ? null : latestIndexRecord.getStatus());
            item.setLatestIndexedChunkCount(latestIndexRecord == null ? null : latestIndexRecord.getIndexedChunkCount());
            item.setLatestIndexError(latestIndexRecord == null ? null : latestIndexRecord.getErrorMessage());
            item.setNeedsSync(needsSync);
            items.add(item);
        }

        KnowledgeBaseResponses.SyncHealthResponse response = new KnowledgeBaseResponses.SyncHealthResponse();
        response.setTotalDocuments(items.size());
        response.setParsedDocuments(parsedDocuments);
        response.setChunkedDocuments(chunkedDocuments);
        response.setIndexedDocuments(indexedDocuments);
        response.setFailedDocuments(failedDocuments);
        response.setNeedsSyncDocuments(needsSyncDocuments);
        response.setStaleDocuments(staleDocuments);
        response.setMissingChunkDocuments(missingChunkDocuments);
        response.setLatestFailedIndexDocuments(latestFailedIndexDocuments);
        response.setItems(items);
        return response;
    }

    private KbIndexRecord findLatestSuccessRecord(List<KbIndexRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        for (KbIndexRecord record : records) {
            if (record != null && "success".equals(normalizeOptional(record.getStatus()))) {
                return record;
            }
        }
        return null;
    }

    private void persistFailedIndexRecord(KbDocument document, int chunkCount, String errorMessage) {
        KbIndexRecord record = new KbIndexRecord();
        record.setDocumentId(document.getId());
        record.setVersion(document.getVersion());
        record.setEmbeddingProvider("unknown");
        record.setVectorCollection("unknown");
        record.setIndexedChunkCount(chunkCount);
        record.setStatus("failed");
        record.setErrorMessage(truncate(errorMessage, 1000));
        knowledgeBaseMapper.insertIndexRecord(record);

        document.setStatus(STATUS_FAILED);
        knowledgeBaseMapper.updateDocument(document);
    }

    private KbDocument newParsedDocument(String title, String category, String sourceType, String contentText, String createdBy) {
        KbDocument document = new KbDocument();
        document.setTitle(title.trim());
        document.setCategory(category.trim());
        document.setSourceType(sourceType);
        document.setStatus(STATUS_PARSED);
        document.setVersion(1);
        document.setContentText(normalizeRequiredContent(contentText));
        document.setCreatedBy(createdBy);
        return document;
    }

    private String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String suffix = getSuffix(filename);
        try {
            if ("txt".equals(suffix) || "md".equals(suffix)) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            if ("docx".equals(suffix)) {
                return extractDocxText(file);
            }
        } catch (IOException e) {
            throw new BusinessException("VALIDATION_FAILED", "Failed to parse uploaded file");
        }
        throw new BusinessException("VALIDATION_FAILED", "Only txt, md, and docx files are supported");
    }

    private String extractDocxText(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private Path storeUploadedFile(Long documentId, MultipartFile file) {
        String filename = sanitizeFilename(file.getOriginalFilename());
        Path documentDir = storageRoot.resolve(String.valueOf(documentId)).normalize();
        ensureInsideStorageRoot(documentDir);
        try {
            Files.createDirectories(documentDir);
            Path targetPath = documentDir.resolve(filename).normalize();
            ensureInsideStorageRoot(targetPath);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetPath;
        } catch (IOException e) {
            throw new BusinessException("VALIDATION_FAILED", "Failed to store uploaded file");
        }
    }

    private void deleteStoredFile(KbDocument document) {
        Path documentDir = storageRoot.resolve(String.valueOf(document.getId())).normalize();
        if (!documentDir.startsWith(storageRoot) || !Files.exists(documentDir)) {
            return;
        }
        try {
            List<Path> paths = Files.walk(documentDir)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new BusinessException("VALIDATION_FAILED", "Failed to delete stored file");
        }
    }

    private void ensureInsideStorageRoot(Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(storageRoot)) {
            throw new BusinessException("VALIDATION_FAILED", "Invalid storage path");
        }
    }

    private String sanitizeFilename(String filename) {
        String normalized = normalizeOptional(filename);
        if (normalized == null) {
            throw new BusinessException("VALIDATION_FAILED", "Uploaded filename is required");
        }
        return normalized.replace("\\", "_").replace("/", "_");
    }

    private String getSuffix(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private KbDocument getRequiredDocument(Long id) {
        KbDocument document = knowledgeBaseMapper.getDocumentById(id);
        if (document == null) {
            throw new BusinessException("NOT_FOUND", "Knowledge base document not found");
        }
        return document;
    }

    private void validateManualDocumentRequest(KnowledgeBaseRequests.CreateDocumentRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_FAILED", "Request body is required");
        }
        validateTextField(request.getTitle(), "Title is required");
        validateTextField(request.getCategory(), "Category is required");
        validateTextField(request.getContentText(), "Content is required");
    }

    private void validateUploadRequest(MultipartFile file, String title, String category) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("VALIDATION_FAILED", "Uploaded file is required");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new BusinessException("VALIDATION_FAILED", "Uploaded file must be 2MB or smaller");
        }
        validateTextField(title, "Title is required");
        validateTextField(category, "Category is required");
        if (normalizeOptional(file.getOriginalFilename()) == null) {
            throw new BusinessException("VALIDATION_FAILED", "Uploaded filename is required");
        }
    }

    private void validateDocumentId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Document id is invalid");
        }
    }

    private void validateTextField(String value, String message) {
        if (normalizeOptional(value) == null) {
            throw new BusinessException("VALIDATION_FAILED", message);
        }
    }

    private String normalizeRequiredContent(String contentText) {
        String normalized = normalizeOptional(contentText);
        if (normalized == null) {
            throw new BusinessException("VALIDATION_FAILED", "Document content is empty");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Integer normalizeOptionalLimit(Integer limit) {
        if (limit == null) {
            return null;
        }
        if (limit <= 0) {
            throw new BusinessException("VALIDATION_FAILED", "Limit must be greater than 0");
        }
        return limit;
    }

    private List<String> splitContent(String contentText) {
        String normalized = contentText == null ? "" : contentText.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                flushBlock(blocks, current);
                continue;
            }
            if (isHeading(trimmed) && current.length() > 0) {
                flushBlock(blocks, current);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(trimmed);
        }
        flushBlock(blocks, current);

        List<String> chunks = new ArrayList<>();
        for (String block : blocks) {
            if (block.length() <= MAX_CHUNK_LENGTH) {
                chunks.add(block);
                continue;
            }
            for (int start = 0; start < block.length(); start += MAX_CHUNK_LENGTH) {
                chunks.add(block.substring(start, Math.min(start + MAX_CHUNK_LENGTH, block.length())));
            }
        }
        return chunks;
    }

    private boolean isHeading(String line) {
        return line.startsWith("#")
                || line.matches("^[0-9]+[.、].*")
                || line.matches("^(第[一二三四五六七八九十百千0-9]+[章节部分篇]).*");
    }

    private void flushBlock(List<String> blocks, StringBuilder current) {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            blocks.add(text);
        }
        current.setLength(0);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private KnowledgeBaseResponses.DocumentDetailResponse toDocumentDetailResponse(KbDocument document) {
        KnowledgeBaseResponses.DocumentDetailResponse response = new KnowledgeBaseResponses.DocumentDetailResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setCategory(document.getCategory());
        response.setSourceType(document.getSourceType());
        response.setStatus(document.getStatus());
        response.setVersion(document.getVersion());
        response.setStoragePath(document.getStoragePath());
        response.setContentText(document.getContentText());
        response.setCreatedBy(document.getCreatedBy());
        response.setCreatedAt(document.getCreatedAt());
        response.setUpdatedAt(document.getUpdatedAt());
        return response;
    }

    private KnowledgeBaseResponses.ChunkResponse toChunkResponse(KbChunk chunk) {
        KnowledgeBaseResponses.ChunkResponse response = new KnowledgeBaseResponses.ChunkResponse();
        response.setId(chunk.getId());
        response.setChunkIndex(chunk.getChunkIndex());
        response.setContent(chunk.getContent());
        response.setCharCount(chunk.getCharCount());
        response.setStatus(chunk.getStatus());
        return response;
    }

    private KnowledgeBaseResponses.IndexRecordResponse toIndexRecordResponse(KbIndexRecord record) {
        KnowledgeBaseResponses.IndexRecordResponse response = new KnowledgeBaseResponses.IndexRecordResponse();
        response.setId(record.getId());
        response.setVersion(record.getVersion());
        response.setEmbeddingProvider(record.getEmbeddingProvider());
        response.setVectorCollection(record.getVectorCollection());
        response.setIndexedChunkCount(record.getIndexedChunkCount());
        response.setStatus(record.getStatus());
        response.setErrorMessage(record.getErrorMessage());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    private KnowledgeBaseResponses.HitLogResponse toHitLogResponse(KbHitLog hitLog) {
        KnowledgeBaseResponses.HitLogResponse response = new KnowledgeBaseResponses.HitLogResponse();
        response.setId(hitLog.getId());
        response.setDocumentId(hitLog.getDocumentId());
        response.setChunkId(hitLog.getChunkId());
        response.setQueryText(hitLog.getQueryText());
        response.setConversationId(hitLog.getConversationId());
        response.setHitTime(hitLog.getHitTime());
        return response;
    }

    private KnowledgeBaseResponses.BatchIndexItemResponse toBatchIndexItemResponse(
            Long documentId,
            String title,
            String status,
            Integer chunkCount,
            String action,
            String result,
            String error) {
        KnowledgeBaseResponses.BatchIndexItemResponse response = new KnowledgeBaseResponses.BatchIndexItemResponse();
        response.setDocumentId(documentId);
        response.setTitle(title);
        response.setStatus(status);
        response.setChunkCount(chunkCount);
        response.setAction(action);
        response.setResult(result);
        response.setError(error);
        return response;
    }

    private KnowledgeBaseResponses.MissLogResponse toMissLogResponse(KbMissLog missLog) {
        KnowledgeBaseResponses.MissLogResponse response = new KnowledgeBaseResponses.MissLogResponse();
        response.setId(missLog.getId());
        response.setQueryText(missLog.getQueryText());
        response.setConversationId(missLog.getConversationId());
        response.setConfidence(missLog.getConfidence());
        response.setFallbackReason(missLog.getFallbackReason());
        response.setStatus(missLog.getStatus());
        response.setCreatedAt(missLog.getCreatedAt());
        return response;
    }

    private KnowledgeBaseResponses.CustomerServiceLogResponse toCustomerServiceLogResponse(CustomerServiceLog log) {
        KnowledgeBaseResponses.CustomerServiceLogResponse response = new KnowledgeBaseResponses.CustomerServiceLogResponse();
        response.setId(log.getId());
        response.setQueryText(log.getQueryText());
        response.setConversationId(log.getConversationId());
        response.setRoute(log.getRoute());
        response.setSourceType(log.getSourceType());
        response.setSourceId(log.getSourceId());
        response.setConfidence(log.getConfidence());
        response.setFallbackReason(log.getFallbackReason());
        response.setCreatedAt(log.getCreatedAt());
        return response;
    }
}
