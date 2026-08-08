package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.entity.AiAgentMessage;
import com.opc.platform.ai.entity.AiAgentSession;
import com.opc.platform.ai.entity.AiAnalysisRun;
import com.opc.platform.ai.mapper.AiAgentMessageMapper;
import com.opc.platform.ai.mapper.AiAnalysisRunMapper;
import com.opc.platform.ai.vo.AgentResearchBranchMaterialVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgentResearchBranchService {

    private static final Set<String> BRANCHABLE_STATUSES = Set.of("completed", "evidence_insufficient");
    private static final int MAX_SUMMARY_LENGTH = 2000;

    private final AiAnalysisRunMapper runMapper;
    private final AiAgentMessageMapper messageMapper;
    private final AgentSessionService sessionService;
    private final ObjectMapper objectMapper;

    public AgentResearchBranchMaterialVO material(AuthenticatedUser user, Long runId) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        AiAnalysisRun run = runMapper.selectOwnedAgentRun(runId, user.userId());
        if (run == null) throw new BusinessException(ErrorCode.NOT_FOUND, "研究运行不存在");
        if (!BRANCHABLE_STATUSES.contains(run.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前研究尚无可用于分支的结果");
        }
        AiAgentMessage finalMessage = messageMapper.selectFinalByRun(runId);
        if (finalMessage == null || !"assistant".equals(finalMessage.getRole())
                || !"completed".equals(finalMessage.getStatus())
                || !StringUtils.hasText(finalMessage.getContent())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前研究尚无可用于分支的结果");
        }

        AiAgentSession session = sessionService.requireOwned(user, run.getSessionId());
        JsonNode taskContext = parseObject(session.getTaskContextJson());
        JsonNode result = parseObject(run.getResultJson());
        JsonNode structuredResult = result == null ? null : result.path("structuredResult");
        String requestedIntent = taskContext != null && taskContext.path("taskType").isTextual()
                ? taskContext.path("taskType").asText() : run.getRequestedIntent();
        if (!StringUtils.hasText(requestedIntent)) requestedIntent = "auto";
        String summary = firstText(structuredResult, "directAnswer", "summary");
        if (!StringUtils.hasText(summary)) summary = finalMessage.getContent();

        return new AgentResearchBranchMaterialVO(
                run.getSessionId(), run.getId(), requestedIntent, taskContext,
                session.getTaskContextVersion(), session.getTaskContextHash(), bounded(summary, MAX_SUMMARY_LENGTH),
                parseCitations(finalMessage.getCitationsJson()), bounded(text(structuredResult, "evidenceVersion"), 160)
        );
    }

    private JsonNode parseObject(String value) {
        JsonNode parsed = parse(value);
        return parsed != null && parsed.isObject() ? parsed : null;
    }

    private ArrayNode parseCitations(String value) {
        ArrayNode safe = objectMapper.createArrayNode();
        JsonNode parsed = parse(value);
        if (parsed == null || !parsed.isArray()) return safe;
        for (JsonNode citation : parsed) {
            long sourceId = citation.path("sourceId").asLong(0);
            String claim = bounded(citation.path("claim").asText(""), 300);
            if (sourceId <= 0 || !StringUtils.hasText(claim)) continue;
            ObjectNode item = safe.addObject();
            item.put("sourceId", sourceId);
            item.put("claim", claim);
        }
        return safe;
    }

    private JsonNode parse(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.path(field).isTextual()) return null;
        return node.path(field).asText();
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return null;
        String clean = value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        if (clean.codePointCount(0, clean.length()) <= maxLength) return clean;
        return clean.substring(0, clean.offsetByCodePoints(0, maxLength));
    }
}
