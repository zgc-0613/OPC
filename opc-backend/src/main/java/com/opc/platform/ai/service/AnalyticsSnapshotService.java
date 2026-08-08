package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.entity.AiAnalyticsSnapshot;
import com.opc.platform.ai.mapper.AiAnalyticsSnapshotMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates only short-lived, server-reconstructed analytics boundaries. Client
 * supplied aggregates, evidence, URLs, SQL, and entity identifiers are never
 * accepted as snapshot material.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsSnapshotService {

    public static final int TTL_MINUTES = 30;
    private static final Set<String> FIELDS = Set.of(
            "metricId", "filters", "selectedDimension", "selectedBucketIds",
            "dateRange", "granularity", "userQuestion", "dataVersion",
            "idempotencyKey", "sessionId"
    );
    private static final Set<String> UNTRUSTED_FIELDS = Set.of(
            "aggregate", "total", "percentage", "evidence", "citations", "sql", "url",
            "caseIds", "policyIds", "sourceIds", "payload", "data", "snapshot"
    );

    private final AiAnalyticsSnapshotMapper snapshotMapper;
    private final AnalyticsOverviewService overviewService;
    private final ObjectMapper objectMapper;

    public AiAnalyticsSnapshot create(AuthenticatedUser user, JsonNode raw) {
        requireUser(user);
        Request request = parse(raw);
        String requestHash = requestHash(request);
        AiAnalyticsSnapshot existing = snapshotMapper.findByUserAndIdempotency(user.userId(), request.idempotencyKey());
        if (existing != null) {
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_IDEMPOTENCY_CONFLICT");
            }
            return existing;
        }
        AnalyticsSnapshotMaterial material = overviewService.rebuildSnapshot(
                request.metricId(), request.filters(), request.selectedBucketIds());
        if (!request.dataVersion().equals(material.dataVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_DATA_VERSION_STALE");
        }
        if (!material.allowedBucketIds().containsAll(request.selectedBucketIds())) {
            invalid("ANALYTICS_INVALID_FILTER");
        }
        LocalDateTime now = LocalDateTime.now();
        AiAnalyticsSnapshot snapshot = new AiAnalyticsSnapshot();
        snapshot.setUserId(user.userId());
        snapshot.setIdempotencyKey(request.idempotencyKey());
        snapshot.setRequestHash(requestHash);
        snapshot.setMetricId(material.metricId());
        snapshot.setFiltersJson(material.filtersJson());
        snapshot.setSelectedDimension(request.selectedDimension());
        snapshot.setSelectedBucketIdsJson(write(request.selectedBucketIds()));
        snapshot.setDataVersion(material.dataVersion());
        snapshot.setPayloadJson(material.payloadJson());
        snapshot.setSnapshotHash(hash(material.metricId() + "\n" + material.filtersJson() + "\n"
                + write(request.selectedBucketIds()) + "\n" + material.dataVersion() + "\n" + material.payloadJson()));
        snapshot.setExpiresAt(now.plusMinutes(TTL_MINUTES));
        snapshot.setCreatedAt(now);
        try {
            snapshotMapper.insert(snapshot);
        } catch (DuplicateKeyException exception) {
            AiAnalyticsSnapshot replay = snapshotMapper.findByUserAndIdempotency(user.userId(), request.idempotencyKey());
            if (replay != null && requestHash.equals(replay.getRequestHash())) return replay;
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_IDEMPOTENCY_CONFLICT");
        }
        return snapshot;
    }

    public String requestHash(JsonNode raw) {
        return requestHash(parse(raw));
    }

    /** The start boundary checks ownership and freshness before any run can reserve tokens. */
    public AiAnalyticsSnapshot requireUsableOwned(AuthenticatedUser user, Long snapshotId) {
        requireUser(user);
        if (snapshotId == null || snapshotId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_SNAPSHOT_INVALID");
        }
        AiAnalyticsSnapshot snapshot = snapshotMapper.selectById(snapshotId);
        if (snapshot == null || !user.userId().equals(snapshot.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_SNAPSHOT_NOT_FOUND");
        }
        if (snapshot.getExpiresAt() == null || !snapshot.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ANALYTICS_SNAPSHOT_EXPIRED");
        }
        return snapshot;
    }

    /** Read-only historical access supports an already accepted run after the snapshot TTL passes. */
    public AiAnalyticsSnapshot requireOwnedHistorical(AuthenticatedUser user, Long snapshotId) {
        requireUser(user);
        AiAnalyticsSnapshot snapshot = snapshotMapper.selectById(snapshotId);
        if (snapshot == null || !user.userId().equals(snapshot.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "ANALYTICS_SNAPSHOT_NOT_FOUND");
        }
        return snapshot;
    }

    public Request parse(JsonNode raw) {
        if (raw == null || !raw.isObject()) invalid("ANALYTICS_UNTRUSTED_PAYLOAD");
        raw.fieldNames().forEachRemaining(field -> {
            if (UNTRUSTED_FIELDS.contains(field) || !FIELDS.contains(field)) {
                invalid("ANALYTICS_UNTRUSTED_PAYLOAD");
            }
        });
        String metricId = requiredText(raw, "metricId", 80, "ANALYTICS_INVALID_FILTER");
        String dataVersion = requiredText(raw, "dataVersion", 128, "ANALYTICS_INVALID_FILTER");
        JsonNode filters = normalizeFilters(metricId, raw.get("filters"));
        String selectedDimension = optionalText(raw, "selectedDimension", 80, "ANALYTICS_INVALID_FILTER");
        if (selectedDimension != null) invalid("ANALYTICS_INVALID_FILTER");
        if (raw.hasNonNull("dateRange") || raw.hasNonNull("granularity")) invalid("ANALYTICS_INVALID_FILTER");
        Long sessionId = optionalPositiveLong(raw, "sessionId", "ANALYTICS_INVALID_FILTER");
        List<String> buckets = bucketIds(raw.get("selectedBucketIds"));
        if ("industry.case_count".equals(metricId)
                && (buckets.size() != 1
                || !buckets.get(0).equals("industry:" + filters.path("industryTagId").asLong()))) {
            invalid("ANALYTICS_INVALID_FILTER");
        }
        if (!"industry.case_count".equals(metricId) && !buckets.isEmpty()) {
            invalid("ANALYTICS_INVALID_FILTER");
        }
        String question = requiredText(raw, "userQuestion", 2000, "ANALYTICS_INVALID_FILTER");
        String idempotencyKey = requiredText(raw, "idempotencyKey", 64, "ANALYTICS_INVALID_FILTER");
        if (!idempotencyKey.matches("[A-Za-z0-9_-]{8,64}")) invalid("ANALYTICS_INVALID_FILTER");
        return new Request(metricId, filters.deepCopy(), buckets, dataVersion, question, idempotencyKey, sessionId, null);
    }

    private JsonNode normalizeFilters(String metricId, JsonNode filters) {
        if (filters == null || !filters.isObject()) invalid("ANALYTICS_INVALID_FILTER");
        if (!"industry.case_count".equals(metricId)) {
            if (filters.size() != 0) invalid("ANALYTICS_INVALID_FILTER");
            return filters.deepCopy();
        }
        JsonNode industryTagId = filters.get("industryTagId");
        if (filters.size() != 1 || industryTagId == null || !industryTagId.isIntegralNumber()
                || !industryTagId.canConvertToLong() || industryTagId.asLong() <= 0) {
            invalid("ANALYTICS_INVALID_FILTER");
        }
        return objectMapper.createObjectNode().put("industryTagId", industryTagId.asLong());
    }

    private List<String> bucketIds(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray() || node.size() > 10) invalid("ANALYTICS_FILTER_TOO_LARGE");
        List<String> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !StringUtils.hasText(item.asText()) || item.asText().length() > 128
                    || !unique.add(item.asText())) {
                invalid("ANALYTICS_INVALID_FILTER");
            }
            result.add(item.asText());
        }
        return List.copyOf(result);
    }

    private String requiredText(JsonNode raw, String field, int max, String error) {
        String value = optionalText(raw, field, max, error);
        if (!StringUtils.hasText(value)) invalid(error);
        return value;
    }

    private String optionalText(JsonNode raw, String field, int max, String error) {
        JsonNode node = raw.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) invalid(error);
        String value = node.asText().trim();
        if (value.codePointCount(0, value.length()) > max) invalid(error);
        return value.isEmpty() ? null : value;
    }

    private Long optionalPositiveLong(JsonNode raw, String field, String error) {
        JsonNode node = raw.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) invalid(error);
        return node.asLong();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "ANALYTICS_SNAPSHOT_SERIALIZATION_FAILED");
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String requestHash(Request request) {
        return hash(request.metricId() + "\n" + write(request.filters()) + "\n"
                + write(request.selectedBucketIds()) + "\n" + request.dataVersion() + "\n"
                + request.userQuestion() + "\n" + request.idempotencyKey() + "\n"
                + (request.sessionId() == null ? "" : request.sessionId()));
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "UNAUTHORIZED");
        }
    }

    private void invalid(String code) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, code);
    }

    public record Request(
            String metricId,
            JsonNode filters,
            List<String> selectedBucketIds,
            String dataVersion,
            String userQuestion,
            String idempotencyKey,
            Long sessionId,
            String selectedDimension
    ) {
    }
}
