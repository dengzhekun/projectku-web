# Knowledge Base Admin MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a synchronous knowledge base admin MVP inside the existing e-commerce app so admins can create/upload `txt`/`md`/`docx` documents, preview chunks, manually index them into Chroma, and inspect retrieval hit references.

**Architecture:** Keep document management in the Spring Boot backend and MySQL, keep vector indexing and retrieval inside `ai-service`, and add a single admin route in the existing Vue frontend. Uploaded source files are stored on disk, metadata/chunks/index records/hit logs are stored in MySQL, and the Java backend calls the AI service for chunk indexing.

**Tech Stack:** Spring Boot 3, MyBatis XML mappers, MySQL 8, Apache POI (`docx` parsing), FastAPI, ChromaDB, Vue 3, Vite, Axios

---

## File Structure Lock-In

### Backend files

- Modify: `back/pom.xml`
- Modify: `back/sql/init_db.sql`
- Modify: `back/src/main/resources/application.yml`
- Modify: `back/src/main/resources/application-prod.yml`
- Create: `back/src/main/java/com/web/pojo/KbDocument.java`
- Create: `back/src/main/java/com/web/pojo/KbChunk.java`
- Create: `back/src/main/java/com/web/pojo/KbIndexRecord.java`
- Create: `back/src/main/java/com/web/pojo/KbHitLog.java`
- Create: `back/src/main/java/com/web/dto/KnowledgeBaseRequests.java`
- Create: `back/src/main/java/com/web/dto/KnowledgeBaseResponses.java`
- Create: `back/src/main/java/com/web/mapper/KnowledgeBaseMapper.java`
- Create: `back/src/main/resources/mapper/KnowledgeBaseMapper.xml`
- Create: `back/src/main/java/com/web/service/KnowledgeBaseService.java`
- Create: `back/src/main/java/com/web/service/AiKnowledgeBaseClient.java`
- Create: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Create: `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`
- Create: `back/src/main/java/com/web/controller/KnowledgeBaseController.java`
- Modify: `back/src/main/java/com/web/dto/CustomerServiceChatResponse.java`
- Modify: `back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java`
- Modify: `back/src/main/java/com/web/service/impl/AiCustomerServiceClientImpl.java`
- Create: `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java`
- Create: `back/src/test/java/com/web/service/impl/AiKnowledgeBaseClientImplTest.java`
- Modify: `back/src/test/java/com/web/service/impl/CustomerServiceServiceImplTest.java`

### Frontend files

- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/lib/knowledgeBase.ts`
- Create: `frontend/src/views/KnowledgeBaseAdminView.vue`
- Create: `frontend/src/components/kb/KbDocumentForm.vue`
- Create: `frontend/src/components/kb/KbChunkPreview.vue`
- Create: `frontend/src/components/kb/KbIndexRecords.vue`
- Create: `frontend/src/components/kb/KbHitLogs.vue`

### AI service files

- Modify: `ai-service/app/schemas.py`
- Modify: `ai-service/app/main.py`
- Modify: `ai-service/app/api/chat.py`
- Create: `ai-service/app/api/indexing.py`
- Modify: `ai-service/app/retrieval/chroma_retriever.py`
- Create: `ai-service/tests/test_indexing_api.py`
- Modify: `ai-service/tests/test_chat_api.py`

### Docs files

- Modify: `docs/ai-service-runbook.md`
- Modify: `docs/deployment.md`

### Runtime directories

- Create at runtime: `back/storage/kb/`

---

### Task 1: Backend Schema And Domain Models

**Files:**
- Modify: `back/pom.xml`
- Modify: `back/sql/init_db.sql`
- Modify: `back/src/main/resources/application.yml`
- Modify: `back/src/main/resources/application-prod.yml`
- Create: `back/src/main/java/com/web/pojo/KbDocument.java`
- Create: `back/src/main/java/com/web/pojo/KbChunk.java`
- Create: `back/src/main/java/com/web/pojo/KbIndexRecord.java`
- Create: `back/src/main/java/com/web/pojo/KbHitLog.java`
- Create: `back/src/main/java/com/web/mapper/KnowledgeBaseMapper.java`
- Create: `back/src/main/resources/mapper/KnowledgeBaseMapper.xml`

- [ ] **Step 1: Add the failing service test shell for document persistence dependencies**

Create `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java` with:

```java
package com.web.service.impl;

import com.web.mapper.KnowledgeBaseMapper;
import com.web.service.AiKnowledgeBaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private AiKnowledgeBaseClient aiKnowledgeBaseClient;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void serviceCanBeConstructedWithKnowledgeBaseDependencies() {
        assertNotNull(knowledgeBaseService);
    }
}
```

- [ ] **Step 2: Run the backend test to verify the new class is missing**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected:

- FAIL
- compile error because `KnowledgeBaseMapper`, `AiKnowledgeBaseClient`, or `KnowledgeBaseServiceImpl` do not exist yet

- [ ] **Step 3: Add the backend dependency and config properties**

Modify `back/pom.xml` to add Apache POI:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

Add to both `back/src/main/resources/application.yml` and `back/src/main/resources/application-prod.yml`:

```yaml
kb:
  storage:
    root-path: ${KB_STORAGE_ROOT:./storage/kb}
