package com.opc.platform.visit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.visit.entity.VisitLog;
import com.opc.platform.visit.vo.VisitSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import com.opc.platform.visit.vo.VisitRankingVO;
import com.opc.platform.visit.vo.VisitTrendVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VisitLogMapper extends BaseMapper<VisitLog> {

    @Select("""
            SELECT
                COUNT(*) AS totalPv,
                COUNT(DISTINCT visitor_key) AS totalUv,
                COALESCE(SUM(CASE WHEN DATE(visited_at) = CURDATE() THEN 1 ELSE 0 END), 0) AS todayPv,
                COUNT(DISTINCT CASE WHEN DATE(visited_at) = CURDATE() THEN visitor_key END) AS todayUv,
                COALESCE(SUM(CASE WHEN target_type = 'policy' THEN 1 ELSE 0 END), 0) AS policyPv,
                COALESCE(SUM(CASE WHEN target_type = 'case' THEN 1 ELSE 0 END), 0) AS casePv
            FROM visit_logs
            """)
    VisitSummaryVO selectSummary();

    @Select("""
        SELECT
            target_id AS targetId,
            MAX(page_title) AS title,
            COUNT(*) AS pv,
            COUNT(DISTINCT visitor_key) AS uv
        FROM visit_logs
        WHERE target_type = #{targetType}
          AND target_id IS NOT NULL
        GROUP BY target_id
        ORDER BY pv DESC
        LIMIT #{limit}
        """)
    List<VisitRankingVO> selectRankings(@Param("targetType") String targetType,
                                        @Param("limit") Integer limit);

    @Select("""
            SELECT
                DATE(visited_at) AS date,
                COUNT(*) AS pv,
                COUNT(DISTINCT visitor_key) AS uv
            FROM visit_logs
            WHERE visited_at >= DATE_SUB(CURDATE(), INTERVAL #{days} - 1 DAY)
            GROUP BY DATE(visited_at)
            ORDER BY date ASC
            """)
    List<VisitTrendVO> selectTrend(@Param("days") Integer days);
}