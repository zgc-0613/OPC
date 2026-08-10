package com.opc.platform.ai.service;

import com.opc.platform.caseitem.entity.CaseItem;
import com.opc.platform.caseitem.mapper.CaseItemMapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.source.entity.Source;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Set;

/**
 * Locks and validates explicit Phase Three evidence choices inside the start
 * transaction. This intentionally validates only user-selected IDs; tool
 * discoveries continue through the existing run-local allowlist.
 */
@Service
public class PhaseThreeSelectedEvidenceValidator {

    private final CaseItemMapper caseMapper;
    private final SourceMapper sourceMapper;
    private final TagMapper tagMapper;

    public PhaseThreeSelectedEvidenceValidator(CaseItemMapper caseMapper, SourceMapper sourceMapper) {
        this(caseMapper, sourceMapper, null);
    }

    @Autowired
    public PhaseThreeSelectedEvidenceValidator(
            CaseItemMapper caseMapper, SourceMapper sourceMapper, TagMapper tagMapper) {
        this.caseMapper = caseMapper;
        this.sourceMapper = sourceMapper;
        this.tagMapper = tagMapper;
    }

    public void validate(PhaseThreeTaskContext taskContext) {
        if (taskContext == null) return;

        for (var caseId : taskContext.node().path("caseIds")) {
            validateCase(caseId.asLong());
        }
        if (taskContext.node().path("sourceId").isIntegralNumber()) {
            validateSelectedSource(taskContext.node().path("sourceId").asLong());
        }
        if (taskContext.node().path("technologyTagId").isIntegralNumber()) {
            validateTechnologyTag(taskContext.node().path("technologyTagId").asLong());
        }
    }

    private void validateCase(long caseId) {
        CaseItem caseItem = caseMapper.selectByIdForUpdate(caseId);
        if (!eligibleCase(caseItem)) {
            throw caseIneligible();
        }
        Source source = sourceMapper.selectByIdForUpdate(caseItem.getSourceId());
        if (!eligibleSource(source)) {
            throw caseIneligible();
        }
    }

    private void validateSelectedSource(long sourceId) {
        if (!eligibleSource(sourceMapper.selectByIdForUpdate(sourceId))) {
            throw sourceIneligible();
        }
    }

    private void validateTechnologyTag(long tagId) {
        Tag tag = tagMapper == null ? null : tagMapper.selectById(tagId);
        if (tag == null || !"technology".equals(tag.getTagType()) || !StringUtils.hasText(tag.getName())) {
            throw technologyTagIneligible();
        }
    }

    private boolean eligibleCase(CaseItem item) {
        return item != null && item.getSourceId() != null && item.getSourceId() > 0
                && "published".equals(item.getStatus())
                && "verified".equals(item.getAiEvidenceStatus())
                && item.getEvidenceRevision() != null;
    }

    private boolean eligibleSource(Source source) {
        if (source == null || !"published".equals(source.getStatus())
                || !"verified".equals(source.getAiEvidenceStatus())
                || source.getEvidenceRevision() == null
                || !StringUtils.hasText(source.getTitle())
                || !StringUtils.hasText(source.getPublisher())
                || !StringUtils.hasText(source.getUrl())) {
            return false;
        }
        try {
            URI uri = URI.create(source.getUrl().trim());
            return uri.getHost() != null && uri.getUserInfo() == null
                    && Set.of("http", "https").contains(uri.getScheme().toLowerCase());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private BusinessException caseIneligible() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "PHASE3_CASE_NOT_ELIGIBLE");
    }

    private BusinessException sourceIneligible() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "PHASE3_SOURCE_NOT_ELIGIBLE");
    }

    private BusinessException technologyTagIneligible() {
        return new BusinessException(ErrorCode.BAD_REQUEST, "PHASE3_TECHNOLOGY_TAG_NOT_ELIGIBLE");
    }
}
