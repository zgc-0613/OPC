package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentRunQueueService {

    static final int MAX_EXECUTION_ATTEMPTS = 3;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(45);

    private final AiAnalysisRunMapper runMapper;

    @Transactional
    public AiAnalysisRun claimNext(String owner) {
        if (!StringUtils.hasText(owner) || owner.length() > 120) {
            throw new IllegalArgumentException("Agent worker owner is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        AiAnalysisRun candidate = runMapper.selectClaimableAgentRunForUpdate(now, MAX_EXECUTION_ATTEMPTS);
        if (candidate == null) return null;
        if (runMapper.claimAgentRun(
                candidate.getId(), owner, now, now.plus(LEASE_DURATION), MAX_EXECUTION_ATTEMPTS) != 1) {
            return null;
        }
        return runMapper.selectRunForUpdate(candidate.getId());
    }

    public int finalizeUnrecoverable() {
        return runMapper.finalizeUnrecoverableAgentRuns(LocalDateTime.now(), MAX_EXECUTION_ATTEMPTS);
    }
}
