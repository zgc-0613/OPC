package com.opc.platform.policy.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.adminauth.AuthenticatedAdmin;
import com.opc.platform.policy.dto.PolicyCreateDTO;
import com.opc.platform.policy.dto.PolicyApplicabilityBatchDTO;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.dto.PolicyUpdateDTO;
import com.opc.platform.policy.service.PolicyService;
import com.opc.platform.policy.vo.PolicyDetailVO;
import com.opc.platform.policy.vo.PolicyListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

import static com.opc.platform.adminauth.AdminAuthInterceptor.AUTHENTICATED_ADMIN_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/policies")
public class PolicyAdminController {

    private final PolicyService policyService;

    @GetMapping
    public Result<List<PolicyListVO>> listPolicies(@ModelAttribute PolicyQueryDTO query) {
        return Result.success(policyService.listPolicies(query));
    }

    @GetMapping("/{id}")
    public Result<PolicyDetailVO> getPolicyDetail(@PathVariable Long id) {
        return Result.success(policyService.getPolicyDetail(id));
    }

    @PostMapping
    public Result<PolicyDetailVO> createPolicy(@Valid @RequestBody PolicyCreateDTO dto) {
        return Result.success(policyService.createPolicy(dto));
    }

    @PutMapping("/{id}")
    public Result<PolicyDetailVO> updatePolicy(
            @PathVariable Long id,
            @Valid @RequestBody PolicyUpdateDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        return Result.success(policyService.updatePolicy(id, dto, admin));
    }

    @PutMapping("/applicability/batch")
    public Result<Void> updateApplicabilityBatch(
            @Valid @RequestBody PolicyApplicabilityBatchDTO dto,
            @RequestAttribute(AUTHENTICATED_ADMIN_ATTRIBUTE) AuthenticatedAdmin admin
    ) {
        policyService.updateApplicabilityBatch(dto, admin);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePolicy(
            @PathVariable Long id,
            @RequestParam Long expectedEvidenceRevision,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedUpdatedAt
    ) {
        policyService.deletePolicy(id, expectedEvidenceRevision, expectedUpdatedAt);
        return Result.success();
    }
}
