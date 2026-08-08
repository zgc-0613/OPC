package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.tool.AgentToolException;
import com.opc.platform.ai.tool.PhaseThreeEvidenceBundle;
import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Rebuilds the current authoritative evidence manifest immediately before persistence. */
@Service
public class PhaseThreeEvidenceResolver {

    private final CaseItemMapper caseMapper;
    private final PolicyMapper policyMapper;
    private final SourceMapper sourceMapper;
    private final ObjectMapper objectMapper;

    public PhaseThreeEvidenceResolver(
            CaseItemMapper caseMapper,
            PolicyMapper policyMapper,
            SourceMapper sourceMapper,
            ObjectMapper objectMapper
    ) {
        this.caseMapper = caseMapper;
        this.policyMapper = policyMapper;
        this.sourceMapper = sourceMapper;
        this.objectMapper = objectMapper;
    }

    public PhaseThreeEvidenceBundle resolve(
            Set<Long> toolCaseIds,
            Set<Long> toolPolicyIds,
            Set<Long> toolSourceIds,
            JsonNode taskContext
    ) {
        Set<Long> caseIds = positiveIds(toolCaseIds);
        Set<Long> policyIds = positiveIds(toolPolicyIds);
        Set<Long> sourceIds = positiveIds(toolSourceIds);
        if (taskContext != null && taskContext.isObject()) {
            JsonNode selectedCases = taskContext.path("caseIds");
            if (selectedCases.isArray()) {
                selectedCases.forEach(value -> {
                    if (value.isIntegralNumber() && value.asLong() > 0) caseIds.add(value.asLong());
                });
            }
            if (taskContext.path("sourceId").isIntegralNumber()
                    && taskContext.path("sourceId").asLong() > 0) {
                sourceIds.add(taskContext.path("sourceId").asLong());
            }
        }

        List<CaseItem> cases = loadCases(caseIds);
        List<Policy> policies = loadPolicies(policyIds);
        cases.forEach(item -> sourceIds.add(item.getSourceId()));
        policies.forEach(item -> sourceIds.add(item.getSourceId()));
        List<Source> sources = loadSources(sourceIds);

        List<PhaseThreeEvidenceBundle.EntityEvidence> caseEvidence = cases.stream()
                .map(item -> new PhaseThreeEvidenceBundle.EntityEvidence(
                        item.getId(), item.getEvidenceRevision(), hash(caseContent(item)), "published_verified"))
                .toList();
        List<PhaseThreeEvidenceBundle.EntityEvidence> policyEvidence = policies.stream()
                .map(item -> new PhaseThreeEvidenceBundle.EntityEvidence(
                        item.getId(), item.getEvidenceRevision(), hash(policyContent(item)), "published_verified"))
                .toList();
        List<PhaseThreeEvidenceBundle.SourceEvidence> sourceEvidence = sources.stream()
                .map(source -> new PhaseThreeEvidenceBundle.SourceEvidence(
                        source.getId(), source.getTitle().trim(), source.getPublisher().trim(), source.getUrl().trim(),
                        source.getEvidenceRevision(), hash(sourceContent(source)), "published_verified"))
                .toList();
        List<PhaseThreeEvidenceBundle.CaseSourceLink> caseLinks = cases.stream()
                .map(item -> new PhaseThreeEvidenceBundle.CaseSourceLink(item.getId(), item.getSourceId()))
                .toList();
        List<PhaseThreeEvidenceBundle.PolicySourceLink> policyLinks = policies.stream()
                .map(item -> new PhaseThreeEvidenceBundle.PolicySourceLink(item.getId(), item.getSourceId()))
                .toList();
        return new PhaseThreeEvidenceBundle(
                caseEvidence, policyEvidence, sourceEvidence, caseLinks, policyLinks);
    }

    private List<CaseItem> loadCases(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<CaseItem> values = caseMapper.selectBatchIds(sorted(ids));
        if (values == null || values.size() != ids.size() || values.stream().anyMatch(item -> !eligible(item))) {
            throw evidenceChanged();
        }
        return values.stream().sorted(Comparator.comparingLong(CaseItem::getId)).toList();
    }

    private List<Policy> loadPolicies(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<Policy> values = policyMapper.selectBatchIds(sorted(ids));
        if (values == null || values.size() != ids.size() || values.stream().anyMatch(item -> !eligible(item))) {
            throw evidenceChanged();
        }
        return values.stream().sorted(Comparator.comparingLong(Policy::getId)).toList();
    }

    private List<Source> loadSources(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        List<Source> values = sourceMapper.selectBatchIds(sorted(ids));
        if (values == null || values.size() != ids.size() || values.stream().anyMatch(source -> !eligible(source))) {
            throw evidenceChanged();
        }
        return values.stream().sorted(Comparator.comparingLong(Source::getId)).toList();
    }

