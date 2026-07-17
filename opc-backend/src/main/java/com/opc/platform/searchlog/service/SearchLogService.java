package com.opc.platform.searchlog.service;

import com.opc.platform.searchlog.dto.SearchLogCreateDTO;
import com.opc.platform.searchlog.entity.SearchLog;
import com.opc.platform.searchlog.mapper.SearchLogMapper;
import com.opc.platform.searchlog.vo.SearchKeywordVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogMapper searchLogMapper;

    public void recordSearch(SearchLogCreateDTO dto, HttpServletRequest request) {
        String keyword = normalizeKeyword(dto.getKeyword());
        if (keyword.length() < 2) {
            return;
        }

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        SearchLog searchLog = new SearchLog();
        searchLog.setKeyword(keyword);
        searchLog.setSearchScope(StringUtils.hasText(dto.getSearchScope()) ? dto.getSearchScope() : "all");
        searchLog.setResultCount(dto.getResultCount() == null ? 0 : Math.max(dto.getResultCount(), 0));
        searchLog.setPagePath(dto.getPagePath());
        searchLog.setIpAddress(ipAddress);
        searchLog.setUserAgent(userAgent);
        searchLog.setVisitorKey(buildVisitorKey(ipAddress, userAgent));
        searchLog.setSearchedAt(LocalDateTime.now());

        searchLogMapper.insert(searchLog);
    }

    public List<SearchKeywordVO> getHotKeywords(String searchScope, Integer limit) {
        String safeScope = StringUtils.hasText(searchScope) ? searchScope : null;
        Integer safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);
        return searchLogMapper.selectHotKeywords(safeScope, safeLimit);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return keyword.trim().replaceAll("\\s+", " ");
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String buildVisitorKey(String ipAddress, String userAgent) {
        String raw = String.valueOf(ipAddress) + "|" + String.valueOf(userAgent);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