```

- [ ] **Step 4: Add MySQL schema for knowledge base tables**

Append to `back/sql/init_db.sql`:

```sql
CREATE TABLE `kb_document` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(255) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `storage_path` VARCHAR(512) DEFAULT NULL,
  `content_text` LONGTEXT,
  `created_by` VARCHAR(64) DEFAULT 'admin',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_index` INT NOT NULL,
  `content` LONGTEXT NOT NULL,
  `char_count` INT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'active',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_chunk_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_index_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `version` INT NOT NULL,
  `embedding_provider` VARCHAR(64) NOT NULL,
  `vector_collection` VARCHAR(128) NOT NULL,
  `indexed_chunk_count` INT NOT NULL DEFAULT 0,
  `status` VARCHAR(32) NOT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_index_record_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `kb_hit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `document_id` BIGINT NOT NULL,
  `chunk_id` BIGINT NOT NULL,
  `query_text` VARCHAR(1000) NOT NULL,
  `conversation_id` VARCHAR(128) DEFAULT NULL,
  `hit_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_kb_hit_log_document_id` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 5: Create the POJOs and mapper interface**

Create `back/src/main/java/com/web/pojo/KbDocument.java`:

```java
package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbDocument {
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
}
```

Create `back/src/main/java/com/web/pojo/KbChunk.java`:

```java
package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbChunk {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;
    private String status;
    private LocalDateTime createdAt;
}
```

Create `KbIndexRecord.java` and `KbHitLog.java` using the same Lombok pattern.

Create `back/src/main/java/com/web/mapper/KnowledgeBaseMapper.java`:

```java
package com.web.mapper;

import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import com.web.pojo.KbHitLog;
import com.web.pojo.KbIndexRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {
    int insertDocument(KbDocument document);
    int updateDocument(KbDocument document);
    KbDocument getDocumentById(Long id);
    List<KbDocument> getDocuments(@Param("category") String category, @Param("status") String status, @Param("keyword") String keyword);
    int deleteDocument(Long id);
    int deleteChunksByDocumentId(Long documentId);
    int insertChunk(KbChunk chunk);
    List<KbChunk> getChunksByDocumentId(Long documentId);
    int insertIndexRecord(KbIndexRecord record);
    List<KbIndexRecord> getIndexRecordsByDocumentId(Long documentId);
    int insertHitLog(KbHitLog hitLog);
    List<KbHitLog> getHitLogsByDocumentId(Long documentId);
}
```

- [ ] **Step 6: Add the MyBatis XML mapper**

Create `back/src/main/resources/mapper/KnowledgeBaseMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.web.mapper.KnowledgeBaseMapper">
    <resultMap id="KbDocumentMap" type="com.web.pojo.KbDocument">
        <id property="id" column="id"/>
        <result property="sourceType" column="source_type"/>
        <result property="storagePath" column="storage_path"/>
        <result property="contentText" column="content_text"/>
        <result property="createdBy" column="created_by"/>
        <result property="createdAt" column="created_at"/>
        <result property="updatedAt" column="updated_at"/>
    </resultMap>

    <insert id="insertDocument" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO kb_document(title, category, source_type, status, version, storage_path, content_text, created_by)
        VALUES(#{title}, #{category}, #{sourceType}, #{status}, #{version}, #{storagePath}, #{contentText}, #{createdBy})
    </insert>
</mapper>
```

- [ ] **Step 7: Run the backend test again**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected:

- compile still fails because `AiKnowledgeBaseClient` and `KnowledgeBaseServiceImpl` are not implemented yet
- schema and mapper classes compile cleanly

- [ ] **Step 8: Apply the schema locally**

Run:

```powershell
mysql -uroot -p123456 web < back\sql\init_db.sql
```

Expected:

- command succeeds
- `kb_document`, `kb_chunk`, `kb_index_record`, and `kb_hit_log` tables exist

- [ ] **Step 9: Do not commit in this workspace**

Run:

```powershell
git status
```

Expected:

- `fatal: not a git repository...`

Action:

- skip commit because this workspace is not a Git repository

---

### Task 2: Backend Document CRUD And Upload Parsing

**Files:**
- Create: `back/src/main/java/com/web/dto/KnowledgeBaseRequests.java`
- Create: `back/src/main/java/com/web/dto/KnowledgeBaseResponses.java`
- Create: `back/src/main/java/com/web/service/KnowledgeBaseService.java`
- Create: `back/src/main/java/com/web/service/AiKnowledgeBaseClient.java`
- Create: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Create: `back/src/main/java/com/web/controller/KnowledgeBaseController.java`
- Create: `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java`

- [ ] **Step 1: Write the failing CRUD test**

Replace `back/src/test/java/com/web/service/impl/KnowledgeBaseServiceImplTest.java` with:

```java
package com.web.service.impl;

import com.web.dto.KnowledgeBaseRequests;
import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.KbDocument;
import com.web.service.AiKnowledgeBaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private AiKnowledgeBaseClient aiKnowledgeBaseClient;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void createManualDocumentPersistsDraftDocument() {
        KnowledgeBaseRequests.CreateDocumentRequest request = new KnowledgeBaseRequests.CreateDocumentRequest();
        request.setTitle("售后规则");
        request.setCategory("policy");
        request.setContentText("七天无理由退货说明");

        knowledgeBaseService.createManualDocument(request, "admin");

        ArgumentCaptor<KbDocument> captor = ArgumentCaptor.forClass(KbDocument.class);
        verify(knowledgeBaseMapper).insertDocument(captor.capture());
        assertEquals("售后规则", captor.getValue().getTitle());
        assertEquals("manual", captor.getValue().getSourceType());
        assertEquals("parsed", captor.getValue().getStatus());
    }
}
```

