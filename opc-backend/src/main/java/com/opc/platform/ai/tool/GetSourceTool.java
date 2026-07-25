package com.opc.platform.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class GetSourceTool implements AgentTool<GetSourceArguments> {

    private final SourceMapper sourceMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "get_source";
    }

    @Override
    public String description() {
        return "读取当前运行已检索到且仍为公开已核验状态的来源详情";
    }

    @Override
    public Class<GetSourceArguments> argumentType() {
        return GetSourceArguments.class;
    }

    @Override
    public String argumentSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["sourceId"],
                 "properties":{"sourceId":{"type":"integer"}}}
                """;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, GetSourceArguments arguments) {
        if (!context.allowedSourceIds().contains(arguments.getSourceId())) {
            throw new AgentToolException("FORBIDDEN_SOURCE_ID", "来源不属于当前运行的检索结果");
        }
        Source source = sourceMapper.selectById(arguments.getSourceId());
        if (!eligible(source)) {
            throw new AgentToolException(com.opc.platform.common.enums.ErrorCode.CONFLICT,
                    "EVIDENCE_CHANGED", "来源状态已变化，请重新检索");
        }
        SourceItem item = new SourceItem(
                source.getId(), bounded(source.getTitle(), 240), bounded(source.getPublisher(), 160),
                source.getUrl(), source.getAccessedAt() == null ? null : source.getAccessedAt().toString(),
                bounded(source.getNotes(), 600)
        );
        Map<String, Object> versioned = Map.of(
                "item", item,
                "status", source.getStatus(),
                "evidenceStatus", source.getAiEvidenceStatus(),
                "revision", source.getEvidenceRevision() == null ? 0L : source.getEvidenceRevision(),
                "updatedAt", source.getUpdatedAt() == null ? "" : source.getUpdatedAt().toString()
        );
        return new AgentToolResult(
                objectMapper.valueToTree(item),
                1,
                AgentEvidenceHasher.hash(objectMapper, versioned),
                Set.of(source.getId()),
                Set.of()
        );
    }

    private boolean eligible(Source source) {
        if (source == null || !"published".equals(source.getStatus())
                || !"verified".equals(source.getAiEvidenceStatus())
                || !StringUtils.hasText(source.getTitle())
                || !StringUtils.hasText(source.getPublisher())
                || !StringUtils.hasText(source.getUrl())) {
            return false;
        }
        try {
            URI uri = URI.create(source.getUrl());
            return Set.of("http", "https").contains(uri.getScheme()) && uri.getUserInfo() == null && uri.getHost() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String bounded(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record SourceItem(
            Long sourceId,
            String title,
            String publisher,
            String url,
            String accessedAt,
            String notes
    ) {
    }
}
