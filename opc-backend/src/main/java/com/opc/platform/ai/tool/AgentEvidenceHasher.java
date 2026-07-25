package com.opc.platform.ai.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class AgentEvidenceHasher {

    private AgentEvidenceHasher() {
    }

    static String hash(ObjectMapper objectMapper, Object value) {
        try {
            byte[] serialized = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException exception) {
            throw new AgentToolException("TOOL_RESULT_INVALID", "工具结果无法序列化");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
