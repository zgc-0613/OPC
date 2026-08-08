package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.opc.platform.ai.dto.AgentSessionStartDTO;
import com.opc.platform.ai.entity.AiAnalyticsSnapshot;
import com.opc.platform.ai.mapper.AiAnalyticsSnapshotMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts a normal Agent Runtime run while binding only server-owned snapshot data. */
@Service
@RequiredArgsConstructor
public class AnalyticsResearchStartService {

    private final AnalyticsSnapshotService snapshotService;
    private final AiAnalyticsSnapshotMapper snapshotMapper;
    private final AgentResearchService researchService;

    @Transactional
    public AnalyticsResearchStartReceipt start(AuthenticatedUser user, JsonNode raw) {
        AnalyticsSnapshotService.Request request = snapshotService.parse(raw);
        if (request.sessionId() != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ANALYTICS_SESSION_LINK_UNAVAILABLE");
        }
        AiAnalyticsSnapshot snapshot = snapshotService.create(user, raw);
        AgentSessionStartDTO start = new AgentSessionStartDTO();
        start.setContent(request.userQuestion());
        start.setIdempotencyKey(request.idempotencyKey());
        start.setRequestedIntent("general_research");
        start.setAnalyticsSnapshotBinding(new AgentAnalyticsSnapshotBinding(
                snapshot.getId(), snapshot.getMetricId(), snapshot.getDataVersion(),
                snapshot.getFiltersJson(), snapshot.getPayloadJson()));
        AgentResearchStartReceipt receipt = researchService.start(user, start);
        snapshot.setRunId(receipt.runId());
        snapshotMapper.updateById(snapshot);
        return new AnalyticsResearchStartReceipt(
                receipt.session(), receipt.messageId(), receipt.runId(), receipt.status(),
                snapshot.getId(), snapshot.getMetricId(), snapshot.getDataVersion());
    }
}