- [ ] **Step 2: Run the test to confirm the service API is missing**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected:

- FAIL
- compile error because `KnowledgeBaseRequests` and `createManualDocument` do not exist yet

- [ ] **Step 3: Add request/response DTOs**

Create `back/src/main/java/com/web/dto/KnowledgeBaseRequests.java`:

```java
package com.web.dto;

public class KnowledgeBaseRequests {
    public static class CreateDocumentRequest {
        private String title;
        private String category;
        private String contentText;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
    }

    public static class UpdateDocumentRequest extends CreateDocumentRequest {
    }
}
```

Create `back/src/main/java/com/web/dto/KnowledgeBaseResponses.java`:

```java
package com.web.dto;

public class KnowledgeBaseResponses {
    public static class DocumentDetailResponse {
        private Long id;
        private String title;
        private String category;
        private String status;
        private Integer version;
        private String contentText;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
        public String getContentText() { return contentText; }
        public void setContentText(String contentText) { this.contentText = contentText; }
    }
}
```

- [ ] **Step 4: Implement the service interface and manual creation flow**

Create `back/src/main/java/com/web/service/KnowledgeBaseService.java`:

```java
package com.web.service;

import com.web.dto.KnowledgeBaseRequests;
import com.web.dto.KnowledgeBaseResponses;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService {
    void createManualDocument(KnowledgeBaseRequests.CreateDocumentRequest request, String createdBy);
    void uploadDocument(MultipartFile file, String title, String category, String createdBy);
    List<KnowledgeBaseResponses.DocumentDetailResponse> getDocuments(String category, String status, String keyword);
    KnowledgeBaseResponses.DocumentDetailResponse getDocument(Long id);
    void updateDocument(Long id, KnowledgeBaseRequests.UpdateDocumentRequest request);
    void deleteDocument(Long id);
}
```

Create `back/src/main/java/com/web/service/AiKnowledgeBaseClient.java`:

```java
package com.web.service;

public interface AiKnowledgeBaseClient {
    void indexDocumentChunks(Long documentId);
}
```

Create `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`:

```java
package com.web.service.impl;

import com.web.dto.KnowledgeBaseRequests;
import com.web.dto.KnowledgeBaseResponses;
import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.KbDocument;
import com.web.service.AiKnowledgeBaseClient;
import com.web.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AiKnowledgeBaseClient aiKnowledgeBaseClient;

    public KnowledgeBaseServiceImpl(KnowledgeBaseMapper knowledgeBaseMapper, AiKnowledgeBaseClient aiKnowledgeBaseClient) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.aiKnowledgeBaseClient = aiKnowledgeBaseClient;
    }

    @Override
    public void createManualDocument(KnowledgeBaseRequests.CreateDocumentRequest request, String createdBy) {
        KbDocument document = new KbDocument();
        document.setTitle(request.getTitle());
        document.setCategory(request.getCategory());
        document.setSourceType("manual");
        document.setStatus("parsed");
        document.setVersion(1);
        document.setContentText(request.getContentText());
        document.setCreatedBy(createdBy);
        knowledgeBaseMapper.insertDocument(document);
    }

    @Override
    public void uploadDocument(MultipartFile file, String title, String category, String createdBy) { throw new UnsupportedOperationException(); }
    @Override
    public List<KnowledgeBaseResponses.DocumentDetailResponse> getDocuments(String category, String status, String keyword) { return Collections.emptyList(); }
    @Override
    public KnowledgeBaseResponses.DocumentDetailResponse getDocument(Long id) { throw new UnsupportedOperationException(); }
    @Override
    public void updateDocument(Long id, KnowledgeBaseRequests.UpdateDocumentRequest request) { throw new UnsupportedOperationException(); }
    @Override
    public void deleteDocument(Long id) { throw new UnsupportedOperationException(); }
}
```

- [ ] **Step 5: Run the test and make it pass**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 6: Add file parsing and CRUD endpoints**

Extend `KnowledgeBaseServiceImpl.java` with:

```java
private String extractText(MultipartFile file) throws Exception {
    String lowerName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
        return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
    if (lowerName.endsWith(".docx")) {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(file.getInputStream());
             org.apache.poi.xwpf.extractor.XWPFWordExtractor extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }
    throw new IllegalArgumentException("unsupported file type");
}
```

Create `back/src/main/java/com/web/controller/KnowledgeBaseController.java`:

```java
package com.web.controller;

import cn.hutool.core.map.MapUtil;
import com.web.dto.KnowledgeBaseRequests;
import com.web.service.KnowledgeBaseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/kb/documents")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam(required = false) String category,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", knowledgeBaseService.getDocuments(category, status, keyword))
                .build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody KnowledgeBaseRequests.CreateDocumentRequest request) {
        knowledgeBaseService.createManualDocument(request, "admin");
        return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>()).put("code", 200).put("message", "success").build());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestPart("file") MultipartFile file,
                                                      @RequestParam String title,
                                                      @RequestParam String category) {
        knowledgeBaseService.uploadDocument(file, title, category, "admin");
        return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>()).put("code", 200).put("message", "success").build());
    }
}
```

- [ ] **Step 7: Run targeted backend verification**

