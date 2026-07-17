package com.opc.platform.searchlog.controller;

import com.opc.platform.common.result.Result;
import com.opc.platform.searchlog.dto.SearchLogCreateDTO;
import com.opc.platform.searchlog.service.SearchLogService;
import com.opc.platform.searchlog.vo.SearchKeywordVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/search-logs")
@RequiredArgsConstructor
public class SearchLogController {

    private final SearchLogService searchLogService;

    @PostMapping
    public Result<Void> recordSearch(@RequestBody SearchLogCreateDTO dto, HttpServletRequest request) {
        searchLogService.recordSearch(dto, request);
        return Result.success();
    }

    @GetMapping("/hot")
    public Result<List<SearchKeywordVO>> getHotKeywords(
            @RequestParam(required = false) String searchScope,
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return Result.success(searchLogService.getHotKeywords(searchScope, limit));
    }
}
