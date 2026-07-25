package com.opc.platform.policy.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchItemDTO;
import com.opc.platform.policy.entity.Policy;
import com.opc.platform.policy.mapper.PolicyMapper;
import com.opc.platform.policytag.mapper.PolicyTagMapper;
import com.opc.platform.policyindustrytag.mapper.PolicyIndustryTagMapper;
import com.opc.platform.policyindustrytag.entity.PolicyIndustryTag;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.region.mapper.RegionMapper;
import com.opc.platform.source.mapper.SourceMapper;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.ai.service.EvidenceReviewService;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.policy.dto.PolicyUpdateDTO;
import com.opc.platform.region.entity.Region;
import com.opc.platform.source.entity.Source;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "policy-test"),
                Policy.class
        );
    }

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private SourceMapper sourceMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private PolicyTagMapper policyTagMapper;

    @Mock
    private PolicyIndustryTagMapper policyIndustryTagMapper;

    @Mock
    private EvidenceReviewService evidenceReviewService;

    private PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(policyMapper, regionMapper, sourceMapper, tagMapper, policyTagMapper,
                policyIndustryTagMapper,
                evidenceReviewService);
    }

    @Test
    void applicabilityBatchRejectsMoreThanOneHundredPolicies() {
        PolicyApplicabilityBatchDTO dto = new PolicyApplicabilityBatchDTO();
        dto.setApplicabilityMode("general");
        List<PolicyApplicabilityBatchItemDTO> items = new ArrayList<>();
        for (long id = 1; id <= 101; id++) {
            PolicyApplicabilityBatchItemDTO item = new PolicyApplicabilityBatchItemDTO();
            item.setPolicyId(id);
            items.add(item);
        }
        dto.setItems(items);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updateApplicabilityBatch(dto, new AuthenticatedAdmin(7L, "reviewer"))
        );

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(policyMapper, never()).selectById(any());
    }

    @Test
    void publicListAlwaysReadsPublishedPolicies() {
        PolicyQueryDTO query = new PolicyQueryDTO();
        query.setStatus("draft");
        Policy published = new Policy();
        published.setId(3L);
        published.setRegionId(1L);
        published.setSourceId(2L);
        published.setStatus("published");

        when(policyMapper.selectList(argThat(wrapper -> hasParameter(wrapper, "published"))))
                .thenReturn(List.of(published));
        when(regionMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(sourceMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        var result = service.listPublicPolicies(query);

        assertEquals(1, result.size());
        assertEquals("published", result.get(0).getStatus());
    }

    @Test
    void publicDetailHidesDraftPolicies() {
        Policy draft = new Policy();
        draft.setId(11L);
        draft.setStatus("draft");
        when(policyMapper.selectById(11L)).thenReturn(draft);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.getPublicPolicyDetail(11L)
        );

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void changingVerifiedPolicyEvidenceFieldsUsesCentralInvalidationService() {
        Policy current = new Policy();
        current.setId(11L);
        current.setTitle("Old title");
        current.setRegionId(1L);
        current.setSourceId(2L);
        current.setIssuingBody("Authority");
        current.setPolicyLevel("provincial");
        current.setPolicyType("comprehensive");
        current.setSummary("Summary");
        current.setAccessedAt(LocalDate.of(2026, 7, 25));
        current.setStatus("published");
        current.setAiEvidenceStatus("verified");
        current.setEvidenceRevision(0L);
        current.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 1, 0));
        when(policyMapper.selectById(11L)).thenReturn(current);
        when(policyMapper.selectByIdForUpdate(11L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(policyMapper.updateById(current)).thenReturn(1);
        PolicyUpdateDTO dto = new PolicyUpdateDTO();
        dto.setTitle("New title");
        dto.setRegionId(1L);
        dto.setIssuingBody("Authority");
        dto.setSourceId(2L);
        dto.setPolicyLevel("provincial");
        dto.setPolicyType("comprehensive");
        dto.setSummary("Summary");
        dto.setAccessedAt(LocalDate.of(2026, 7, 25));
        dto.setStatus("published");
        dto.setExpectedEvidenceRevision(0L);
        dto.setExpectedUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 1, 0));
        AuthenticatedAdmin admin = new AuthenticatedAdmin(7L, "reviewer");

        service.updatePolicy(11L, dto, admin);

        verify(evidenceReviewService).invalidatePolicyAfterEvidenceEdit(current, admin);
        verify(policyMapper).updateById(current);
    }

    @Test
    void ordinaryPolicyUpdateReportsConflictWhenNoRowIsUpdated() {
        Policy current = editablePolicy();
        when(policyMapper.selectById(11L)).thenReturn(current);
        when(policyMapper.selectByIdForUpdate(11L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(policyMapper.updateById(any(Policy.class))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.updatePolicy(11L, updateDto(), new AuthenticatedAdmin(7L, "reviewer"))
        );

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    @Test
    void updateLocksTheSourceBeforeThePolicyRow() {
        Policy current = editablePolicy();
        when(policyMapper.selectById(11L)).thenReturn(current);
        when(policyMapper.selectByIdForUpdate(11L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(policyMapper.updateById(any(Policy.class))).thenReturn(1);

        service.updatePolicy(11L, updateDto(), new AuthenticatedAdmin(7L, "reviewer"));

        var order = inOrder(sourceMapper, policyMapper);
        order.verify(sourceMapper).selectByIdForUpdate(2L);
        order.verify(policyMapper).selectByIdForUpdate(11L);
    }

    @Test
    void updateFromEvidenceWorkbenchPreservesSpecificIndustryRelationsWhenIdsAreOmitted() {
        Policy current = editablePolicy();
        current.setApplicabilityMode("specific");
        PolicyIndustryTag relation = new PolicyIndustryTag();
        relation.setPolicyId(11L);
        relation.setIndustryTagId(703L);
        when(policyMapper.selectById(11L)).thenReturn(current);
        when(policyMapper.selectByIdForUpdate(11L)).thenReturn(current);
        when(regionMapper.selectById(1L)).thenReturn(new Region());
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(policyIndustryTagMapper.selectList(any())).thenReturn(List.of(relation));
        when(policyMapper.updateById(any(Policy.class))).thenReturn(1);
        PolicyUpdateDTO dto = updateDto();
        dto.setTitle("Policy");
        dto.setApplicabilityMode("specific");
        dto.setIndustryTagIds(null);

        service.updatePolicy(11L, dto, new AuthenticatedAdmin(7L, "reviewer"));

        verify(policyIndustryTagMapper).insert(argThat(
                (PolicyIndustryTag item) -> item.getIndustryTagId().equals(703L)
        ));
    }

    @Test
    void ordinaryPolicyDeleteReportsConflictWhenNoRowIsDeleted() {
        Policy current = editablePolicy();
        when(policyMapper.selectById(11L)).thenReturn(current);
        when(policyMapper.selectByIdForUpdate(11L)).thenReturn(current);
        when(sourceMapper.selectByIdForUpdate(2L)).thenReturn(new Source());
        when(policyMapper.deleteById(11L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deletePolicy(11L, 2L, java.time.LocalDateTime.of(2026, 7, 25, 2, 0)));

        assertEquals(ErrorCode.CONFLICT, exception.getErrorCode());
    }

    private Policy editablePolicy() {
        Policy current = new Policy();
        current.setId(11L);
        current.setTitle("Policy");
        current.setRegionId(1L);
        current.setSourceId(2L);
        current.setIssuingBody("Authority");
        current.setPolicyLevel("provincial");
        current.setPolicyType("comprehensive");
        current.setSummary("Summary");
        current.setAccessedAt(LocalDate.of(2026, 7, 25));
        current.setStatus("published");
        current.setAiEvidenceStatus("legacy_unverified");
        current.setEvidenceRevision(2L);
        current.setUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 2, 0));
        return current;
    }

    private PolicyUpdateDTO updateDto() {
        PolicyUpdateDTO dto = new PolicyUpdateDTO();
        dto.setTitle("Policy updated");
        dto.setRegionId(1L);
        dto.setIssuingBody("Authority");
        dto.setSourceId(2L);
        dto.setPolicyLevel("provincial");
        dto.setPolicyType("comprehensive");
        dto.setSummary("Summary");
        dto.setAccessedAt(LocalDate.of(2026, 7, 25));
        dto.setStatus("published");
        dto.setExpectedEvidenceRevision(2L);
        dto.setExpectedUpdatedAt(java.time.LocalDateTime.of(2026, 7, 25, 2, 0));
        return dto;
    }

    private boolean hasParameter(Wrapper<Policy> wrapper, String value) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> abstractWrapper)) {
            return false;
        }
        abstractWrapper.getSqlSegment();
        return abstractWrapper.getParamNameValuePairs().containsValue(value);
    }
}
