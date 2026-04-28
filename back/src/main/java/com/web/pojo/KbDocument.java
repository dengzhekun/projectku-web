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