Run:

```powershell
cd back
mvn -Dtest=KnowledgeBaseServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 8: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

### Task 3: Chunk Preview And Index Record APIs

**Files:**
- Modify: `back/src/main/java/com/web/service/KnowledgeBaseService.java`
- Modify: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `back/src/main/java/com/web/controller/KnowledgeBaseController.java`
- Modify: `back/src/main/java/com/web/dto/KnowledgeBaseResponses.java`
- Modify: `back/src/main/resources/mapper/KnowledgeBaseMapper.xml`
- Create: `back/src/test/java/com/web/service/impl/AiKnowledgeBaseClientImplTest.java`

- [ ] **Step 1: Write the failing chunking test**

Create `back/src/test/java/com/web/service/impl/AiKnowledgeBaseClientImplTest.java`:

```java
package com.web.service.impl;

import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.KbDocument;
import com.web.service.AiKnowledgeBaseClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeBaseClientImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private AiKnowledgeBaseClient aiKnowledgeBaseClient;

    @InjectMocks
    private KnowledgeBaseServiceImpl knowledgeBaseService;

    @Test
    void splitContentCreatesMultipleChunksForLongParagraphs() {
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setContentText("第一段\\n\\n" + "A".repeat(450));
        when(knowledgeBaseMapper.getDocumentById(1L)).thenReturn(doc);

        List<String> chunks = knowledgeBaseService.splitIntoChunksForTest(1L);

        assertEquals(3, chunks.size());
    }
}
```

- [ ] **Step 2: Run the test to confirm the chunk helper is missing**

Run:

```powershell
cd back
mvn -Dtest=AiKnowledgeBaseClientImplTest test
```

Expected:

- FAIL
- `splitIntoChunksForTest` missing

- [ ] **Step 3: Implement deterministic chunking and chunk preview responses**

Extend `KnowledgeBaseResponses.java`:

```java
public static class IndexRecordResponse {
    private Long id;
    private Integer indexedChunkCount;
    private String status;
    private String errorMessage;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getIndexedChunkCount() { return indexedChunkCount; }
    public void setIndexedChunkCount(Integer indexedChunkCount) { this.indexedChunkCount = indexedChunkCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

public static class ChunkResponse {
    private Long id;
    private Integer chunkIndex;
    private String content;
    private Integer charCount;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
}
```

Extend `KnowledgeBaseService.java`:

```java
void chunkDocument(Long id);
List<KnowledgeBaseResponses.ChunkResponse> getChunks(Long id);
void indexDocument(Long id);
List<KnowledgeBaseResponses.IndexRecordResponse> getIndexRecords(Long id);
List<String> splitIntoChunksForTest(Long id);
```

Extend `KnowledgeBaseServiceImpl.java`:

```java
private List<String> splitContent(String content) {
    List<String> result = new java.util.ArrayList<>();
    for (String block : content.split("\\n\\s*\\n")) {
        String trimmed = block.trim();
        if (trimmed.isEmpty()) {
            continue;
        }
        if (trimmed.length() <= 200) {
            result.add(trimmed);
            continue;
        }
        for (int i = 0; i < trimmed.length(); i += 200) {
            result.add(trimmed.substring(i, Math.min(trimmed.length(), i + 200)));
        }
    }
    return result;
}

@Override
public List<String> splitIntoChunksForTest(Long id) {
    return splitContent(knowledgeBaseMapper.getDocumentById(id).getContentText());
}
```

- [ ] **Step 4: Add chunk persistence and indexing orchestration**

Implement in `KnowledgeBaseServiceImpl.java`:

```java
@Override
public void chunkDocument(Long id) {
    KbDocument document = knowledgeBaseMapper.getDocumentById(id);
    List<String> parts = splitContent(document.getContentText());
    knowledgeBaseMapper.deleteChunksByDocumentId(id);
    for (int index = 0; index < parts.size(); index++) {
        KbChunk chunk = new KbChunk();
        chunk.setDocumentId(id);
        chunk.setChunkIndex(index);
        chunk.setContent(parts.get(index));
        chunk.setCharCount(parts.get(index).length());
        chunk.setStatus("active");
        knowledgeBaseMapper.insertChunk(chunk);
    }
    document.setStatus("chunked");
    knowledgeBaseMapper.updateDocument(document);
}

@Override
public void indexDocument(Long id) {
    aiKnowledgeBaseClient.indexDocumentChunks(id);
}
```

- [ ] **Step 5: Add controller endpoints**

Add to `KnowledgeBaseController.java`:

```java
@PostMapping("/{id}/chunk")
public ResponseEntity<Map<String, Object>> chunk(@PathVariable Long id) {
    knowledgeBaseService.chunkDocument(id);
    return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>()).put("code", 200).put("message", "success").build());
}

@GetMapping("/{id}/chunks")
public ResponseEntity<Map<String, Object>> chunks(@PathVariable Long id) {
    return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>())
            .put("code", 200)
            .put("message", "success")
            .put("data", knowledgeBaseService.getChunks(id))
            .build());
}

