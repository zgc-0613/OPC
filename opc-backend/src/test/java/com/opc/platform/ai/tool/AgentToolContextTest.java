package com.opc.platform.ai.tool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentToolContextTest {

    @Test
    void emptyAuthorizedEvidenceUsesTheFrozenCanonicalEvidenceVersion() {
        AgentToolContext context = new AgentToolContext(91L, 42L);

        assertEquals(
                "sha256:d93e8851c631b2eca793eeda59b20eff593db61e95168526bed0f9b9ee2f58df",
                context.evidenceVersion()
        );
    }

    @Test
    void authorizedEvidenceUsesEntityRevisionContentHashAndLinksInTheFrozenOrder() {
        AgentToolContext context = new AgentToolContext(91L, 42L);
        context.installEvidenceBundle(new PhaseThreeEvidenceBundle(
                List.of(new PhaseThreeEvidenceBundle.EntityEvidence(
                        1001L, 1L,
                        "sha256:6eabaecbec85a148bba05cdbecfb71ee5d9dd3bf6efd8dae8ea3f320dfecd440",
                        "published_verified"
                )),
                List.of(),
                List.of(new PhaseThreeEvidenceBundle.SourceEvidence(
                        9001L, "契约占位来源 A", "契约发布者",
                        "https://example.invalid/source/9001", 1L,
                        "sha256:87ed45da273c371046ee83570ada5a605bc5f311514595497c1628bdbee3cac2",
                        "published_verified"
                )),
                List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9001L)),
                List.of()
        ));

        assertEquals(
                "sha256:9d7a818ff4f57a7dec741fb8d58f4686f7f2b2ccce3604971bd3318689430ee2",
                context.evidenceVersion()
        );
    }

    @Test
    void evidenceVersionChangesWhenRevisionContentHashOrProvenanceLinkChanges() {
        AgentToolContext context = new AgentToolContext(91L, 42L);
        PhaseThreeEvidenceBundle.SourceEvidence sourceA = new PhaseThreeEvidenceBundle.SourceEvidence(
                9001L, "Source A", "Publisher", "https://example.invalid/source/9001", 1L,
                "sha256:" + "a".repeat(64), "published_verified");
        PhaseThreeEvidenceBundle.SourceEvidence sourceB = new PhaseThreeEvidenceBundle.SourceEvidence(
                9002L, "Source B", "Publisher", "https://example.invalid/source/9002", 1L,
                "sha256:" + "b".repeat(64), "published_verified");
        PhaseThreeEvidenceBundle.EntityEvidence caseA = new PhaseThreeEvidenceBundle.EntityEvidence(
                1001L, 1L, "sha256:" + "c".repeat(64), "published_verified");
        PhaseThreeEvidenceBundle.EntityEvidence caseRevision = new PhaseThreeEvidenceBundle.EntityEvidence(
                1001L, 2L, "sha256:" + "c".repeat(64), "published_verified");
        PhaseThreeEvidenceBundle.EntityEvidence caseHash = new PhaseThreeEvidenceBundle.EntityEvidence(
                1001L, 1L, "sha256:" + "d".repeat(64), "published_verified");

        context.installEvidenceBundle(bundle(caseA, sourceA,
                new PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9001L)));
        String baseline = context.evidenceVersion();

        context.installEvidenceBundle(bundle(caseRevision, sourceA,
                new PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9001L)));
        String revisionVersion = context.evidenceVersion();
        assertNotEquals(baseline, revisionVersion);

        context.installEvidenceBundle(bundle(caseHash, sourceA,
                new PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9001L)));
        String hashVersion = context.evidenceVersion();
        assertNotEquals(revisionVersion, hashVersion);

        context.installEvidenceBundle(new PhaseThreeEvidenceBundle(
                List.of(caseA), List.of(), List.of(sourceA, sourceB),
                List.of(new PhaseThreeEvidenceBundle.CaseSourceLink(1001L, 9002L)), List.of()));
        assertNotEquals(hashVersion, context.evidenceVersion());
    }

    private PhaseThreeEvidenceBundle bundle(
            PhaseThreeEvidenceBundle.EntityEvidence caseEvidence,
            PhaseThreeEvidenceBundle.SourceEvidence sourceEvidence,
            PhaseThreeEvidenceBundle.CaseSourceLink link
    ) {
        return new PhaseThreeEvidenceBundle(
                List.of(caseEvidence), List.of(), List.of(sourceEvidence), List.of(link), List.of());
    }
}
