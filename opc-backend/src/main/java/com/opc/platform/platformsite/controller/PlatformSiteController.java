package com.opc.platform.platformsite.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.platformsite.service.PlatformSiteService;
import com.opc.platform.platformsite.vo.PlatformSiteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/platform-sites")
public class PlatformSiteController {

    private final PlatformSiteService platformSiteService;

    @GetMapping
    public Result<List<PlatformSiteVO>> listSites() {
        return Result.success(platformSiteService.listPublishedSites());
    }
}
