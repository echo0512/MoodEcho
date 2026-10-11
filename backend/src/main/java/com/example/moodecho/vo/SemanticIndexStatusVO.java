package com.example.moodecho.vo;

import lombok.Data;

@Data
public class SemanticIndexStatusVO {
    private long pending;
    private long processing;
    private long ready;
    private long failed;
    private java.time.LocalDateTime lastCompletedAt;
    private boolean available;
}
