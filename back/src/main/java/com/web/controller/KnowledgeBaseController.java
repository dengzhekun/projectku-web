package com.web.controller;

import cn.hutool.core.map.MapUtil;
import com.web.dto.KnowledgeBaseRequests;
import com.web.service.KnowledgeBaseService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/kb/documents")
public class KnowledgeBaseController {

    private static final String DEFAULT_OPERATOR = "admin";

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(success(knowledgeBaseService.getDocuments(category, status, keyword)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody KnowledgeBaseRequests.CreateDocumentRequest request) {
        return ResponseEntity.ok(success(knowledgeBaseService.createManualDocument(request, DEFAULT_OPERATOR)));
    }

    @GetMapping("/misses")
    public ResponseEntity<Map<String, Object>> misses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(success(knowledgeBaseService.getMissLogs(status, keyword)));
    }

    @GetMapping("/customer-service-logs")
    public ResponseEntity<Map<String, Object>> customerServiceLogs(
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(success(knowledgeBaseService.getCustomerServiceLogs(route, sourceType, keyword)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return ResponseEntity.ok(success(knowledgeBaseService.getDocument(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable Long id,
            @RequestBody KnowledgeBaseRequests.UpdateDocumentRequest request) {
        return ResponseEntity.ok(success(knowledgeBaseService.updateDocument(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return ResponseEntity.ok(success(null));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam String category) {
        return ResponseEntity.ok(success(knowledgeBaseService.uploadDocument(file, title, category, DEFAULT_OPERATOR)));
    }

    @PostMapping("/{id}/chunk")
    public ResponseEntity<Map<String, Object>> chunk(@PathVariable Long id) {
        knowledgeBaseService.chunkDocument(id);
        return ResponseEntity.ok(success(null));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<Map<String, Object>> chunks(@PathVariable Long id) {
        return ResponseEntity.ok(success(knowledgeBaseService.getChunks(id)));
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<Map<String, Object>> index(@PathVariable Long id) {
        knowledgeBaseService.indexDocument(id);
        return ResponseEntity.ok(success(null));
    }

    @PostMapping("/batch-index")
    public ResponseEntity<Map<String, Object>> batchIndex(
            @RequestParam(required = false) Boolean allowLarge,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(success(knowledgeBaseService.batchIndexDocuments(allowLarge, limit)));
    }

    @GetMapping("/{id}/index-records")
    public ResponseEntity<Map<String, Object>> indexRecords(@PathVariable Long id) {
        return ResponseEntity.ok(success(knowledgeBaseService.getIndexRecords(id)));
    }

    @GetMapping("/{id}/hits")
    public ResponseEntity<Map<String, Object>> hits(@PathVariable Long id) {
        return ResponseEntity.ok(success(knowledgeBaseService.getHitLogs(id)));
    }

    private Map<String, Object> success(Object data) {
        return MapUtil.builder(new HashMap<String, Object>())
                .put("code", 200)
                .put("message", "success")
                .put("data", data)
                .build();
    }
}
