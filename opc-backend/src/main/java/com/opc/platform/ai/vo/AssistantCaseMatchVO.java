package com.opc.platform.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantCaseMatchVO {

    private Long id;
    private String title;
    private String regionName;
    private String category;
    private String summary;
    private String detailUrl;
    private String geographicLevel;
    private String matchReason;
}
