package com.opc.platform.ai.service;

import org.springframework.stereotype.Component;

@Component
public class AgentSessionTitlePolicy {

    private static final int MAX_CODE_POINTS = 40;

    public String fromFirstQuestion(String content) {
        String normalized = content == null ? "" : content
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("[#*_`>\\[\\]()~]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .replaceAll("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})", "");
        if (normalized.isEmpty()) return "新研究";
        int count = normalized.codePointCount(0, normalized.length());
        if (count <= MAX_CODE_POINTS) return normalized;
        return normalized.substring(0, normalized.offsetByCodePoints(0, MAX_CODE_POINTS));
    }
}
