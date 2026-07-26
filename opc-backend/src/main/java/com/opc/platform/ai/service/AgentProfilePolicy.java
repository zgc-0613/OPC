package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class AgentProfilePolicy {

    private static final Set<String> FIELDS = Set.of(
            "ventureType", "regionId", "industryTagId", "industry", "stage",
            "budgetRange", "goal", "resources"
    );

    private final ObjectMapper objectMapper;
    private final RegionMapper regionMapper;
    private final TagMapper tagMapper;

    public String canonicalJson(JsonNode profile) {
        if (profile == null || profile.isNull()) return null;
        if (!profile.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Research profile must be an object");
        }
        profile.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Research profile contains an unknown field");
            }
        });
        JsonNode normalized = canonical(profile);
        validateFields(normalized);
        try {
            String value = objectMapper.writeValueAsString(normalized);
            if (value.length() > 8000) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Research profile is too long");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Research profile is invalid");
        }
    }

    public String fingerprint(String canonicalProfileJson) {
        return sha256(canonicalProfileJson == null ? "null" : canonicalProfileJson);
    }

    private void validateFields(JsonNode profile) {
        validateEnum(profile, "ventureType", Set.of(
                "solo_company", "individual_business", "small_team", "exploring"));
        validateText(profile, "industry", 80);
        validateEnum(profile, "stage", Set.of("idea", "validation", "early_operation", "growth"));
        validateEnum(profile, "budgetRange", Set.of(
                "under_100k", "100k_500k", "500k_1m", "over_1m", "undecided"));
        validateText(profile, "goal", 200);
        validateText(profile, "resources", 300);
        Long regionId = validateId(profile, "regionId");
        if (regionId != null && regionMapper.selectById(regionId) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Research profile regionId is invalid");
        }
        Long industryTagId = validateId(profile, "industryTagId");
        if (industryTagId != null) {
            Tag tag = tagMapper.selectById(industryTagId);
            if (tag == null || !Boolean.TRUE.equals(tag.getIsIndustry())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "Research profile industryTagId is invalid");
            }
            ((ObjectNode) profile).put("industry", tag.getName());
        }
    }

    private void validateEnum(JsonNode profile, String field, Set<String> allowed) {
        JsonNode value = profile.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || !allowed.contains(value.asText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Research profile field " + field + " is invalid");
        }
    }

    private void validateText(JsonNode profile, String field, int maxCodePoints) {
        JsonNode value = profile.get(field);
        if (value == null || value.isNull()) return;
        if (!value.isTextual()
                || value.asText().codePointCount(0, value.asText().length()) > maxCodePoints) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Research profile field " + field + " is invalid");
        }
    }

    private Long validateId(JsonNode profile, String field) {
        JsonNode value = profile.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Research profile field " + field + " is invalid");
        }
        return value.asLong();
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            value.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, child) -> result.set(key, canonical(child)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(child -> result.add(canonical(child)));
            return result;
        }
        return value.deepCopy();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
