package com.opc.platform.ai.mapper;

import com.opc.platform.ai.dto.EvidenceReviewQueryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface EvidenceReviewQueueMapper {

    @SelectProvider(type = EvidenceReviewQueueSqlProvider.class, method = "selectPage")
    List<EvidenceReviewQueueRow> selectPage(
            @Param("query") EvidenceReviewQueryDTO query,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @SelectProvider(type = EvidenceReviewQueueSqlProvider.class, method = "count")
    long count(@Param("query") EvidenceReviewQueryDTO query);
}
