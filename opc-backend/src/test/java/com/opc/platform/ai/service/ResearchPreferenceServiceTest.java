package com.opc.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opc.platform.ai.dto.ResearchPreferenceUpdateDTO;
import com.opc.platform.ai.entity.AiResearchPreference;
import com.opc.platform.ai.mapper.AiResearchPreferenceMapper;
import com.opc.platform.ai.vo.ResearchPreferenceVO;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchPreferenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiResearchPreferenceMapper mapper = mock(AiResearchPreferenceMapper.class);
    private final ResearchPreferenceService service = new ResearchPreferenceService(mapper, objectMapper);
    private final AuthenticatedUser user = new AuthenticatedUser(41L, "researcher", "researcher@example.test");

    @Test
    void createsOnlyExplicitPreferencesAndKeepsMemoryDisabledUntilOptedIn() {
        ResearchPreferenceUpdateDTO request = new ResearchPreferenceUpdateDTO();
        request.setMemoryEnabled(false);
        request.setCommonRegion("上海");
        request.setCommonIndustry("企业服务");
        request.setVentureStage("validation");
        when(mapper.selectByUserId(41L)).thenReturn(null);

        ResearchPreferenceVO result = service.update(user, request);

        ArgumentCaptor<AiResearchPreference> captured = ArgumentCaptor.forClass(AiResearchPreference.class);
        verify(mapper).insert(captured.capture());
        assertEquals(41L, captured.getValue().getUserId());
        assertFalse(captured.getValue().getMemoryEnabled());
        assertEquals("上海", result.commonRegion());
        assertNull(service.contextForResearch(user));
    }

    @Test
    void returnsAControlledContextOnlyAfterTheUserEnablesMemory() throws Exception {
        AiResearchPreference preference = new AiResearchPreference();
        preference.setUserId(41L);
        preference.setMemoryEnabled(true);
        preference.setCommonRegion("上海");
        preference.setCommonIndustry("企业服务");
        preference.setVentureStage("validation");
        when(mapper.selectByUserId(41L)).thenReturn(preference);

        JsonNode context = service.contextForResearch(user);

        assertEquals("上海", context.path("commonRegion").asText());
        assertEquals("企业服务", context.path("commonIndustry").asText());
        assertEquals("validation", context.path("ventureStage").asText());
    }

    @Test
    void rejectsOversizedOrUnknownPreferenceValuesAndAllowsTheUserToDeleteTheirRecord() {
        ResearchPreferenceUpdateDTO invalid = new ResearchPreferenceUpdateDTO();
        invalid.setMemoryEnabled(true);
        invalid.setCommonRegion("x".repeat(121));
        assertThrows(BusinessException.class, () -> service.update(user, invalid));

        service.clear(user);
        verify(mapper).deleteByUserId(41L);
    }
}