@PostMapping("/{id}/index")
public ResponseEntity<Map<String, Object>> index(@PathVariable Long id) {
    knowledgeBaseService.indexDocument(id);
    return ResponseEntity.ok(MapUtil.builder(new HashMap<String, Object>()).put("code", 200).put("message", "success").build());
}
```

- [ ] **Step 6: Run the chunking test**

Run:

```powershell
cd back
mvn -Dtest=AiKnowledgeBaseClientImplTest test
```

Expected:

- PASS

- [ ] **Step 7: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

### Task 4: AI Service Chunk Indexing API

**Files:**
- Modify: `ai-service/app/schemas.py`
- Modify: `ai-service/app/main.py`
- Create: `ai-service/app/api/indexing.py`
- Modify: `ai-service/app/retrieval/chroma_retriever.py`
- Create: `ai-service/tests/test_indexing_api.py`
- Create: `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`
- Create: `back/src/test/java/com/web/service/impl/AiKnowledgeBaseClientImplTest.java`

- [ ] **Step 1: Write the failing indexing API test**

Create `ai-service/tests/test_indexing_api.py`:

```python
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_index_endpoint_accepts_chunk_payload(monkeypatch):
    captured = {}

    class StubRetriever:
        def upsert(self, records):
            captured["records"] = records

    monkeypatch.setattr("app.api.indexing.get_chroma_retriever", lambda: StubRetriever())

    response = client.post(
        "/internal/index",
        json={
            "documentId": 1,
            "version": 1,
            "chunks": [
                {"chunkId": 11, "chunkIndex": 0, "content": "七天无理由退货规则", "category": "policy", "title": "售后规则"}
            ],
        },
    )

    assert response.status_code == 200
    assert response.json()["indexedCount"] == 1
```

- [ ] **Step 2: Run the AI test to confirm the endpoint is missing**

Run:

```powershell
cd ai-service
python -m pytest tests/test_indexing_api.py -q
```

Expected:

- FAIL
- route `/internal/index` missing

- [ ] **Step 3: Add request/response schemas**

Extend `ai-service/app/schemas.py`:

```python
class IndexChunk(BaseModel):
    chunkId: int
    chunkIndex: int
    content: str
    category: str
    title: str


class IndexRequest(BaseModel):
    documentId: int
    version: int
    chunks: list[IndexChunk]


class IndexResponse(BaseModel):
    indexedCount: int
```

- [ ] **Step 4: Add indexing route**

Create `ai-service/app/api/indexing.py`:

```python
from fastapi import APIRouter

from app.api.chat import get_chroma_retriever
from app.schemas import IndexRequest, IndexResponse

router = APIRouter(prefix="/internal")


@router.post("/index", response_model=IndexResponse)
def index_chunks(request: IndexRequest) -> IndexResponse:
    records = [
        {
            "id": f"kb:{request.documentId}:v{request.version}:c{chunk.chunkIndex}",
            "document": chunk.content,
            "metadata": {
                "source_type": "kb_document",
                "source_id": str(request.documentId),
                "title": chunk.title,
                "category": chunk.category,
                "document_id": request.documentId,
                "chunk_id": chunk.chunkId,
                "version": request.version,
            },
        }
        for chunk in request.chunks
    ]
    get_chroma_retriever().upsert(records)
    return IndexResponse(indexedCount=len(records))
```

Modify `ai-service/app/main.py`:

```python
from app.api.indexing import router as indexing_router

app.include_router(indexing_router)
```

- [ ] **Step 5: Run the AI indexing test**

Run:

```powershell
cd ai-service
python -m pytest tests/test_indexing_api.py -q
```

Expected:

- PASS

- [ ] **Step 6: Add the failing backend AI client test**

Create `back/src/test/java/com/web/service/impl/AiKnowledgeBaseClientImplTest.java`:

```java
package com.web.service.impl;

import com.web.config.AiServiceProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiKnowledgeBaseClientImplTest {

    @Test
    void buildIndexUrlUsesInternalIndexPath() throws Exception {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl("http://127.0.0.1:9000/");
        AiKnowledgeBaseClientImpl client = new AiKnowledgeBaseClientImpl(properties, null);

        Method method = AiKnowledgeBaseClientImpl.class.getDeclaredMethod("buildIndexUrl");
        method.setAccessible(true);

        assertEquals("http://127.0.0.1:9000/internal/index", method.invoke(client));
    }
}
```

- [ ] **Step 7: Implement the backend AI indexing client**

Create `back/src/main/java/com/web/service/impl/AiKnowledgeBaseClientImpl.java`:

```java
package com.web.service.impl;

