package com.opc.platform.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opc.platform.ai.dto.ResearchPreferenceUpdateDTO;
import com.opc.platform.ai.entity.AiResearchPreference;
import com.opc.platform.ai.mapper.AiResearchPreferenceMapper;
import com.opc.platform.ai.vo.ResearchPreferenceVO;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ResearchPreferenceService {
    private final AiResearchPreferenceMapper mapper;
    private final ObjectMapper objectMapper;

    public ResearchPreferenceVO read(AuthenticatedUser user) {
        requireUser(user);
        return toVO(mapper.selectByUserId(user.userId()));
    }

    @Transactional
    public ResearchPreferenceVO update(AuthenticatedUser user, ResearchPreferenceUpdateDTO request) {
        requireUser(user);
        if (request == null) throw new BusinessException(ErrorCode.BAD_REQUEST, "研究偏好不能为空");
        AiResearchPreference current = mapper.selectByUserId(user.userId());
        AiResearchPreference preference = current == null ? new AiResearchPreference() : current;
        preference.setUserId(user.userId());
        preference.setMemoryEnabled(Boolean.TRUE.equals(request.getMemoryEnabled()));
        preference.setCommonRegion(normalize(request.getCommonRegion(), 120));
        preference.setCommonIndustry(normalize(request.getCommonIndustry(), 120));
        preference.setTechnologyDirection(normalize(request.getTechnologyDirection(), 80));
        preference.setVentureStage(normalize(request.getVentureStage(), 80));
        preference.setBudgetRange(normalize(request.getBudgetRange(), 120));
        preference.setTeamCapabilities(normalize(request.getTeamCapabilities(), 500));
        preference.setExistingResources(normalize(request.getExistingResources(), 500));
        preference.setPolicyFocus(normalize(request.getPolicyFocus(), 500));
        if (current == null) {
            preference.setCreatedAt(LocalDateTime.now());
            mapper.insert(preference);
        } else {
            preference.setUpdatedAt(LocalDateTime.now());
            mapper.updateById(preference);
        }
        return toVO(preference);
    }

    @Transactional
    public void clear(AuthenticatedUser user) {
        requireUser(user);
        mapper.deleteByUserId(user.userId());
    }

    public JsonNode contextForResearch(AuthenticatedUser user) {
        requireUser(user);
        AiResearchPreference preference = mapper.selectByUserId(user.userId());
        if (preference == null || !Boolean.TRUE.equals(preference.getMemoryEnabled())) return null;
        ObjectNode context = objectMapper.createObjectNode();
        put(context, "commonRegion", preference.getCommonRegion());
        put(context, "commonIndustry", preference.getCommonIndustry());
        put(context, "technologyDirection", preference.getTechnologyDirection());
        put(context, "ventureStage", preference.getVentureStage());
        put(context, "budgetRange", preference.getBudgetRange());
        put(context, "teamCapabilities", preference.getTeamCapabilities());
        put(context, "existingResources", preference.getExistingResources());
        put(context, "policyFocus", preference.getPolicyFocus());
        return context;
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null || user.userId() == null) throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "研究偏好内容过长");
        }
        return normalized;
    }

    private void put(ObjectNode node, String key, String value) {
        if (StringUtils.hasText(value)) node.put(key, value);
    }

    private ResearchPreferenceVO toVO(AiResearchPreference preference) {
        if (preference == null) return null;
        return new ResearchPreferenceVO(
                preference.getUserId(), Boolean.TRUE.equals(preference.getMemoryEnabled()),
                preference.getCommonRegion(), preference.getCommonIndustry(), preference.getTechnologyDirection(),
                preference.getVentureStage(), preference.getBudgetRange(), preference.getTeamCapabilities(),
                preference.getExistingResources(), preference.getPolicyFocus(), preference.getUpdatedAt());
    }
}
