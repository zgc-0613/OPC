package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AgentResearchReport;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentResearchReportMapper extends BaseMapper<AgentResearchReport> {
    @Select("<script>SELECT * FROM ai_research_reports WHERE user_id=#{userId} AND status=#{status} <if test='query != null and query != \"\"'>AND title LIKE CONCAT('%',#{query},'%')</if> ORDER BY updated_at DESC,id DESC LIMIT #{limit}</script>")
    List<AgentResearchReport> listOwned(@Param("userId") Long userId, @Param("status") String status, @Param("query") String query, @Param("limit") int limit);

    @Select("SELECT CURRENT_TIMESTAMP(6)")
    LocalDateTime selectCurrentTimestamp();

    @Select("""
            <script>
            SELECT * FROM ai_research_reports
            WHERE user_id=#{userId} AND status=#{status} AND updated_at &lt;= #{snapshotAt}
            <if test='query != null and query != ""'>AND title LIKE CONCAT('%',#{query},'%')</if>
            <if test='beforeUpdatedAt != null and beforeId != null'>
              AND (updated_at &lt; #{beforeUpdatedAt}
                   OR (updated_at = #{beforeUpdatedAt} AND id &lt; #{beforeId}))
            </if>
            ORDER BY updated_at DESC,id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AgentResearchReport> listOwnedPage(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("query") String query,
            @Param("snapshotAt") LocalDateTime snapshotAt,
            @Param("beforeUpdatedAt") LocalDateTime beforeUpdatedAt,
            @Param("beforeId") Long beforeId,
            @Param("limit") int limit
    );
    @Select("SELECT * FROM ai_research_reports WHERE id=#{id} AND user_id=#{userId} LIMIT 1")
    AgentResearchReport selectOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_research_reports WHERE user_id=#{userId} AND idempotency_key=#{idempotencyKey} LIMIT 1")
    AgentResearchReport findByUserAndIdempotency(@Param("userId") Long userId, @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE ai_research_reports
            SET source_session_available=0, revision=revision+1, updated_at=#{now}
            WHERE session_id=#{sessionId} AND user_id=#{userId}
              AND source_session_available=1 AND status IN ('active','trash')
            """)
    int markSourceSessionUnavailable(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Update("UPDATE ai_research_reports SET title=#{title}, notes=#{notes}, revision=revision+1, updated_at=#{now} WHERE id=#{id} AND user_id=#{userId} AND status='active' AND revision=#{expectedRevision}")
    int updateMetadata(@Param("id") Long id, @Param("userId") Long userId,
                       @Param("expectedRevision") Long expectedRevision, @Param("title") String title,
                       @Param("notes") String notes, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_research_reports SET status='trash', revision=revision+1, trashed_at=#{now}, purge_after=#{purgeAfter}, updated_at=#{now} WHERE id=#{id} AND user_id=#{userId} AND status='active' AND revision=#{expectedRevision}")
    int trash(@Param("id") Long id, @Param("userId") Long userId, @Param("expectedRevision") Long expectedRevision, @Param("now") LocalDateTime now, @Param("purgeAfter") LocalDateTime purgeAfter);

    @Update("UPDATE ai_research_reports SET status='active', revision=revision+1, trashed_at=NULL, purge_after=NULL, updated_at=#{now} WHERE id=#{id} AND user_id=#{userId} AND status='trash' AND revision=#{expectedRevision}")
    int restore(@Param("id") Long id, @Param("userId") Long userId, @Param("expectedRevision") Long expectedRevision, @Param("now") LocalDateTime now);

    @Update("UPDATE ai_research_reports SET status='permanently_purged', revision=revision+1, title=NULL, notes=NULL, result_json=NULL, citation_manifest_json=NULL, evidence_version=NULL, data_version=NULL, updated_at=#{now}, purged_at=#{now} WHERE id=#{id} AND user_id=#{userId} AND status='trash' AND revision=#{expectedRevision}")
    int permanentlyPurge(@Param("id") Long id, @Param("userId") Long userId, @Param("expectedRevision") Long expectedRevision, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM ai_research_reports WHERE status='trash' AND purge_after IS NOT NULL AND purge_after <= #{now} ORDER BY purge_after,id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<AgentResearchReport> selectDueForPurge(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE ai_research_reports SET status='permanently_purged', revision=revision+1, title=NULL, notes=NULL, result_json=NULL, citation_manifest_json=NULL, evidence_version=NULL, data_version=NULL, updated_at=#{now}, purged_at=#{now} WHERE id=#{id} AND status='trash' AND revision=#{expectedRevision}")
    int permanentlyPurgeDue(@Param("id") Long id, @Param("expectedRevision") Long expectedRevision, @Param("now") LocalDateTime now);
}
