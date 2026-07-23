package com.opc.platform.policy.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.policy.dto.PolicyQueryDTO;
import com.opc.platform.policy.service.PolicyService;
import com.opc.platform.policy.vo.PolicyDetailVO;
import com.opc.platform.policy.vo.PolicyListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/policies")
public class PolicyController {

    private final PolicyService policyService;

    @GetMapping
    public Result<List<PolicyListVO>> listPolicies(@ModelAttribute PolicyQueryDTO query) {
        return Result.success(policyService.listPublicPolicies(query));
    }

    @GetMapping("/{id}")
    public Result<PolicyDetailVO> getPolicyDetail(@PathVariable Long id) {
        return Result.success(policyService.getPublicPolicyDetail(id));
    }
}
