package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AgentRunQueueService {

    static final int MAX_EXECUTION_ATTEMPTS = 3;

    private final AiAnalysisRunMapper runMapper;
    private final Duration leaseDuration;

    @Autowired
    public AgentRunQueueService(
            AiAnalysisRunMapper runMapper,
            @Value("${opc.ai.agent.lease-seconds:45}") long leaseSeconds
    ) {
        this.runMapper = runMapper;
        this.leaseDuration = Duration.ofSeconds(Math.max(1, Math.min(leaseSeconds, 3600)));
    }

    /** Compatibility constructor for unit and integration fixtures. */
    public AgentRunQueueService(AiAnalysisRunMapper runMapper) {
        this(runMapper, 45);
    }

    @Transactional
    public AiAnalysisRun claimNext(String owner) {
        if (!StringUtils.hasText(owner) || owner.length() > 120) {
            throw new IllegalArgumentException("Agent worker owner is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        AiAnalysisRun candidate = runMapper.selectClaimableAgentRunForUpdate(now, MAX_EXECUTION_ATTEMPTS);
        if (candidate == null) return null;
        LocalDateTime requestedExpiry = now.plus(leaseDuration);
        LocalDateTime leaseExpiresAt = candidate.getDeadlineAt() == null
                ? requestedExpiry
                : requestedExpiry.isBefore(candidate.getDeadlineAt())
                ? requestedExpiry : candidate.getDeadlineAt();
        if (!leaseExpiresAt.isAfter(now)) return null;
        if (runMapper.claimAgentRun(
                candidate.getId(), owner, now, leaseExpiresAt, MAX_EXECUTION_ATTEMPTS) != 1) {
            return null;
        }
        return runMapper.selectRunForUpdate(candidate.getId());
    }

    public int finalizeUnrecoverable() {
        return runMapper.finalizeUnrecoverableAgentRuns(LocalDateTime.now(), MAX_EXECUTION_ATTEMPTS);
    }
}
