package ai.forge.server.conversation.infrastructure.persistence;

import ai.forge.server.conversation.application.ConversationStore;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ConversationMapper extends ConversationStore {
    @Insert("INSERT INTO agent_conversations(organization_id,user_id,title,created_at,updated_at,version) "
            + "VALUES(#{organizationId},#{userId},#{title},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)")
    int insertConversation(@Param("organizationId") long organizationId,@Param("userId") long userId,@Param("title") String title);
    @Select("SELECT LAST_INSERT_ID()") long lastInsertId();
    @Select("SELECT id,title,created_at,updated_at,version FROM agent_conversations WHERE organization_id=#{organizationId} "
            + "AND user_id=#{userId} ORDER BY updated_at DESC,id DESC")
    List<Map<String,Object>> conversations(@Param("organizationId") long organizationId,@Param("userId") long userId);
    @Select("SELECT COUNT(*) FROM agent_conversations WHERE id=#{id} AND organization_id=#{organizationId} AND user_id=#{userId}")
    int owns(@Param("organizationId") long organizationId,@Param("userId") long userId,@Param("id") long id);
    @Select("SELECT COALESCE(" +
            "(SELECT ar.work_item_id FROM agent_messages m JOIN agent_runs ar ON ar.id=m.run_id " +
            "WHERE m.conversation_id=#{id} AND ar.organization_id=#{organizationId} " +
            "AND ar.user_id=#{userId} AND ar.work_item_id IS NOT NULL ORDER BY m.created_at DESC LIMIT 1)," +
            "(SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(tc.result_json,'$.id')) AS UNSIGNED) " +
            "FROM agent_messages m JOIN agent_runs ar ON ar.id=m.run_id " +
            "JOIN agent_tool_calls tc ON tc.run_id=ar.id AND tc.organization_id=#{organizationId} " +
            "WHERE m.conversation_id=#{id} AND ar.organization_id=#{organizationId} AND ar.user_id=#{userId} " +
            "AND tc.tool_name='create_requirement' AND tc.status='SUCCEEDED' " +
            "ORDER BY tc.created_at DESC LIMIT 1))")
    Long requirementId(@Param("organizationId") long organizationId,
            @Param("userId") long userId,@Param("id") long id);
    @Insert("INSERT INTO agent_messages(conversation_id,sender,body,run_id,created_at) "
            + "VALUES(#{conversationId},'USER',#{body},#{runId},UTC_TIMESTAMP(6))")
    int insertUserMessage(@Param("conversationId") long conversationId,@Param("body") String body,@Param("runId") String runId);
    @Update("UPDATE agent_conversations SET updated_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=#{id}")
    int touch(@Param("id") long id);
    @Select("SELECT sender,body,run_id,created_at FROM agent_messages WHERE conversation_id=#{id} UNION ALL "
            + "SELECT 'AGENT',COALESCE(s.output_summary,ar.error_code,'Agent run completed'),ar.id,"
            + "COALESCE(ar.finished_at,ar.created_at) FROM agent_messages m JOIN agent_runs ar ON ar.id=m.run_id "
            + "LEFT JOIN agent_steps s ON s.run_id=ar.id AND s.type='FINAL' WHERE m.conversation_id=#{id} "
            + "AND m.sender='USER' AND ar.status IN ('SUCCEEDED','FAILED','CANCELLED') ORDER BY created_at")
    List<Map<String,Object>> messages(@Param("id") long id);
}
