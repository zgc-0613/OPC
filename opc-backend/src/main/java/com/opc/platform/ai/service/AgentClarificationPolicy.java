package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AgentClarificationPolicy {

    private final ObjectMapper objectMapper;

    public String question(String profileJson, String content) {
        try {
            JsonNode profile = StringUtils.hasText(profileJson)
                    ? objectMapper.readTree(profileJson) : objectMapper.createObjectNode();
            if (!profile.path("regionId").isIntegralNumber()) {
                return "您希望这次研究聚焦哪个地区？";
            }
            if (!profile.path("industryTagId").isIntegralNumber()
                    && !StringUtils.hasText(profile.path("industry").asText())) {
                return "您希望研究哪个行业或具体业务方向？";
            }
            if (content == null || content.trim().length() < 6) {
                return "您这次最希望验证的研究目标是什么？";
            }
            return null;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究画像格式无效");
        }
    }
}
