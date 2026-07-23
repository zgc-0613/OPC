package com.opc.platform.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AiConnectionTestVO {
    private boolean success;
    private String message;
    private LocalDateTime testedAt;
}
