package com.opc.platform.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "opc.ai.agent.report-purge-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AgentResearchReportPurgeScheduler {

    private final AgentResearchReportService reports;

    @Scheduled(
            fixedDelayString = "${opc.ai.agent.report-purge-delay-ms:3600000}",
            initialDelayString = "${opc.ai.agent.report-purge-initial-delay-ms:90000}"
    )
    public void purgeExpiredReports() {
        reports.purgeDue();
    }
}
