package com.opc.platform.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "opc.ai.agent.history-purge-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AgentSessionHistoryPurgeScheduler {

    private final AgentSessionHistoryService historyService;

    @Scheduled(
            fixedDelayString = "${opc.ai.agent.history-purge-delay-ms:3600000}",
            initialDelayString = "${opc.ai.agent.history-purge-initial-delay-ms:60000}"
    )
    public void purgeExpiredConversationContent() {
        historyService.purgeDue();
    }
}
