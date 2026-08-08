package com.opc.platform.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseThreeEvidenceBundleTest {

    @Test
    void rejectsDuplicateCaseIds() {
        AgentToolException exception = assertThrows(AgentToolException.class, () ->
                new PhaseThreeEvidenceBundle(
                        List.of(entity(1, "a"), entity(1, "b")),
                        List.of(),
                        List.of(source(11, "a")),
                        List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11)),
                        List.of()
                ));

        assertEquals("EVIDENCE_MANIFEST_INVALID", exception.getDiagnosticCode());
    }

    @Test
    void rejectsDuplicatePolicyIds() {
        AgentToolException exception = assertThrows(AgentToolException.class, () ->
                new PhaseThreeEvidenceBundle(
                        List.of(),
                        List.of(entity(2, "a"), entity(2, "b")),
                        List.of(source(11, "a")),
                        List.of(),
                        List.of(new PhaseThreeEvidenceBundle.PolicySourceLink(2, 11))
                ));

        assertEquals("EVIDENCE_MANIFEST_INVALID", exception.getDiagnosticCode());
    }

    @Test
    void rejectsDuplicateSourceIds() {
        AgentToolException exception = assertThrows(AgentToolException.class, () ->
                new PhaseThreeEvidenceBundle(
                        List.of(entity(1, "a")),
                        List.of(),
                        List.of(source(11, "a"), source(11, "b")),
                        List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11)),
                        List.of()
                ));

        assertEquals("EVIDENCE_MANIFEST_INVALID", exception.getDiagnosticCode());
    }

    @Test
    void rejectsDuplicateCaseSourceLinks() {
        AgentToolException exception = assertThrows(AgentToolException.class, () ->
                new PhaseThreeEvidenceBundle(
                        List.of(entity(1, "a")),
                        List.of(),
                        List.of(source(11, "a")),
                        List.of(
                                new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11),
                                new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11)
                        ),
                        List.of()
                ));

        assertEquals("EVIDENCE_MANIFEST_INVALID", exception.getDiagnosticCode());
    }

    @Test
    void rejectsDuplicatePolicySourceLinks() {
        AgentToolException exception = assertThrows(AgentToolException.class, () ->
                new PhaseThreeEvidenceBundle(
                        List.of(),
                        List.of(entity(2, "a")),
                        List.of(source(11, "a")),
                        List.of(),
                        List.of(
                                new PhaseThreeEvidenceBundle.PolicySourceLink(2, 11),
                                new PhaseThreeEvidenceBundle.PolicySourceLink(2, 11)
                        )
                ));

        assertEquals("EVIDENCE_MANIFEST_INVALID", exception.getDiagnosticCode());
    }

    @Test
    void acceptsManyToManyCaseSourceLinks() {
        PhaseThreeEvidenceBundle bundle = new PhaseThreeEvidenceBundle(
                List.of(entity(1, "a"), entity(2, "b")),
                List.of(entity(3, "c")),
                List.of(source(11, "a"), source(12, "b")),
                List.of(
                        new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11),
                        new PhaseThreeEvidenceBundle.CaseSourceLink(1, 12),
                        new PhaseThreeEvidenceBundle.CaseSourceLink(2, 11)
                ),
                List.of(new PhaseThreeEvidenceBundle.PolicySourceLink(3, 11))
        );

        assertEquals(3, bundle.caseSourceLinks().size());
        assertEquals(List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11),
                        new PhaseThreeEvidenceBundle.CaseSourceLink(1, 12),
                        new PhaseThreeEvidenceBundle.CaseSourceLink(2, 11)),
                bundle.caseSourceLinks());
    }

    @Test
    void acceptsManyToManyPolicySourceLinks() {
        PhaseThreeEvidenceBundle bundle = new PhaseThreeEvidenceBundle(
                List.of(entity(1, "a")),
                List.of(entity(3, "c"), entity(4, "d")),
                List.of(source(11, "a"), source(12, "b")),
                List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1, 11)),
                List.of(
                        new PhaseThreeEvidenceBundle.PolicySourceLink(3, 11),
                        new PhaseThreeEvidenceBundle.PolicySourceLink(3, 12),
                        new PhaseThreeEvidenceBundle.PolicySourceLink(4, 12)
                )
        );

        assertEquals(3, bundle.policySourceLinks().size());
        assertEquals(List.of(new PhaseThreeEvidenceBundle.PolicySourceLink(3, 11),
                        new PhaseThreeEvidenceBundle.PolicySourceLink(3, 12),
                        new PhaseThreeEvidenceBundle.PolicySourceLink(4, 12)),
                bundle.policySourceLinks());
    }

    private PhaseThreeEvidenceBundle.EntityEvidence entity(long id, String hashSeed) {
        return new PhaseThreeEvidenceBundle.EntityEvidence(
                id, 1, "sha256:" + hashSeed.repeat(64), "published_verified");
    }

    private PhaseThreeEvidenceBundle.SourceEvidence source(long id, String hashSeed) {
        return new PhaseThreeEvidenceBundle.SourceEvidence(
                id, "Source " + id, "Publisher", "https://example.invalid/source/" + id,
                1, "sha256:" + hashSeed.repeat(64), "published_verified");
    }
}
