package com.opc.platform.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiCitationVO {
    private Long sourceId;
    private String title;
    private String url;
    private String claim;
}
