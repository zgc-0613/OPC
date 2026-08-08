package com.opc.platform.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.opc.platform.ai.service.AnalyticsResearchStartReceipt;
import com.opc.platform.ai.service.AnalyticsResearchStartService;
import com.opc.platform.common.result.Result;
import com.opc.platform.userauth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.opc.platform.userauth.UserAuthInterceptor.AUTHENTICATED_USER_ATTRIBUTE;

/** Analytics-to-Agent bridge. The raw request is strictly validated by the service. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/research")
public class AnalyticsResearchController {

    private final AnalyticsResearchStartService analyticsResearchStartService;

    @PostMapping("/from-analytics")
    public ResponseEntity<Result<AnalyticsResearchStartReceipt>> fromAnalytics(
            @RequestBody JsonNode request,
            @RequestAttribute(AUTHENTICATED_USER_ATTRIBUTE) AuthenticatedUser user
    ) {
        return ResponseEntity.accepted().body(Result.success(analyticsResearchStartService.start(user, request)));
    }
}