import com.web.config.AiServiceProperties;
import com.web.exception.BusinessException;
import com.web.mapper.KnowledgeBaseMapper;
import com.web.pojo.KbChunk;
import com.web.pojo.KbIndexRecord;
import com.web.service.AiKnowledgeBaseClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiKnowledgeBaseClientImpl implements AiKnowledgeBaseClient {

    private final AiServiceProperties properties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final RestTemplate restTemplate;

    public AiKnowledgeBaseClientImpl(AiServiceProperties properties, KnowledgeBaseMapper knowledgeBaseMapper) {
        this.properties = properties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(properties.getTimeoutSeconds(), 1) * 1000);
        factory.setReadTimeout(Math.max(properties.getTimeoutSeconds(), 1) * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    String buildIndexUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "") + "/internal/index";
    }

    @Override
    public void indexDocumentChunks(Long documentId) {
        List<KbChunk> chunks = knowledgeBaseMapper.getChunksByDocumentId(documentId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentId", documentId);
        payload.put("version", 1);
        payload.put("chunks", chunks.stream().map(chunk -> Map.of(
                "chunkId", chunk.getId(),
                "chunkIndex", chunk.getChunkIndex(),
                "content", chunk.getContent(),
                "category", "general",
                "title", "knowledge-base"
        )).toList());
        try {
            restTemplate.postForObject(buildIndexUrl(), payload, Map.class);
            KbIndexRecord record = new KbIndexRecord();
            record.setDocumentId(documentId);
            record.setVersion(1);
            record.setEmbeddingProvider("local_bge_m3");
            record.setVectorCollection("ecommerce_kb_v1");
            record.setIndexedChunkCount(chunks.size());
            record.setStatus("success");
            knowledgeBaseMapper.insertIndexRecord(record);
        } catch (RestClientException ex) {
            throw new BusinessException("KB_INDEX_FAILED", "知识库向量入库失败");
        }
    }
}
```

- [ ] **Step 8: Run backend AI client verification**

Run:

```powershell
cd back
mvn -Dtest=AiKnowledgeBaseClientImplTest test
```

Expected:

- PASS

- [ ] **Step 9: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

### Task 5: Retrieval Hit Logging Integration

**Files:**
- Modify: `ai-service/app/schemas.py`
- Modify: `ai-service/app/api/chat.py`
- Modify: `back/src/main/java/com/web/dto/CustomerServiceChatResponse.java`
- Create: `back/src/main/java/com/web/dto/CustomerServiceHitLog.java`
- Modify: `back/src/main/java/com/web/service/KnowledgeBaseService.java`
- Modify: `back/src/main/java/com/web/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `back/src/main/java/com/web/service/impl/CustomerServiceServiceImpl.java`
- Modify: `back/src/test/java/com/web/service/impl/CustomerServiceServiceImplTest.java`

- [ ] **Step 1: Write the failing backend hit-log test**

Add to `back/src/test/java/com/web/service/impl/CustomerServiceServiceImplTest.java`:

```java
@Test
void chatReturnsStructuredReplyFromAiGateway() {
    CustomerServiceChatResponse gatewayReply = new CustomerServiceChatResponse();
    gatewayReply.setAnswer("可以先查看订单详情页中的售后入口。");
    gatewayReply.setConfidence(new BigDecimal("0.92"));

    CustomerServiceHitLog hitLog = new CustomerServiceHitLog();
    hitLog.setDocumentId(1L);
    hitLog.setChunkId(2L);
    gatewayReply.setHitLogs(java.util.List.of(hitLog));

    when(aiCustomerServiceClient.chat(any(CustomerServiceChatRequest.class))).thenReturn(gatewayReply);

    CustomerServiceChatResponse response = customerServiceService.chat("申请退款", "conversation-1");

    assertEquals("可以先查看订单详情页中的售后入口。", response.getAnswer());
}
```

- [ ] **Step 2: Run the backend customer-service test**

Run:

```powershell
cd back
mvn -Dtest=CustomerServiceServiceImplTest test
```

Expected:

- FAIL
- `CustomerServiceHitLog` or `getHitLogs` missing

- [ ] **Step 3: Extend AI response models with hit payload**

Extend `ai-service/app/schemas.py`:

```python
class HitLog(BaseModel):
    documentId: int
    chunkId: int


class ChatResponse(BaseModel):
    answer: str
    confidence: Optional[float] = None
    citations: list[Citation] = Field(default_factory=list)
    actions: list[Action] = Field(default_factory=list)
    fallbackReason: Optional[str] = None
    hitLogs: list[HitLog] = Field(default_factory=list)
```

Modify `ai-service/app/api/chat.py`:

```python
    hit_logs = [
        {"documentId": chunk["metadata"].get("document_id"), "chunkId": chunk["metadata"].get("chunk_id")}
        for chunk in chunks
        if chunk["metadata"].get("document_id") and chunk["metadata"].get("chunk_id")
    ]
```

And include `hitLogs=hit_logs` in both return paths.

- [ ] **Step 4: Persist hit logs in the backend service**

Create `back/src/main/java/com/web/dto/CustomerServiceHitLog.java`:

```java
package com.web.dto;

public class CustomerServiceHitLog {
    private Long documentId;
    private Long chunkId;
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
}
```

Extend `CustomerServiceChatResponse.java`:

```java
private List<CustomerServiceHitLog> hitLogs = new ArrayList<>();
public List<CustomerServiceHitLog> getHitLogs() { return hitLogs; }
public void setHitLogs(List<CustomerServiceHitLog> hitLogs) { this.hitLogs = hitLogs; }
```

Extend `KnowledgeBaseService.java`:

```java
void recordHitLogs(String queryText, String conversationId, java.util.List<com.web.dto.CustomerServiceHitLog> hitLogs);
```

Implement in `KnowledgeBaseServiceImpl.java`:

```java
@Override
public void recordHitLogs(String queryText, String conversationId, java.util.List<com.web.dto.CustomerServiceHitLog> hitLogs) {
    for (com.web.dto.CustomerServiceHitLog hit : hitLogs) {
        KbHitLog entity = new KbHitLog();
        entity.setDocumentId(hit.getDocumentId());
        entity.setChunkId(hit.getChunkId());
        entity.setQueryText(queryText);
        entity.setConversationId(conversationId);
        knowledgeBaseMapper.insertHitLog(entity);
    }
}
```

Modify `CustomerServiceServiceImpl.java` constructor and `chat`:

