package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.provider.AiClient;
import com.opc.platform.ai.provider.AiProviderDescriptor;
import com.opc.platform.ai.provider.AiProviderRequest;
import com.opc.platform.ai.provider.AiProviderResponse;
import com.opc.platform.ai.provider.AiRuntimeSettings;
import com.opc.platform.ai.provider.AiRuntimeSettingsProvider;
import com.opc.platform.ai.provider.AiRuntimeSnapshot;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AiTaskExecutionService {

    private static final long DEADLINE_GRACE_SECONDS = 30;

    private final AiAnalysisRunMapper runMapper;
    private final AiClient aiClient;
    private final AiRuntimeSettingsProvider settingsProvider;

    public <T> T execute(
            Task task,
            AiProviderRequest providerRequest,
            Function<Execution, T> resultHandler
    ) {
        AiRuntimeSnapshot runtime = settingsProvider.snapshot();
        AiRuntimeSettings settings = runtime.settings();
        AiProviderDescriptor descriptor = aiClient.descriptor(settings);
        if (!descriptor.available()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "智能体模型尚未配置或未启用");
        }
        int estimatedPromptTokens = ConservativeTokenEstimator.estimate(providerRequest);
        int maxOutputTokens = Math.max(1, settings.maxOutputTokens());
        int reservedTokens = (int) Math.min(
                Integer.MAX_VALUE,
                (long) estimatedPromptTokens + maxOutputTokens
        );
        long deadlineSeconds = Math.max(1, settings.timeout().toSeconds())
                * Math.max(1, settings.retryCount() + 1L)
                + DEADLINE_GRACE_SECONDS;

        LocalDateTime now = LocalDateTime.now();
        runMapper.failExpiredRunning(now);
        AiAnalysisRun run = new AiAnalysisRun();
        run.setUserId(task.user().userId());
        run.setTaskType(task.taskType());
        run.setCaseId(task.caseId());
        run.setStatus("running");
        run.setProvider(descriptor.provider());
        run.setModelId(descriptor.model());
        run.setPromptVersion(task.promptVersion());
        run.setEvidenceHash(task.evidenceHash());
        run.setReservedTokens((long) reservedTokens);
        run.setPromptTokens(estimatedPromptTokens);
        run.setCompletionTokens(maxOutputTokens);
        run.setTotalTokens(reservedTokens);
        run.setStartedAt(now);
        run.setDeadlineAt(now.plusSeconds(deadlineSeconds));
        run.setHeartbeatAt(now);

        try {
            int inserted = runMapper.reserve(run, runtime.dailyTokenQuota(), reservedTokens);
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "今日智能体词元额度不足");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "已有智能体请求正在进行，请勿重复提交");
        }

        AiProviderResponse response = null;
        try {
            response = aiClient.generate(providerRequest, settings);
            T result = resultHandler.apply(new Execution(run, descriptor, response));
            settle(run, response, "completed", null, response.content());
            return result;
        } catch (BusinessException exception) {
            settle(run, response, "failed", exception.getErrorCode().name(), null);
            throw exception;
        } catch (RuntimeException exception) {
            settle(run, response, "failed", "UNEXPECTED_PROVIDER_RESPONSE", null);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "AI 返回内容格式无效，请稍后重试");
        }
    }

    private void settle(
            AiAnalysisRun run,
            AiProviderResponse response,
            String status,
            String errorType,
            String resultJson
    ) {
        boolean hasUsage = response != null
                && (response.totalTokens() > 0 || response.promptTokens() > 0 || response.completionTokens() > 0);
        int promptTokens = hasUsage ? Math.max(0, response.promptTokens()) : safe(run.getPromptTokens());
        int completionTokens = hasUsage ? Math.max(0, response.completionTokens()) : safe(run.getCompletionTokens());
        int totalTokens = hasUsage
                ? Math.max(response.totalTokens(), promptTokens + completionTokens)
                : Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1L, run.getReservedTokens())));
        long latencyMs = response == null ? 0 : response.latencyMs();
        String requestId = response == null ? null : response.requestId();
        runMapper.settle(
                run.getId(), status, errorType, promptTokens, completionTokens,
                totalTokens, latencyMs, requestId, resultJson
        );
    }

    private int safe(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    public record Task(
            AuthenticatedUser user,
            String taskType,
            Long caseId,
            String promptVersion,
            String evidenceHash
    ) {
    }

    public record Execution(
            AiAnalysisRun run,
            AiProviderDescriptor descriptor,
            AiProviderResponse response
    ) {
    }
}
