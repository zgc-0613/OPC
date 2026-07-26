package com.opc.platform.ai.service;

import com.opc.platform.ai.entity.AiAgentContentPurgeAudit;
import com.opc.platform.ai.mapper.AiAgentContentPurgeAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentContentPurgeAuditService {
    private final AiAgentContentPurgeAuditMapper mapper;

    @Transactional
    public void success(String operation, Long sessionId, Long userId, String operatorType, Long operatorId) {
        insert(operation, sessionId, userId, operatorType, operatorId, "success", null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String operation, Long sessionId, Long userId, String operatorType,
                        Long operatorId, String result, String diagnosticCode) {
        insert(operation, sessionId, userId, operatorType, operatorId, result, diagnosticCode);
    }

    private void insert(String operation, Long sessionId, Long userId, String operatorType,
                        Long operatorId, String result, String diagnosticCode) {
        AiAgentContentPurgeAudit audit = new AiAgentContentPurgeAudit();
        audit.setOperation(operation);
        audit.setSessionId(sessionId);
        audit.setUserId(userId);
        audit.setOperatorType(operatorType);
        audit.setOperatorId(operatorId);
        audit.setResult(result);
        audit.setDiagnosticCode(diagnosticCode);
        mapper.insert(audit);
    }
}
