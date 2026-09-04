package ai.forge.server.agent.infrastructure.persistence;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalResumeMapper {

    @Select("SELECT id, payload FROM outbox_events WHERE event_type = 'AGENT_RUN_RESUME_REQUESTED' "
            + "AND processed_at IS NULL ORDER BY id LIMIT #{limit}")
    List<Map<String, Object>> findPending(@Param("limit") int limit);

    @Update("UPDATE outbox_events SET processed_at = UTC_TIMESTAMP(6) WHERE id = #{id} AND processed_at IS NULL")
    int markProcessed(@Param("id") long id);
}
