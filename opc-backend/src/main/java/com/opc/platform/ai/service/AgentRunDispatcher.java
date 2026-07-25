package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAgentSessionMapper;
import com.opc.platform.ai.provider.AgentRuntimeConfig;
import com.opc.platform.ai.provider.AgentRuntimeConfigProvider;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.userauth.AuthenticatedUser;
import com.opc.platform.userauth.entity.PlatformUser;
import com.opc.platform.userauth.mapper.PlatformUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentRunDispatcher {

    private final AgentRunQueueService queueService;
    private final AgentRunLifecycleService lifecycle;
    private final AgentResearchWorker worker;
    private final AgentRuntimeConfigProvider configProvider;
    private final AiAgentSessionMapper sessionMapper;
    private final AiAgentMessageMapper messageMapper;
    private final PlatformUserMapper userMapper;
    private final String workerOwner = "agent-worker-" + UUID.randomUUID();

    @Value("${opc.ai.agent.worker-enabled:true}")
    private boolean scheduledWorkerEnabled;

    @Scheduled(
            fixedDelayString = "${opc.ai.agent.worker-delay-ms:2000}",
            initialDelayString = "${opc.ai.agent.worker-initial-delay-ms:5000}"
    )
    public void scheduledTick() {
        if (scheduledWorkerEnabled) {
            queueService.finalizeUnrecoverable();
            processNext();
        }
    }

    public void processNext() {
        AiAnalysisRun run = queueService.claimNext(workerOwner);
        if (run == null) return;
        AgentRuntimeConfig config = configProvider.agentRuntimeConfig();
        AgentRunLease lease;
        try {
            lease = lifecycle.resume(run, config);
        } catch (RuntimeException exception) {
            lifecycle.fail(new AgentRunLease(run, null, null, config), "failed",
                    ErrorCode.CONFLICT, "AGENT_RECOVERY_CONFIG_CHANGED");
            return;
        }
        PlatformUser storedUser = userMapper.selectById(run.getUserId());
        AiAgentSession session = sessionMapper.selectById(run.getSessionId());
        AiAgentMessage message = messageMapper.selectById(run.getUserMessageId());
        if (storedUser == null || !"active".equals(storedUser.getStatus())
                || session == null || message == null || !"active".equals(session.getStatus())) {
            lifecycle.fail(lease, "failed", ErrorCode.CONFLICT, "AGENT_RECOVERY_CONTEXT_INVALID");
            return;
        }
        AuthenticatedUser user = new AuthenticatedUser(
                storedUser.getId(), storedUser.getUsername(), storedUser.getEmail());
        String profileJson = session.getResearchContextJson() == null
                ? session.getProfileJson() : session.getResearchContextJson();
        worker.execute(lease, user, profileJson, message.getContent());
    }
}
