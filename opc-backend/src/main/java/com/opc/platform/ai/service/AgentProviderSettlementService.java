package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentProviderCall;
import com.opc.platform.ai.mapper.AiAgentProviderCallMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AgentProviderSettlementService {

    private final AiAnalysisRunMapper runMapper;
    private final AiAgentProviderCallMapper providerCallMapper;

    public AgentProviderSettlementService(
            AiAnalysisRunMapper runMapper,
            AiAgentProviderCallMapper providerCallMapper
    ) {
        this.runMapper = runMapper;
        this.providerCallMapper = providerCallMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settleActual(Long providerCallId, AiProviderResponse response) {
        if (providerCallId == null || response == null) return false;
        AiAgentProviderCall call = providerCallMapper.selectForUpdate(providerCallId);
        if (call == null) return false;

        Usage usage = Usage.from(response);
        LocalDateTime now = LocalDateTime.now();
        if ("settled_estimated".equals(call.getSettlementStatus())) {
            if (providerCallMapper.replaceEstimateWithActual(
                    providerCallId, usage.prompt(), usage.completion(), usage.total(), usage.latency(),
                    usage.requestId(), response.finishReason(), now) != 1) {
                return false;
            }
            if (runMapper.reconcileAgentProviderUsage(call.getAnalysisRunId(), now) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "Agent usage reconciliation failed");
            }
            return true;
        }
        if (!"provider_dispatched".equals(call.getSettlementStatus())) return false;
        if (providerCallMapper.settleActual(
                providerCallId, usage.prompt(), usage.completion(), usage.total(), usage.latency(),
                usage.requestId(), response.finishReason(), now) != 1) {
            return false;
        }
        if (runMapper.settleAgentUsageActual(
                call.getAnalysisRunId(), usage.prompt(), usage.completion(), usage.total(), usage.latency(),
                usage.requestId(), response.finishReason(), now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent usage settlement state changed");
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean settleLateUsage(Long providerCallId, AiProviderResponse response) {
        if (providerCallId == null || response == null) return false;
        AiAgentProviderCall call = providerCallMapper.selectForUpdate(providerCallId);
        if (call == null || !"settled_estimated".equals(call.getSettlementStatus())) return false;

        Usage usage = Usage.from(response);
        LocalDateTime now = LocalDateTime.now();
        if (providerCallMapper.replaceEstimateWithActual(
                providerCallId, usage.prompt(), usage.completion(), usage.total(), usage.latency(),
                usage.requestId(), response.finishReason(), now) != 1) {
            return false;
        }
        if (runMapper.reconcileAgentProviderUsage(call.getAnalysisRunId(), now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent usage reconciliation failed");
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleFailure(
            Long runId,
            String status,
            String errorType,
            String diagnosticCode,
            int stepCount,
            int toolCallCount
    ) {
        LocalDateTime now = LocalDateTime.now();
        int estimatedCalls = providerCallMapper.markDispatchedEstimated(runId, now);
        runMapper.settleAgentFailed(
                runId, status, "研究运行未完成", errorType, diagnosticCode,
                stepCount, toolCallCount, now);
        if (estimatedCalls > 0 && runMapper.reconcileAgentProviderUsage(runId, now) != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "Agent usage reconciliation failed");
        }
    }

    private record Usage(int prompt, int completion, int total, long latency, String requestId) {
        private static Usage from(AiProviderResponse response) {
            int prompt = Math.max(0, response.promptTokens());
            int completion = Math.max(0, response.completionTokens());
            int total = Math.max(Math.max(0, response.totalTokens()), prompt + completion);
            String requestId = response.requestId() == null || response.requestId().isBlank()
                    ? "not_provided" : response.requestId();
            return new Usage(prompt, completion, total, Math.max(0, response.latencyMs()), requestId);
        }
    }
}
