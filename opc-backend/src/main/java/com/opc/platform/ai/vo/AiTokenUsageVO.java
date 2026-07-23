package com.opc.platform.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenUsageVO {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
}
