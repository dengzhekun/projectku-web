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
