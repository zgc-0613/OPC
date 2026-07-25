package com.opc.platform.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opc.platform.ai.entity.AiAgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiAgentSessionMapper extends BaseMapper<AiAgentSession> {

    @Select("SELECT * FROM ai_agent_sessions WHERE id=#{id} AND user_id=#{userId} LIMIT 1")
    AiAgentSession selectOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM ai_agent_sessions WHERE id=#{id} AND user_id=#{userId} FOR UPDATE")
    AiAgentSession selectOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE ai_agent_sessions
            SET version=version+1, last_message_at=CURRENT_TIMESTAMP(6)
            WHERE id=#{id} AND user_id=#{userId} AND status='active'
              AND deleted_at IS NULL AND purged_at IS NULL
            """)
    int touchActive(@Param("id") Long id, @Param("userId") Long userId);

    @Update("""
            UPDATE ai_agent_sessions
            SET research_context_json=#{contextJson}, version=version+1
            WHERE id=#{id} AND user_id=#{userId} AND status='active'
            """)
    int updateResearchContext(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("contextJson") String contextJson
    );

    @Update("""
            UPDATE ai_agent_sessions
            SET title=#{title}, version=version+1
            WHERE id=#{id} AND user_id=#{userId} AND title_mode='auto'
              AND deleted_at IS NULL AND purged_at IS NULL
            """)
    int applyAutomaticTitle(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("title") String title
    );

    @Select("""
            <script>
            SELECT s.*,
              (SELECT r.status FROM ai_analysis_runs r
               WHERE r.session_id=s.id AND r.task_type='agent_research'
                 AND r.status IN ('received','running')
               ORDER BY r.id DESC LIMIT 1) AS active_run_status
            FROM ai_agent_sessions s
            WHERE s.user_id=#{userId} AND s.purged_at IS NULL
            <choose>
              <when test="scope == 'trash'">AND s.deleted_at IS NOT NULL</when>
              <when test="scope == 'archived'">AND s.deleted_at IS NULL AND s.status='archived'</when>
              <otherwise>AND s.deleted_at IS NULL AND s.status='active'</otherwise>
            </choose>
            <if test="queryLike != null">
              AND (s.title LIKE #{queryLike} ESCAPE '!'
                OR EXISTS (SELECT 1 FROM ai_agent_messages m
                           WHERE m.session_id=s.id AND m.content LIKE #{queryLike} ESCAPE '!'))
            </if>
            <if test="cursorActivity != null">
              AND (
                (CASE WHEN s.pinned_at IS NULL THEN 0 ELSE 1 END) &lt; #{cursorPinned}
                OR ((CASE WHEN s.pinned_at IS NULL THEN 0 ELSE 1 END) = #{cursorPinned}
                    AND COALESCE(s.last_message_at,s.created_at) &lt; #{cursorActivity})
                OR ((CASE WHEN s.pinned_at IS NULL THEN 0 ELSE 1 END) = #{cursorPinned}
                    AND COALESCE(s.last_message_at,s.created_at) = #{cursorActivity}
                    AND s.id &lt; #{cursorId})
              )
            </if>
            ORDER BY (s.pinned_at IS NOT NULL) DESC,
                     COALESCE(s.last_message_at,s.created_at) DESC, s.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AiAgentSession> selectHistory(
            @Param("userId") Long userId,
            @Param("scope") String scope,
            @Param("queryLike") String queryLike,
            @Param("cursorPinned") Integer cursorPinned,
            @Param("cursorActivity") LocalDateTime cursorActivity,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );

    @Select("""
            SELECT * FROM ai_agent_sessions
            WHERE purged_at IS NULL AND deleted_at IS NOT NULL
              AND purge_after IS NOT NULL AND purge_after <= #{now}
            ORDER BY purge_after,id LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<AiAgentSession> selectDueForPurge(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    @Update("""
            UPDATE ai_agent_sessions
            SET title=#{title}, title_mode=#{titleMode}, profile_json=NULL,
                research_context_json=NULL, pinned_at=NULL, purged_at=#{purgedAt},
                version=version+1
            WHERE id=#{id} AND purged_at IS NULL
            """)
    int purgeSessionContent(
            @Param("id") Long id,
            @Param("title") String title,
            @Param("titleMode") String titleMode,
            @Param("purgedAt") LocalDateTime purgedAt
    );
}
