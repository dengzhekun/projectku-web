package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbIndexRecord {
    private Long id;
    private Long documentId;
    private Integer version;
    private String embeddingProvider;
    private String vectorCollection;
    private Integer indexedChunkCount;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
}
