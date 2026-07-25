package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentToolCall;
import com.opc.platform.ai.mapper.AdminAgentRunMapper;
import com.opc.platform.ai.mapper.AiAgentToolCallMapper;
import com.opc.platform.ai.vo.AdminAgentRunDetailVO;
import com.opc.platform.ai.vo.AdminAgentRunRowVO;
import com.opc.platform.ai.vo.AgentToolCallSummaryVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAgentRunService {

    private final AdminAgentRunMapper runMapper;
    private final AiAgentToolCallMapper toolCallMapper;

    public List<AdminAgentRunRowVO> list(int requestedLimit) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        List<AdminAgentRunRowVO> rows = runMapper.selectRecent(limit);
        return rows == null ? List.of() : rows;
    }

    public AdminAgentRunDetailVO detail(Long runId) {
        AdminAgentRunRowVO run = runMapper.selectRun(runId);
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "智能体运行记录不存在");
        List<AiAgentToolCall> tools = toolCallMapper.selectByRunId(runId);
        return new AdminAgentRunDetailVO(
                run,
                (tools == null ? List.<AiAgentToolCall>of() : tools).stream().map(call ->
                        new AgentToolCallSummaryVO(
                                call.getId(), call.getStepNo(), call.getToolName(), call.getStatus(),
                                call.getEvidenceCount(), call.getLatencyMs(), call.getEvidenceHash(), call.getDiagnosticCode()
                        )).toList()
        );
    }
}
