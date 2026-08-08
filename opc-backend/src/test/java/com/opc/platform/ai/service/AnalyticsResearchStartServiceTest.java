package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.entity.AiAnalyticsSnapshot;
import com.opc.platform.ai.mapper.AiAnalyticsSnapshotMapper;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsResearchStartServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthenticatedUser user = new AuthenticatedUser(42L, "owner", "owner@example.com");

    @Test
    void startsResearchWithOnlyTheServerCreatedSnapshotBinding() {
        AnalyticsSnapshotService snapshots = mock(AnalyticsSnapshotService.class);
        AiAnalyticsSnapshotMapper snapshotMapper = mock(AiAnalyticsSnapshotMapper.class);
        AgentResearchService research = mock(AgentResearchService.class);
        ObjectNode raw = request();
        AnalyticsSnapshotService.Request parsed = new AnalyticsSnapshotService.Request(
                "overview.verified_cases", objectMapper.createObjectNode(), List.of(),
                "analytics-v1:current", "请说明下一步研究方向", "analytics-start-1", null, null);
        AiAnalyticsSnapshot snapshot = snapshot();
        when(snapshots.parse(raw)).thenReturn(parsed);
        when(snapshots.create(user, raw)).thenReturn(snapshot);
        when(research.start(eq(user), any(AgentSessionStartDTO.class)))
                .thenReturn(new AgentResearchStartReceipt(null, 501L, 91L, "received"));
        AnalyticsResearchStartService service = new AnalyticsResearchStartService(snapshots, snapshotMapper, research);

        AnalyticsResearchStartReceipt result = service.start(user, raw);

        assertEquals(17L, result.analyticsSnapshotId());
        assertEquals("overview.verified_cases", result.metricId());
        assertEquals("analytics-v1:current", result.dataVersion());
        assertEquals(91L, result.runId());
        ArgumentCaptor<AgentSessionStartDTO> requestCaptor = ArgumentCaptor.forClass(AgentSessionStartDTO.class);
        verify(research).start(eq(user), requestCaptor.capture());
        assertEquals("general_research", requestCaptor.getValue().getRequestedIntent());
        assertEquals("请说明下一步研究方向", requestCaptor.getValue().getContent());
        assertEquals(17L, requestCaptor.getValue().getAnalyticsSnapshotBinding().snapshotId());
        assertEquals("analytics-v1:current", requestCaptor.getValue().getAnalyticsSnapshotBinding().dataVersion());
        assertEquals(91L, snapshot.getRunId());
        verify(snapshotMapper).updateById(snapshot);
    }

    private ObjectNode request() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("metricId", "overview.verified_cases");
        node.set("filters", objectMapper.createObjectNode());
        node.putArray("selectedBucketIds");
        node.put("dataVersion", "analytics-v1:current");
        node.put("userQuestion", "请说明下一步研究方向");
        node.put("idempotencyKey", "analytics-start-1");
        return node;
    }

    private AiAnalyticsSnapshot snapshot() {
        AiAnalyticsSnapshot snapshot = new AiAnalyticsSnapshot();
        snapshot.setId(17L);
        snapshot.setUserId(42L);
        snapshot.setMetricId("overview.verified_cases");
        snapshot.setFiltersJson("{}");
        snapshot.setSelectedBucketIdsJson("[]");
        snapshot.setDataVersion("analytics-v1:current");
        snapshot.setPayloadJson("{\"metricId\":\"overview.verified_cases\",\"value\":3}");
        snapshot.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        snapshot.setCreatedAt(LocalDateTime.now());
        return snapshot;
    }
}