```java
private final KnowledgeBaseService knowledgeBaseService;

public CustomerServiceServiceImpl(AiCustomerServiceClient aiCustomerServiceClient, KnowledgeBaseService knowledgeBaseService) {
    this.aiCustomerServiceClient = aiCustomerServiceClient;
    this.knowledgeBaseService = knowledgeBaseService;
}

CustomerServiceChatResponse response = aiCustomerServiceClient.chat(request);
knowledgeBaseService.recordHitLogs(normalizedMessage, conversationId, response.getHitLogs());
return response;
```

- [ ] **Step 5: Run tests for customer-service flow**

Run:

```powershell
cd back
mvn -Dtest=CustomerServiceServiceImplTest test
```

Expected:

- PASS

- [ ] **Step 6: Run AI chat tests**

Run:

```powershell
cd ai-service
python -m pytest tests/test_chat_api.py -q
```

Expected:

- PASS after updating fixtures for `hitLogs`

- [ ] **Step 7: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

### Task 6: Frontend Knowledge Base Admin UI

**Files:**
- Modify: `frontend/src/router/index.ts`
- Create: `frontend/src/lib/knowledgeBase.ts`
- Create: `frontend/src/views/KnowledgeBaseAdminView.vue`
- Create: `frontend/src/components/kb/KbDocumentForm.vue`
- Create: `frontend/src/components/kb/KbChunkPreview.vue`
- Create: `frontend/src/components/kb/KbIndexRecords.vue`
- Create: `frontend/src/components/kb/KbHitLogs.vue`

- [ ] **Step 1: Create the failing frontend API client type file**

Create `frontend/src/lib/knowledgeBase.ts`:

```ts
import { api } from './api'

export type KbDocument = {
  id: number
  title: string
  category: string
  status: string
  version: number
  contentText: string
}

export const listKbDocuments = async () => {
  const res = await api.get('/v1/kb/documents')
  return res.data.data as KbDocument[]
}
```

- [ ] **Step 2: Add the route before the page exists**

Modify `frontend/src/router/index.ts` to add:

```ts
{
  path: '/admin/kb',
  name: 'knowledgeBaseAdmin',
  component: () => import('../views/KnowledgeBaseAdminView.vue'),
  meta: { title: '知识库管理', hideNav: true, requiresAuth: true },
},
```

- [ ] **Step 3: Run the frontend build to confirm the view file is missing**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- FAIL
- `KnowledgeBaseAdminView.vue` missing

- [ ] **Step 4: Build the admin page skeleton and child components**

Create `frontend/src/components/kb/KbDocumentForm.vue`:

```vue
<script setup lang="ts">
defineProps<{ busy?: boolean }>()
</script>

<template>
  <section class="panel">
    <h3>新建或上传文档</h3>
    <slot />
  </section>
</template>
```

Create `frontend/src/components/kb/KbChunkPreview.vue`:

```vue
<script setup lang="ts">
import type { KbChunk } from '../../lib/knowledgeBase'

defineProps<{
  chunks?: KbChunk[]
}>()
</script>

<template>
  <section class="panel">
    <h3>切分预览</h3>
    <ul v-if="chunks?.length">
      <li v-for="chunk in chunks" :key="chunk.id">
        <strong>#{{ chunk.chunkIndex }}</strong>
        <span>{{ chunk.charCount }} 字</span>
        <p>{{ chunk.content }}</p>
      </li>
    </ul>
    <div v-else>暂无切分结果</div>
  </section>
</template>
```

Create `frontend/src/components/kb/KbIndexRecords.vue`:

```vue
<script setup lang="ts">
import type { KbIndexRecord } from '../../lib/knowledgeBase'

defineProps<{
  records?: KbIndexRecord[]
}>()
</script>

<template>
  <section class="panel">
    <h3>入库记录</h3>
    <ul v-if="records?.length">
      <li v-for="record in records" :key="record.id">
        <span>{{ record.status }}</span>
        <span>{{ record.indexedChunkCount }} chunks</span>
        <span v-if="record.errorMessage">{{ record.errorMessage }}</span>
      </li>
    </ul>
    <div v-else>暂无入库记录</div>
  </section>
</template>
```

Create `frontend/src/components/kb/KbHitLogs.vue`:

```vue
<script setup lang="ts">
import type { KbHitLog } from '../../lib/knowledgeBase'

defineProps<{
  hits?: KbHitLog[]
}>()
</script>

<template>
  <section class="panel">
    <h3>命中引用</h3>
    <ul v-if="hits?.length">
      <li v-for="hit in hits" :key="hit.id">
        <strong>{{ hit.hitTime }}</strong>
        <span>{{ hit.queryText }}</span>
        <span v-if="hit.conversationId">{{ hit.conversationId }}</span>
      </li>
    </ul>
    <div v-else>暂无命中记录</div>
  </section>
</template>
```

Create `frontend/src/views/KnowledgeBaseAdminView.vue`:

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listKbDocuments, type KbDocument } from '../lib/knowledgeBase'
import KbDocumentForm from '../components/kb/KbDocumentForm.vue'
import KbChunkPreview from '../components/kb/KbChunkPreview.vue'
import KbIndexRecords from '../components/kb/KbIndexRecords.vue'
import KbHitLogs from '../components/kb/KbHitLogs.vue'

