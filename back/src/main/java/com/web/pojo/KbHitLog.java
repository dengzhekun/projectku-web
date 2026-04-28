package com.web.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbHitLog {
    private Long id;
    private Long documentId;
    private Long chunkId;
    private String queryText;
    private String conversationId;
    private LocalDateTime hitTime;
}
