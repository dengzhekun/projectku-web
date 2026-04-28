package com.web.service.impl;

import com.web.dto.KnowledgeBaseRequests;
import com.web.dto.KnowledgeBaseResponses;
import com.web.exception.BusinessException;
import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import com.web.pojo.KbIndexRecord;
import com.web.service.AiKnowledgeBaseClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private AiKnowledgeBaseClient aiKnowledgeBaseClient;

    @TempDir
    Path tempDir;

    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseServiceImpl(
                knowledgeBaseMapper,
                aiKnowledgeBaseClient,
                tempDir.toString());
    }

    @Test
    void createManualDocumentPersistsParsedDraftDocument() {
        KnowledgeBaseRequests.CreateDocumentRequest request =
                new KnowledgeBaseRequests.CreateDocumentRequest("Returns Policy", "policy", "Manual content");

        knowledgeBaseService.createManualDocument(request, "admin");

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).insertDocument(documentCaptor.capture());
        KbDocument document = documentCaptor.getValue();
        assertEquals("Returns Policy", document.getTitle());
        assertEquals("policy", document.getCategory());
        assertEquals("manual", document.getSourceType());
        assertEquals("parsed", document.getStatus());
        assertEquals(1, document.getVersion());
        assertEquals("Manual content", document.getContentText());
        assertEquals("admin", document.getCreatedBy());
    }

    @Test
    void getDocumentsReflectsInsertedDocuments() {
        Map<Long, KbDocument> documents = new LinkedHashMap<>();
        doAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            long id = documents.size() + 1L;
            document.setId(id);
            documents.put(id, cloneDocument(document));
            return 1;
        }).when(knowledgeBaseMapper).insertDocument(any(KbDocument.class));
        doAnswer(invocation -> new ArrayList<>(documents.values()))
                .when(knowledgeBaseMapper).getDocuments(null, null, null);

        knowledgeBaseService.createManualDocument(
                new KnowledgeBaseRequests.CreateDocumentRequest("Returns Policy", "policy", "Seven-day returns"),
                "admin");

        List<KnowledgeBaseResponses.DocumentDetailResponse> responses =
                knowledgeBaseService.getDocuments(null, null, null);

        assertEquals(1, responses.size());
        assertEquals("Returns Policy", responses.get(0).getTitle());
        assertEquals("policy", responses.get(0).getCategory());
        assertEquals("parsed", responses.get(0).getStatus());
        assertEquals(1, responses.get(0).getVersion());
        assertEquals("Seven-day returns", responses.get(0).getContentText());
    }

    @Test
    void uploadDocumentParsesTextFileStoresSourceFileAndPersistsPath() throws Exception {
        doAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(7L);
            return 1;
        }).when(knowledgeBaseMapper).insertDocument(any(KbDocument.class));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "faq.txt",
                "text/plain",
                "Uploaded content".getBytes(StandardCharsets.UTF_8));

        KnowledgeBaseResponses.DocumentDetailResponse response =
                knowledgeBaseService.uploadDocument(file, "FAQ", "support", "admin");

        Path storedFile = tempDir.resolve("7").resolve("faq.txt");
        assertTrue(Files.exists(storedFile));
        assertEquals("Uploaded content", Files.readString(storedFile, StandardCharsets.UTF_8));
        assertEquals("Uploaded content", response.getContentText());

        ArgumentCaptor<KbDocument> updateCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).updateDocument(updateCaptor.capture());
        assertEquals(storedFile.toString(), updateCaptor.getValue().getStoragePath());
    }

    @Test
    void uploadDocumentRejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "faq.pdf",
                "application/pdf",
                "Unsupported".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.uploadDocument(file, "FAQ", "support", "admin"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void uploadDocumentRejectsEmptyExtractedText() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "faq.txt",
                "text/plain",
                "   \n\t".getBytes(StandardCharsets.UTF_8));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.uploadDocument(file, "FAQ", "support", "admin"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void uploadDocumentRejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "faq.txt",
                "text/plain",
                new byte[2 * 1024 * 1024 + 1]);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.uploadDocument(file, "FAQ", "support", "admin"));

        assertEquals("VALIDATION_FAILED", exception.getCode());
    }

    @Test
    void chunkDocumentSplitsContentAndPersistsChunks() {
        KbDocument document = new KbDocument();
        document.setId(11L);
        document.setTitle("Returns");
        document.setCategory("policy");
        document.setVersion(2);
        document.setContentText("Section One\n\n" + "A".repeat(450));

        doReturn(document).when(knowledgeBaseMapper).getDocumentById(11L);

        knowledgeBaseService.chunkDocument(11L);

        verify(knowledgeBaseMapper).deleteChunksByDocumentId(11L);
        ArgumentCaptor<KbChunk> chunkCaptor = ArgumentCaptor.forClass(KbChunk.class);
        verify(knowledgeBaseMapper, times(4)).insertChunk(chunkCaptor.capture());
        List<KbChunk> savedChunks = chunkCaptor.getAllValues();
        assertEquals("Section One", savedChunks.get(0).getContent());
        assertEquals(200, savedChunks.get(1).getCharCount());
        assertEquals(200, savedChunks.get(2).getCharCount());
        assertEquals(50, savedChunks.get(3).getCharCount());

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).updateDocument(documentCaptor.capture());
        assertEquals("chunked", documentCaptor.getValue().getStatus());
    }

    @Test
    void indexDocumentPersistsSuccessRecordAndUpdatesDocumentStatus() {
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setTitle("Returns");
        document.setCategory("policy");
        document.setVersion(3);
        document.setStatus("chunked");
        document.setContentText("hello");

        List<KbChunk> chunks = List.of(
                new KbChunk(101L, 9L, 0, "chunk-1", 7, "active", null),
                new KbChunk(102L, 9L, 1, "chunk-2", 7, "active", null)
        );

        doReturn(document).when(knowledgeBaseMapper).getDocumentById(9L);
        doReturn(chunks).when(knowledgeBaseMapper).getChunksByDocumentId(9L);
        when(aiKnowledgeBaseClient.indexDocumentChunks(document, chunks))
                .thenReturn(new AiKnowledgeBaseClient.IndexResult("local_bge_m3", "ecommerce_kb_v1", 2));

        knowledgeBaseService.indexDocument(9L);

        verify(aiKnowledgeBaseClient).indexDocumentChunks(document, chunks);
        ArgumentCaptor<KbIndexRecord> recordCaptor = ArgumentCaptor.forClass(KbIndexRecord.class);
        verify(knowledgeBaseMapper).insertIndexRecord(recordCaptor.capture());
        assertEquals("success", recordCaptor.getValue().getStatus());
        assertEquals(2, recordCaptor.getValue().getIndexedChunkCount());
        assertEquals(3, recordCaptor.getValue().getVersion());

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).updateDocument(documentCaptor.capture());
        assertEquals("indexed", documentCaptor.getValue().getStatus());
    }

    @Test
    void indexDocumentPersistsFailureRecordWhenAiCallFails() {
        KbDocument document = new KbDocument();
        document.setId(9L);
        document.setTitle("Returns");
        document.setCategory("policy");
        document.setVersion(3);
        document.setStatus("chunked");
        document.setContentText("hello");

        List<KbChunk> chunks = List.of(
                new KbChunk(101L, 9L, 0, "chunk-1", 7, "active", null)
        );

        doReturn(document).when(knowledgeBaseMapper).getDocumentById(9L);
        doReturn(chunks).when(knowledgeBaseMapper).getChunksByDocumentId(9L);
        doThrow(new RuntimeException("index failed"))
                .when(aiKnowledgeBaseClient).indexDocumentChunks(any(KbDocument.class), anyList());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.indexDocument(9L));

        assertEquals("KB_INDEX_FAILED", exception.getCode());

        ArgumentCaptor<KbIndexRecord> recordCaptor = ArgumentCaptor.forClass(KbIndexRecord.class);
        verify(knowledgeBaseMapper).insertIndexRecord(recordCaptor.capture());
        assertEquals("failed", recordCaptor.getValue().getStatus());
        assertTrue(recordCaptor.getValue().getErrorMessage().contains("index failed"));

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).updateDocument(documentCaptor.capture());
        assertEquals("failed", documentCaptor.getValue().getStatus());
    }

    @Test
    void batchIndexDocumentsSkipsLargeDocumentByDefault() {
        KbDocument chunked = new KbDocument();
        chunked.setId(21L);
        chunked.setTitle("Large Policy");
        chunked.setVersion(1);
        chunked.setStatus("chunked");

        List<KbChunk> chunks = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            chunks.add(new KbChunk((long) index, 21L, index, "chunk-" + index, 7, "active", null));
        }

        doReturn(List.of(chunked)).when(knowledgeBaseMapper).getDocuments(null, "chunked", null);
        doReturn(chunks).when(knowledgeBaseMapper).getChunksByDocumentId(21L);

        List<KnowledgeBaseResponses.BatchIndexItemResponse> responses =
                knowledgeBaseService.batchIndexDocuments(null, null);

        assertEquals(1, responses.size());
        KnowledgeBaseResponses.BatchIndexItemResponse response = responses.get(0);
        assertEquals(21L, response.getDocumentId());
        assertEquals("Large Policy", response.getTitle());
        assertEquals("chunked", response.getStatus());
        assertEquals(51, response.getChunkCount());
        assertEquals("skip", response.getAction());
        assertEquals("skipped", response.getResult());
        assertTrue(response.getError().contains("exceeds threshold"));
        verify(aiKnowledgeBaseClient, never()).indexDocumentChunks(any(KbDocument.class), anyList());
    }

    @Test
    void batchIndexDocumentsIndexesLargeDocumentWhenAllowLargeEnabled() {
        KbDocument chunked = new KbDocument();
        chunked.setId(22L);
        chunked.setTitle("Large FAQ");
        chunked.setVersion(2);
        chunked.setStatus("chunked");

        List<KbChunk> chunks = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            chunks.add(new KbChunk((long) index, 22L, index, "chunk-" + index, 7, "active", null));
        }

        doReturn(List.of(chunked)).when(knowledgeBaseMapper).getDocuments(null, "chunked", null);
        doReturn(chunks).when(knowledgeBaseMapper).getChunksByDocumentId(22L);
        when(aiKnowledgeBaseClient.indexDocumentChunks(chunked, chunks))
                .thenReturn(new AiKnowledgeBaseClient.IndexResult("local_bge_m3", "ecommerce_kb_v1", 51));

        List<KnowledgeBaseResponses.BatchIndexItemResponse> responses =
                knowledgeBaseService.batchIndexDocuments(true, null);

        assertEquals(1, responses.size());
        KnowledgeBaseResponses.BatchIndexItemResponse response = responses.get(0);
        assertEquals("index", response.getAction());
        assertEquals("success", response.getResult());
        assertEquals("indexed", response.getStatus());
        assertNull(response.getError());
        verify(aiKnowledgeBaseClient).indexDocumentChunks(chunked, chunks);
    }

    @Test
    void batchIndexDocumentsRespectsLimit() {
        KbDocument first = new KbDocument();
        first.setId(31L);
        first.setTitle("Doc A");
        first.setVersion(1);
        first.setStatus("chunked");
        KbDocument second = new KbDocument();
        second.setId(32L);
        second.setTitle("Doc B");
        second.setVersion(1);
        second.setStatus("chunked");

        List<KbChunk> firstChunks = List.of(new KbChunk(1L, 31L, 0, "chunk", 5, "active", null));
        doReturn(List.of(first, second)).when(knowledgeBaseMapper).getDocuments(null, "chunked", null);
        doReturn(firstChunks).when(knowledgeBaseMapper).getChunksByDocumentId(31L);
        when(aiKnowledgeBaseClient.indexDocumentChunks(first, firstChunks))
                .thenReturn(new AiKnowledgeBaseClient.IndexResult("local_bge_m3", "ecommerce_kb_v1", 1));

        List<KnowledgeBaseResponses.BatchIndexItemResponse> responses =
                knowledgeBaseService.batchIndexDocuments(null, 1);

        assertEquals(1, responses.size());
        assertEquals(31L, responses.get(0).getDocumentId());
        verify(knowledgeBaseMapper, never()).getChunksByDocumentId(32L);
    }

    @Test
    void deleteDocumentRemovesDependentRowsAndStoredFile() throws Exception {
        Path storedFile = Files.createDirectories(tempDir.resolve("3")).resolve("faq.txt");
        Files.writeString(storedFile, "content", StandardCharsets.UTF_8);

        KbDocument document = new KbDocument();
        document.setId(3L);
        document.setStoragePath(storedFile.toString());

        doReturn(document).when(knowledgeBaseMapper).getDocumentById(3L);
        doReturn(1).when(knowledgeBaseMapper).deleteDocument(3L);

        knowledgeBaseService.deleteDocument(3L);

        verify(knowledgeBaseMapper).deleteChunksByDocumentId(3L);
        verify(knowledgeBaseMapper).deleteIndexRecordsByDocumentId(3L);
        verify(knowledgeBaseMapper).deleteHitLogsByDocumentId(3L);
        verify(knowledgeBaseMapper).deleteDocument(3L);
        assertFalse(Files.exists(storedFile));
    }

    @Test
    void documentCrudPathWorksAtServiceLevel() {
        Map<Long, KbDocument> documents = new LinkedHashMap<>();
        doAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            long id = documents.size() + 1L;
            document.setId(id);
            documents.put(id, cloneDocument(document));
            return 1;
        }).when(knowledgeBaseMapper).insertDocument(any(KbDocument.class));
        doAnswer(invocation -> cloneDocument(documents.get(invocation.getArgument(0))))
                .when(knowledgeBaseMapper).getDocumentById(anyLong());
        doAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            documents.put(document.getId(), cloneDocument(document));
            return 1;
        }).when(knowledgeBaseMapper).updateDocument(any(KbDocument.class));
        doAnswer(invocation -> documents.remove(invocation.getArgument(0)) != null ? 1 : 0)
                .when(knowledgeBaseMapper).deleteDocument(anyLong());

        KnowledgeBaseResponses.DocumentDetailResponse created = knowledgeBaseService.createManualDocument(
                new KnowledgeBaseRequests.CreateDocumentRequest("FAQ", "support", "Original content"),
                "admin");
        assertNotNull(created.getId());

        KnowledgeBaseResponses.DocumentDetailResponse loaded = knowledgeBaseService.getDocument(created.getId());
        assertEquals("FAQ", loaded.getTitle());
        assertEquals("Original content", loaded.getContentText());

        KnowledgeBaseResponses.DocumentDetailResponse updated = knowledgeBaseService.updateDocument(
                created.getId(),
                new KnowledgeBaseRequests.UpdateDocumentRequest("Updated FAQ", "guide", "Updated content"));
        assertEquals("Updated FAQ", updated.getTitle());
        assertEquals("guide", updated.getCategory());
        assertEquals("Updated content", updated.getContentText());
        assertEquals(2, updated.getVersion());
        assertEquals("parsed", updated.getStatus());

        knowledgeBaseService.deleteDocument(created.getId());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.getDocument(created.getId()));
        assertEquals("NOT_FOUND", exception.getCode());
    }

    private KbDocument cloneDocument(KbDocument source) {
        if (source == null) {
            return null;
        }
        KbDocument copy = new KbDocument();
        copy.setId(source.getId());
        copy.setTitle(source.getTitle());
        copy.setCategory(source.getCategory());
        copy.setSourceType(source.getSourceType());
        copy.setStatus(source.getStatus());
        copy.setVersion(source.getVersion());
        copy.setStoragePath(source.getStoragePath());
        copy.setContentText(source.getContentText());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }
}