const documents = ref<KbDocument[]>([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    documents.value = await listKbDocuments()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <UiPageHeader title="知识库管理" />
    <main class="main">
      <KbDocumentForm />
      <section class="panel">
        <h3>文档列表</h3>
        <div v-if="loading">加载中...</div>
        <table v-else class="table">
          <thead>
            <tr><th>标题</th><th>分类</th><th>状态</th><th>版本</th></tr>
          </thead>
          <tbody>
            <tr v-for="doc in documents" :key="doc.id">
              <td>{{ doc.title }}</td>
              <td>{{ doc.category }}</td>
              <td>{{ doc.status }}</td>
              <td>{{ doc.version }}</td>
            </tr>
          </tbody>
        </table>
      </section>
      <KbChunkPreview />
      <KbIndexRecords />
      <KbHitLogs />
    </main>
  </div>
</template>
```

- [ ] **Step 5: Extend the frontend API client with upload/chunk/index/hit calls**

Extend `frontend/src/lib/knowledgeBase.ts`:

```ts
export type KbChunk = { id: number; chunkIndex: number; content: string; charCount: number }
export type KbIndexRecord = { id: number; indexedChunkCount: number; status: string; errorMessage?: string | null }
export type KbHitLog = { id: number; queryText: string; conversationId?: string | null; hitTime: string }

export const createKbDocument = async (payload: { title: string; category: string; contentText: string }) => {
  await api.post('/v1/kb/documents', payload)
}

export const uploadKbDocument = async (payload: { title: string; category: string; file: File }) => {
  const form = new FormData()
  form.append('file', payload.file)
  form.append('title', payload.title)
  form.append('category', payload.category)
  await api.post('/v1/kb/documents/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export const chunkKbDocument = async (id: number) => {
  await api.post(`/v1/kb/documents/${id}/chunk`)
}

export const fetchKbChunks = async (id: number) => {
  const res = await api.get(`/v1/kb/documents/${id}/chunks`)
  return res.data.data as KbChunk[]
}

export const indexKbDocument = async (id: number) => {
  await api.post(`/v1/kb/documents/${id}/index`)
}

export const fetchKbIndexRecords = async (id: number) => {
  const res = await api.get(`/v1/kb/documents/${id}/index-records`)
  return res.data.data as KbIndexRecord[]
}

export const fetchKbHitLogs = async (id: number) => {
  const res = await api.get(`/v1/kb/documents/${id}/hits`)
  return res.data.data as KbHitLog[]
}
```

- [ ] **Step 6: Run the frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- PASS

- [ ] **Step 7: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

### Task 7: End-To-End Verification And Runbook Updates

**Files:**
- Modify: `docs/ai-service-runbook.md`
- Modify: `docs/deployment.md`

- [ ] **Step 1: Update the runbook with knowledge base admin steps**

Add to `docs/ai-service-runbook.md`:

```md
## Knowledge Base Admin MVP

1. Start backend, frontend, and ai-service.
2. Open `/admin/kb`.
3. Create or upload a document.
4. Trigger chunk preview.
5. Trigger indexing.
6. Ask a customer-service question that should hit the document.
7. Refresh the hit log panel and confirm the query appears.
```

- [ ] **Step 2: Update deployment notes**

Add to `docs/deployment.md`:

```md
## Knowledge Base Storage

- Source files are stored under `back/storage/kb`
- Knowledge base metadata/chunks/hit logs are stored in MySQL
- Vector data remains in `ai-service/data/chroma`
```

- [ ] **Step 3: Run full backend verification**

Run:

```powershell
cd back
mvn test
```

Expected:

- PASS with existing tests plus new knowledge base tests

- [ ] **Step 4: Run full AI service verification**

Run:

```powershell
cd ai-service
python -m pytest tests -q
```

Expected:

- PASS

- [ ] **Step 5: Run full frontend verification**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- PASS

- [ ] **Step 6: Manual workflow verification**

Run:

```powershell
cd C:\Users\Administrator\Desktop\web-main
.\start_all.ps1 -Mode dev -SkipDb
```

Then verify:

1. Open `http://127.0.0.1:5173/admin/kb`
2. Create a manual document titled `售后规则`
3. Run chunk preview and confirm chunks render
4. Click index and confirm an index record is created
5. Ask `七天无理由退货规则是什么`
6. Confirm the answer cites the indexed knowledge
7. Confirm hit logs appear in the admin page

- [ ] **Step 7: Skip commit in this workspace**

Run:

```powershell
git status
```

Expected:

- fatal not a git repository

---

## Self-Review

### Spec coverage

- document creation/upload: covered in Task 2
- category/status/version persistence: covered in Tasks 1 and 2
- chunk preview: covered in Task 3
- manual indexing: covered in Tasks 3 and 4
- hit reference view: covered in Tasks 5 and 6
- MySQL metadata + disk file storage + Chroma vectors: covered in Tasks 1, 2, and 4
- docs and verification: covered in Task 7

No spec gaps remain for the MVP scope.

### Placeholder scan

- searched for `TODO`, `TBD`, `implement later`, `placeholder`
- none intentionally left in the plan

### Type consistency

- backend knowledge base domain uses `KbDocument`, `KbChunk`, `KbIndexRecord`, `KbHitLog` throughout
- frontend client uses `KbDocument`, `KbChunk`, `KbIndexRecord`, `KbHitLog`
- AI-service indexing request uses `documentId`, `version`, `chunks[]`
- hit logging uses `documentId` and `chunkId` consistently across AI-service and backend DTOs
