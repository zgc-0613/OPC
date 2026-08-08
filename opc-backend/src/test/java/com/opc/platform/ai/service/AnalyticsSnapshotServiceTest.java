package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.entity.AiAnalyticsSnapshot;
import com.opc.platform.ai.mapper.AiAnalyticsSnapshotMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsSnapshotServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthenticatedUser owner = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void createsAnOwnedSnapshotFromTheServerRebuiltMetric() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        when(overview.rebuildSnapshot(eq("overview.verified_cases"), any(), eq(List.of())))
                .thenReturn(material("analytics-v1:current"));
        when(snapshots.insert(any(AiAnalyticsSnapshot.class))).thenAnswer(invocation -> {
            invocation.<AiAnalyticsSnapshot>getArgument(0).setId(17L);
            return 1;
        });
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);

        AiAnalyticsSnapshot snapshot = service.create(owner, validRequest("analytics-v1:current"));

        assertEquals(17L, snapshot.getId());
        assertEquals(42L, snapshot.getUserId());
        assertEquals("overview.verified_cases", snapshot.getMetricId());
        assertEquals("analytics-v1:current", snapshot.getDataVersion());
        assertTrue(snapshot.getExpiresAt().isAfter(snapshot.getCreatedAt()));
        verify(snapshots).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void createsAnIndustrySnapshotWithTheExactServerRebuiltTagFilter() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        ObjectNode filters = objectMapper.createObjectNode().put("industryTagId", 7L);
        AnalyticsSnapshotMaterial material = new AnalyticsSnapshotMaterial(
                "industry.case_count", "analytics-v1:current", "{\"industryTagId\":7}",
                "{\"metricId\":\"industry.case_count\",\"buckets\":[{\"bucketId\":\"industry:7\"}]}",
                List.of("industry:7"));
        when(overview.rebuildSnapshot(eq("industry.case_count"), eq(filters), eq(List.of("industry:7"))))
                .thenReturn(material);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        ObjectNode request = validIndustryRequest("analytics-v1:current", 7L);

        AiAnalyticsSnapshot snapshot = service.create(owner, request);

        assertEquals("industry.case_count", snapshot.getMetricId());
        assertEquals("{\"industryTagId\":7}", snapshot.getFiltersJson());
        verify(overview).rebuildSnapshot(eq("industry.case_count"), eq(filters), eq(List.of("industry:7")));
    }

    @Test
    void rejectsASyntheticBucketForAnOverviewMetric() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        ObjectNode request = validRequest("analytics-v1:current");
        request.withArray("selectedBucketIds").add("overview.verified_cases");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(owner, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("ANALYTICS_INVALID_FILTER", exception.getMessage());
        verify(overview, never()).rebuildSnapshot(any(), any(), any());
        verify(snapshots, never()).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void refusesAStaleDataVersionBeforeCreatingAnySnapshot() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        when(overview.rebuildSnapshot(eq("overview.verified_cases"), any(), eq(List.of())))
                .thenReturn(material("analytics-v1:new"));
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(owner, validRequest("analytics-v1:old")));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("ANALYTICS_DATA_VERSION_STALE", exception.getMessage());
        verify(snapshots, never()).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void exactIdempotencyReplayReturnsTheOriginalSnapshotBeforeVersionRebuild() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        AiAnalyticsSnapshot existing = storedSnapshot(42L, LocalDateTime.now().minusMinutes(1));
        existing.setIdempotencyKey("analytics-test-1");
        existing.setRequestHash(service.requestHash(validRequest("analytics-v1:current")));
        when(snapshots.findByUserAndIdempotency(42L, "analytics-test-1")).thenReturn(existing);

        AiAnalyticsSnapshot replay = service.create(owner, validRequest("analytics-v1:current"));

        assertEquals(17L, replay.getId());
        verify(overview, never()).rebuildSnapshot(any(), any(), any());
        verify(snapshots, never()).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void rejectsAChangedRequestReusingAnExistingSnapshotIdempotencyKey() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        AiAnalyticsSnapshot existing = storedSnapshot(42L, LocalDateTime.now().plusMinutes(1));
        existing.setIdempotencyKey("analytics-test-1");
        existing.setRequestHash(service.requestHash(validRequest("analytics-v1:current")));
        when(snapshots.findByUserAndIdempotency(42L, "analytics-test-1")).thenReturn(existing);
        ObjectNode changed = validRequest("analytics-v1:current");
        changed.put("userQuestion", "Use the same metric for a different research objective.");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(owner, changed));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
        assertEquals("ANALYTICS_IDEMPOTENCY_CONFLICT", exception.getMessage());
        verify(overview, never()).rebuildSnapshot(any(), any(), any());
        verify(snapshots, never()).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void rejectsUntrustedClientAggregatesBeforeRebuildingOrPersisting() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        ObjectNode request = validRequest("analytics-v1:current");
        request.put("aggregate", 99);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(owner, request));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        assertEquals("ANALYTICS_UNTRUSTED_PAYLOAD", exception.getMessage());
        verify(overview, never()).rebuildSnapshot(any(), any(), any());
        verify(snapshots, never()).insert(any(AiAnalyticsSnapshot.class));
    }

    @Test
    void hidesAnotherUsersSnapshotAndRejectsExpiredSnapshotsForNewResearch() {
        AiAnalyticsSnapshotMapper snapshots = mock(AiAnalyticsSnapshotMapper.class);
        AnalyticsOverviewService overview = mock(AnalyticsOverviewService.class);
        AnalyticsSnapshotService service = new AnalyticsSnapshotService(snapshots, overview, objectMapper);
        AiAnalyticsSnapshot otherUsersSnapshot = storedSnapshot(99L, LocalDateTime.now().plusMinutes(5));
        when(snapshots.selectById(17L)).thenReturn(otherUsersSnapshot);

        BusinessException ownerException = assertThrows(BusinessException.class,
                () -> service.requireUsableOwned(owner, 17L));
        assertEquals(ErrorCode.NOT_FOUND, ownerException.getErrorCode());

        AiAnalyticsSnapshot expired = storedSnapshot(42L, LocalDateTime.now().minusSeconds(1));
        when(snapshots.selectById(18L)).thenReturn(expired);
        BusinessException expiredException = assertThrows(BusinessException.class,
                () -> service.requireUsableOwned(owner, 18L));

        assertEquals(ErrorCode.CONFLICT, expiredException.getErrorCode());
        assertEquals("ANALYTICS_SNAPSHOT_EXPIRED", expiredException.getMessage());
    }

    private ObjectNode validRequest(String dataVersion) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("metricId", "overview.verified_cases");
        request.set("filters", objectMapper.createObjectNode());
        request.putArray("selectedBucketIds");
        request.put("dataVersion", dataVersion);
        request.put("userQuestion", "请基于当前已核验案例数据，说明下一步研究方向。");
        request.put("idempotencyKey", "analytics-test-1");
        return request;
    }

    private ObjectNode validIndustryRequest(String dataVersion, long industryTagId) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("metricId", "industry.case_count");
        request.set("filters", objectMapper.createObjectNode().put("industryTagId", industryTagId));
        request.putArray("selectedBucketIds").add("industry:" + industryTagId);
        request.put("dataVersion", dataVersion);
        request.put("userQuestion", "请基于当前行业案例数据，说明下一步研究方向。");
        request.put("idempotencyKey", "analytics-industry-1");
        return request;
    }

    private AnalyticsSnapshotMaterial material(String dataVersion) {
        return new AnalyticsSnapshotMaterial(
                "overview.verified_cases", dataVersion, "{}",
                "{\"metricId\":\"overview.verified_cases\",\"value\":3,\"sampleSize\":3}",
                List.of());
    }

    private AiAnalyticsSnapshot storedSnapshot(Long userId, LocalDateTime expiresAt) {
        AiAnalyticsSnapshot snapshot = new AiAnalyticsSnapshot();
        snapshot.setId(17L);
        snapshot.setUserId(userId);
        snapshot.setMetricId("overview.verified_cases");
        snapshot.setDataVersion("analytics-v1:current");
        snapshot.setFiltersJson("{}");
        snapshot.setPayloadJson("{}");
        snapshot.setExpiresAt(expiresAt);
        snapshot.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        return snapshot;
    }
}