    private boolean eligible(CaseItem item) {
        return item != null && item.getId() != null && item.getId() > 0
                && item.getSourceId() != null && item.getSourceId() > 0
                && item.getEvidenceRevision() != null && item.getEvidenceRevision() >= 0
                && "published".equals(item.getStatus()) && "verified".equals(item.getAiEvidenceStatus());
    }

    private boolean eligible(Policy item) {
        return item != null && item.getId() != null && item.getId() > 0
                && item.getSourceId() != null && item.getSourceId() > 0
                && item.getEvidenceRevision() != null && item.getEvidenceRevision() >= 0
                && "published".equals(item.getStatus()) && "verified".equals(item.getAiEvidenceStatus());
    }

    private boolean eligible(Source source) {
        if (source == null || source.getId() == null || source.getId() <= 0
                || source.getEvidenceRevision() == null || source.getEvidenceRevision() < 0
                || !"published".equals(source.getStatus()) || !"verified".equals(source.getAiEvidenceStatus())
                || !StringUtils.hasText(source.getTitle()) || !StringUtils.hasText(source.getPublisher())
                || !StringUtils.hasText(source.getUrl())) return false;
        try {
            URI uri = URI.create(source.getUrl().trim());
            return uri.getHost() != null && uri.getUserInfo() == null
                    && Set.of("http", "https").contains(uri.getScheme());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private ObjectNode caseContent(CaseItem item) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("title", item.getTitle());
        put(value, "regionId", item.getRegionId());
        value.put("category", item.getCategory());
        value.put("actorName", item.getActorName());
        put(value, "sourceId", item.getSourceId());
        value.put("summary", item.getSummary());
        value.put("businessModel", item.getBusinessModel());
        value.put("aiTools", item.getAiTools());
        value.put("outcome", item.getOutcome());
        value.put("tags", item.getTags());
        value.put("originalUrl", item.getOriginalUrl());
        value.put("accessedAt", item.getAccessedAt() == null ? null : item.getAccessedAt().toString());
        value.put("status", item.getStatus());
        value.put("aiEvidenceStatus", item.getAiEvidenceStatus());
        return value;
    }

    private ObjectNode policyContent(Policy item) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("title", item.getTitle());
        put(value, "regionId", item.getRegionId());
        value.put("issuingBody", item.getIssuingBody());
        value.put("documentNo", item.getDocumentNo());
        value.put("publishDate", item.getPublishDate() == null ? null : item.getPublishDate().toString());
        value.put("effectiveDate", item.getEffectiveDate() == null ? null : item.getEffectiveDate().toString());
        value.put("validPeriod", item.getValidPeriod());
        put(value, "sourceId", item.getSourceId());
        value.put("policyLevel", item.getPolicyLevel());
        value.put("policyType", item.getPolicyType());
        value.put("applicabilityMode", item.getApplicabilityMode());
        value.put("summary", item.getSummary());
        value.put("keyPoints", item.getKeyPoints());
        value.put("supportMeasures", item.getSupportMeasures());
        value.put("tags", item.getTags());
        value.put("originalUrl", item.getOriginalUrl());
        value.put("evidenceUrl", item.getEvidenceUrl());
        value.put("accessedAt", item.getAccessedAt() == null ? null : item.getAccessedAt().toString());
        value.put("status", item.getStatus());
        value.put("aiEvidenceStatus", item.getAiEvidenceStatus());
        return value;
    }

    private ObjectNode sourceContent(Source source) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("title", source.getTitle());
        value.put("sourceType", source.getSourceType());
        value.put("publisher", source.getPublisher());
        value.put("url", source.getUrl());
        value.put("accessedAt", source.getAccessedAt() == null ? null : source.getAccessedAt().toString());
        value.put("notes", source.getNotes());
        value.put("status", source.getStatus());
        value.put("aiEvidenceStatus", source.getAiEvidenceStatus());
        return value;
    }

    private void put(ObjectNode value, String field, Long number) {
        if (number == null) value.putNull(field);
        else value.put(field, number);
    }

    private String hash(JsonNode value) {
        try {
            byte[] serialized = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to canonicalize evidence content", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private Set<Long> positiveIds(Collection<Long> values) {
        Set<Long> result = new LinkedHashSet<>();
        if (values != null) values.stream().filter(java.util.Objects::nonNull).filter(id -> id > 0)
                .sorted().forEach(result::add);
        return result;
    }

    private List<Long> sorted(Set<Long> values) {
        return new ArrayList<>(values.stream().sorted().toList());
    }

    private AgentToolException evidenceChanged() {
        return new AgentToolException(ErrorCode.CONFLICT, "EVIDENCE_CHANGED", "研究证据已变化，请重新研究");
    }
}
