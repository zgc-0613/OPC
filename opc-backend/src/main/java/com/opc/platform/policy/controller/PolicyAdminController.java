package com.opc.platform.policy.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.policy.dto.PolicyCreateDTO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public Result<PolicyDetailVO> updatePolicy(@PathVariable Long id, @Valid @RequestBody PolicyUpdateDTO dto) {
        return Result.success(policyService.updatePolicy(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deletePolicy(@PathVariable Long id) {
        policyService.deletePolicy(id);
        return Result.success();
    }
}
