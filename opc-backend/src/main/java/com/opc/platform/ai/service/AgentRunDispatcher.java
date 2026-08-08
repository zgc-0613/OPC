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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentRunDispatcher {

    private final AgentRunQueueService queueService;
    private final AgentRunLifecycleService lifecycle;
    private final AgentResearchWorker worker;
    private final AgentClarificationPolicy clarificationPolicy;
    private final AgentRuntimeConfigProvider configProvider;
    private final AiAgentSessionMapper sessionMapper;
    private final AiAgentMessageMapper messageMapper;
    private final PlatformUserMapper userMapper;
    private final TaskExecutor taskExecutor;
    private final String workerOwner = "agent-worker-" + UUID.randomUUID();

    @Autowired
    public AgentRunDispatcher(
            AgentRunQueueService queueService,
            AgentRunLifecycleService lifecycle,
            AgentResearchWorker worker,
            AgentClarificationPolicy clarificationPolicy,
            AgentRuntimeConfigProvider configProvider,
            AiAgentSessionMapper sessionMapper,
            AiAgentMessageMapper messageMapper,
            PlatformUserMapper userMapper,
            @Qualifier("agentTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.queueService = queueService;
        this.lifecycle = lifecycle;
        this.worker = worker;
        this.clarificationPolicy = clarificationPolicy;
        this.configProvider = configProvider;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.taskExecutor = taskExecutor;
    }

    @Value("${opc.ai.agent.worker-enabled:true}")
    private boolean scheduledWorkerEnabled;

    @Scheduled(
            fixedDelayString = "${opc.ai.agent.worker-delay-ms:2000}",
            initialDelayString = "${opc.ai.agent.worker-initial-delay-ms:5000}"
    )
    public void scheduledTick() {
        if (scheduledWorkerEnabled) {
            queueService.finalizeUnrecoverable();
            try {
                taskExecutor.execute(this::processNext);
            } catch (TaskRejectedException ignored) {
                // The durable received run remains available for the next scheduled tick.
            }
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
                || session == null || message == null || !"active".equals(session.getStatus())
                || session.getDeletedAt() != null || session.getPurgedAt() != null) {
            lifecycle.fail(lease, "failed", ErrorCode.CONFLICT, "AGENT_RECOVERY_CONTEXT_INVALID");
            return;
        }
        AuthenticatedUser user = new AuthenticatedUser(
                storedUser.getId(), storedUser.getUsername(), storedUser.getEmail());
        String profileJson = clarificationPolicy.runtimeProfile(
                session.getProfileJson(), session.getResearchContextJson());
        worker.execute(lease, user, profileJson, message.getContent(), modelResearchBoundary(run, session));
    }

    private String modelResearchBoundary(AiAnalysisRun run, AiAgentSession session) {
        if (run.getAnalyticsSnapshotId() == null || run.getAnalyticsSnapshotJson() == null) {
            return session.getTaskContextJson();
        }
        String taskContext = session.getTaskContextJson();
        return "{\"taskContext\":" + (taskContext == null ? "null" : taskContext)
                + ",\"analyticsSnapshot\":" + run.getAnalyticsSnapshotJson()
                + ",\"analyticsMetricId\":\"" + run.getAnalyticsMetricId()
                + "\",\"analyticsDataVersion\":\"" + run.getAnalyticsDataVersion()
                + "\",\"analyticsFilters\":" + run.getAnalyticsFiltersJson() + "}";
    }
}
