package com.opc.platform.searchlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.searchlog.entity.SearchLog;
import com.opc.platform.searchlog.vo.SearchKeywordVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    @Select("""
            SELECT
                keyword,
                search_scope AS searchScope,
                COUNT(*) AS searchCount,
                COALESCE(SUM(result_count), 0) AS totalResultCount,
                MAX(searched_at) AS latestSearchedAt
            FROM search_logs
            WHERE (#{searchScope} IS NULL OR search_scope = #{searchScope})
            GROUP BY keyword, search_scope
            ORDER BY searchCount DESC, latestSearchedAt DESC
            LIMIT #{limit}
            """)
    List<SearchKeywordVO> selectHotKeywords(@Param("searchScope") String searchScope,
                                            @Param("limit") Integer limit);
}
