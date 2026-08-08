package com.opc.platform.ai.tool;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-owned, immutable evidence material used by Phase Three results. */
public record PhaseThreeEvidenceBundle(
        List<EntityEvidence> cases,
        List<EntityEvidence> policies,
        List<SourceEvidence> sources,
        List<CaseSourceLink> caseSourceLinks,
        List<PolicySourceLink> policySourceLinks
) {
    private static final Pattern CONTENT_HASH = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final String ELIGIBLE = "published_verified";

    public PhaseThreeEvidenceBundle {
        cases = List.copyOf(cases == null ? List.of() : cases);
        policies = List.copyOf(policies == null ? List.of() : policies);
        sources = List.copyOf(sources == null ? List.of() : sources);
        caseSourceLinks = List.copyOf(caseSourceLinks == null ? List.of() : caseSourceLinks);
        policySourceLinks = List.copyOf(policySourceLinks == null ? List.of() : policySourceLinks);
        validate(cases, policies, sources, caseSourceLinks, policySourceLinks);
    }

    public static PhaseThreeEvidenceBundle empty() {
        return new PhaseThreeEvidenceBundle(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public Set<Long> caseIds() {
        return ids(cases);
    }

    public Set<Long> policyIds() {
        return ids(policies);
    }

    public Set<Long> sourceIds() {
        Set<Long> ids = new LinkedHashSet<>();
        sources.stream().map(SourceEvidence::id).sorted().forEach(ids::add);
        return Set.copyOf(ids);
    }

    public boolean isEmpty() {
        return cases.isEmpty() && policies.isEmpty() && sources.isEmpty()
                && caseSourceLinks.isEmpty() && policySourceLinks.isEmpty();
    }

    public SourceEvidence source(long sourceId) {
        return sources.stream().filter(source -> source.id() == sourceId).findFirst().orElse(null);
    }

    public boolean sourceSupportsCase(long sourceId, long caseId) {
        return caseSourceLinks.stream().anyMatch(link -> link.caseId() == caseId && link.sourceId() == sourceId);
    }

    public boolean sourceSupportsPolicy(long sourceId, long policyId) {
        return policySourceLinks.stream().anyMatch(link -> link.policyId() == policyId && link.sourceId() == sourceId);
    }

    public String canonicalJson() {
        StringBuilder value = new StringBuilder("{\"schemaVersion\":\"phase3-structured-result-v1\",\"cases\":[");
        appendEntities(value, cases);
        value.append("],\"policies\":[");
        appendEntities(value, policies);
        value.append("],\"sources\":[");
        appendSources(value, sources);
        value.append("],\"caseSourceLinks\":[");
        appendCaseLinks(value, caseSourceLinks);
        value.append("],\"policySourceLinks\":[");
        appendPolicyLinks(value, policySourceLinks);
        return value.append("]}").toString();
    }

    private static void appendEntities(StringBuilder target, List<EntityEvidence> values) {
        boolean first = true;
        for (EntityEvidence value : values.stream().sorted(java.util.Comparator.comparingLong(EntityEvidence::id)).toList()) {
            if (!first) target.append(',');
            first = false;
            target.append("{\"id\":").append(value.id())
                    .append(",\"evidenceRevision\":").append(value.evidenceRevision())
                    .append(",\"contentHash\":\"").append(value.contentHash())
                    .append("\",\"eligibility\":\"").append(value.eligibility()).append("\"}");
        }
    }

    private static void appendSources(StringBuilder target, List<SourceEvidence> values) {
        boolean first = true;
        for (SourceEvidence value : values.stream().sorted(java.util.Comparator.comparingLong(SourceEvidence::id)).toList()) {
            if (!first) target.append(',');
            first = false;
            target.append("{\"id\":").append(value.id())
                    .append(",\"evidenceRevision\":").append(value.evidenceRevision())
                    .append(",\"contentHash\":\"").append(value.contentHash())
                    .append("\",\"eligibility\":\"").append(value.eligibility()).append("\"}");
        }
    }

    private static void appendCaseLinks(StringBuilder target, List<CaseSourceLink> values) {
        boolean first = true;
        for (CaseSourceLink value : values.stream().sorted(java.util.Comparator
                .comparingLong(CaseSourceLink::caseId).thenComparingLong(CaseSourceLink::sourceId)).toList()) {
            if (!first) target.append(',');
            first = false;
            target.append("{\"caseId\":").append(value.caseId())
                    .append(",\"sourceId\":").append(value.sourceId()).append('}');
        }
    }

    private static void appendPolicyLinks(StringBuilder target, List<PolicySourceLink> values) {
        boolean first = true;
        for (PolicySourceLink value : values.stream().sorted(java.util.Comparator
                .comparingLong(PolicySourceLink::policyId).thenComparingLong(PolicySourceLink::sourceId)).toList()) {
            if (!first) target.append(',');
            first = false;
            target.append("{\"policyId\":").append(value.policyId())
                    .append(",\"sourceId\":").append(value.sourceId()).append('}');
        }
    }

    private static void validate(
            List<EntityEvidence> cases,
            List<EntityEvidence> policies,
            List<SourceEvidence> sources,
            List<CaseSourceLink> caseLinks,
            List<PolicySourceLink> policyLinks
    ) {
        if (cases.size() > 120 || policies.size() > 120 || sources.size() > 120
                || cases.size() + policies.size() > 120) {
            invalid();
        }
        Set<Long> caseIds = uniqueEntityIds(cases);
        Set<Long> policyIds = uniqueEntityIds(policies);
        Set<Long> sourceIds = uniqueSourceIds(sources);
        Set<String> casePairs = new HashSet<>();
        for (CaseSourceLink link : caseLinks) {
            if (link == null || !caseIds.contains(link.caseId()) || !sourceIds.contains(link.sourceId())
                    || !casePairs.add(link.caseId() + ":" + link.sourceId())) invalid();
        }
        Set<String> policyPairs = new HashSet<>();
        for (PolicySourceLink link : policyLinks) {
            if (link == null || !policyIds.contains(link.policyId()) || !sourceIds.contains(link.sourceId())
                    || !policyPairs.add(link.policyId() + ":" + link.sourceId())) invalid();
        }
        if (caseIds.stream().anyMatch(id -> caseLinks.stream().noneMatch(link -> link.caseId() == id))
                || policyIds.stream().anyMatch(id -> policyLinks.stream().noneMatch(link -> link.policyId() == id))) {
            invalid();
        }
    }

    private static Set<Long> uniqueEntityIds(List<EntityEvidence> values) {
        Set<Long> ids = new HashSet<>();
        for (EntityEvidence value : values) {
            if (value == null || !validEvidence(value.id(), value.evidenceRevision(),
                    value.contentHash(), value.eligibility()) || !ids.add(value.id())) invalid();
        }
        return ids;
    }

    private static Set<Long> uniqueSourceIds(List<SourceEvidence> values) {
        Set<Long> ids = new HashSet<>();
        for (SourceEvidence value : values) {
            if (value == null || !validEvidence(value.id(), value.evidenceRevision(),
                    value.contentHash(), value.eligibility()) || value.title() == null || value.title().isBlank()
                    || !safeUrl(value.url()) || !ids.add(value.id())) invalid();
        }
        return ids;
    }

    private static boolean validEvidence(long id, long revision, String hash, String eligibility) {
        return id > 0 && revision >= 0 && hash != null && CONTENT_HASH.matcher(hash).matches()
                && ELIGIBLE.equals(eligibility);
    }

    private static boolean safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && Set.of("http", "https").contains(uri.getScheme());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static Set<Long> ids(List<EntityEvidence> values) {
        Set<Long> ids = new LinkedHashSet<>();
        values.stream().map(EntityEvidence::id).sorted().forEach(ids::add);
        return Set.copyOf(ids);
    }

    private static void invalid() {
        throw new AgentToolException("EVIDENCE_MANIFEST_INVALID", "证据清单无效");
    }

    public record EntityEvidence(long id, long evidenceRevision, String contentHash, String eligibility) { }

    public record SourceEvidence(
            long id,
            String title,
            String publisher,
            String url,
            long evidenceRevision,
            String contentHash,
            String eligibility
    ) { }

    public record CaseSourceLink(long caseId, long sourceId) { }

    public record PolicySourceLink(long policyId, long sourceId) { }
}
