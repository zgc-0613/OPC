package com.opc.platform.visit.service;

import com.opc.platform.visit.dto.VisitCreateDTO;
import com.opc.platform.visit.entity.VisitLog;
import com.opc.platform.visit.mapper.VisitLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import com.opc.platform.visit.vo.VisitSummaryVO;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import com.opc.platform.visit.vo.VisitRankingVO;
import com.opc.platform.visit.vo.VisitTrendVO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VisitService {

    // Mapper 是访问数据库的入口，这里用它把访问日志插入 visit_logs 表。
    private final VisitLogMapper visitLogMapper;

    // 记录一次访问。dto 保存前端传来的页面信息，request 保存浏览器这次请求的真实上下文。
    public void recordVisit(VisitCreateDTO dto, HttpServletRequest request) {
        // 先创建一个数据库实体对象，后面把字段逐个补齐。
        VisitLog visitLog = new VisitLog();

        // 这部分来自前端：用户访问了哪个页面、这个页面对应什么业务对象。
        visitLog.setPagePath(dto.getPagePath());
        visitLog.setPageTitle(dto.getPageTitle());
        visitLog.setTargetType(StringUtils.hasText(dto.getTargetType()) ? dto.getTargetType() : "other");
        visitLog.setTargetId(dto.getTargetId());
        visitLog.setReferer(StringUtils.hasText(dto.getReferer()) ? dto.getReferer() : request.getHeader("Referer"));

        // 这部分来自 HTTP 请求头：IP 和浏览器信息不让前端自己传，后端从请求中读取更可靠。
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // 后端补充访问者信息和访问时间，后面统计 PV、UV、详情页点击量会用到。
        visitLog.setIpAddress(ipAddress);
        visitLog.setUserAgent(userAgent);
        visitLog.setVisitorKey(buildVisitorKey(ipAddress, userAgent));
        visitLog.setVisitedAt(LocalDateTime.now());

        // 最后把完整的访问日志保存进数据库。
        visitLogMapper.insert(visitLog);
    }

    public VisitSummaryVO getSummary() {
        return visitLogMapper.selectSummary();
    }

    public List<VisitRankingVO> getRankings(String targetType, Integer limit) {
    String safeTargetType = StringUtils.hasText(targetType) ? targetType : "policy";
    Integer safeLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);

    return visitLogMapper.selectRankings(safeTargetType, safeLimit);
}

    public List<VisitTrendVO> getTrend(Integer days) {
        Integer safeDays = days == null || days <= 0 ? 7 : Math.min(days, 30);

        return visitLogMapper.selectTrend(safeDays);
    }


    // 获取客户端 IP。上线后请求可能经过 Nginx，所以优先读取代理转发的真实 IP。
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            // X-Forwarded-For 可能包含多个 IP，第一个通常是用户真实 IP。
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    // 用 IP + 浏览器信息生成一个访问者标识，用于粗略统计 UV。
    private String buildVisitorKey(String ipAddress, String userAgent) {
        String raw = String.valueOf(ipAddress) + "|" + String.valueOf(userAgent);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
