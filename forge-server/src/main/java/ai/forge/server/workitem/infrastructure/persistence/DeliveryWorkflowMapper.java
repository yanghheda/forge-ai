package ai.forge.server.workitem.infrastructure.persistence;

import ai.forge.server.workitem.application.DeliveryWorkflowStore;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DeliveryWorkflowMapper extends DeliveryWorkflowStore {
    @Select("SELECT status FROM work_items WHERE organization_id=#{organizationId} AND id=#{requirementId} AND type='REQUIREMENT' AND deleted_at IS NULL")
    String requirementStatus(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT id FROM work_items WHERE organization_id=#{organizationId} AND parent_id=#{requirementId} AND type IN ('UX_TASK','DEV_TASK','QA_TASK','BUG') AND deleted_at IS NULL ORDER BY id")
    List<Long> taskIds(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT IF(type='REQUIREMENT',id,parent_id) FROM work_items WHERE organization_id=#{organizationId} AND id=#{workItemId} AND deleted_at IS NULL")
    Long requirementId(@Param("organizationId") long organizationId, @Param("workItemId") long workItemId);

    @Select("SELECT id FROM work_items WHERE organization_id=#{organizationId} AND id=#{requirementId} AND type='REQUIREMENT' AND deleted_at IS NULL FOR UPDATE")
    Long lockRequirement(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT EXISTS(SELECT 1 FROM requirement_participants rp JOIN organization_members om ON om.user_id=rp.user_id AND om.organization_id=#{organizationId} "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' JOIN member_roles mr ON mr.organization_member_id=om.id JOIN roles r ON r.id=mr.role_id "
            + "WHERE rp.organization_id=#{organizationId} AND rp.requirement_id=#{requirementId} AND rp.role_code=#{role} AND rp.user_id=#{userId} "
            + "AND om.status='ACTIVE' AND r.code=CONCAT(#{role},'_ADMIN'))")
    boolean isLeader(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role, @Param("userId") long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM requirement_executors re JOIN organization_members om ON om.user_id=re.user_id AND om.organization_id=#{organizationId} "
            + "JOIN users u ON u.id=om.user_id AND u.status='ACTIVE' JOIN member_roles mr ON mr.organization_member_id=om.id JOIN roles r ON r.id=mr.role_id "
            + "WHERE re.organization_id=#{organizationId} AND re.requirement_id=#{requirementId} AND re.role_code=#{role} AND re.user_id=#{userId} "
            + "AND om.status='ACTIVE' AND r.code IN (#{role},CONCAT(#{role},'_ADMIN')))")
    boolean isExecutor(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role, @Param("userId") long userId);

    @Select("SELECT user_id FROM requirement_executors WHERE organization_id=#{organizationId} AND requirement_id=#{requirementId} AND role_code=#{role} ORDER BY user_id")
    List<Long> executors(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role);

    @Select("SELECT EXISTS(SELECT 1 FROM git_repositories r JOIN gitlab_connections c ON c.id=r.connection_id "
            + "AND c.organization_id=r.organization_id AND c.status='ACTIVE' "
            + "WHERE r.organization_id=#{organizationId} AND r.status='ACTIVE')")
    boolean hasActiveRepositoryBinding(@Param("organizationId") long organizationId);

    @Delete("DELETE FROM requirement_executors WHERE organization_id=#{organizationId} AND requirement_id=#{requirementId} AND role_code=#{role}")
    void deleteExecutors(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role);

    @Insert("INSERT INTO requirement_executors(organization_id,requirement_id,role_code,user_id,assigned_by,created_at) VALUES(#{organizationId},#{requirementId},#{role},#{userId},#{actorId},UTC_TIMESTAMP(6))")
    void addExecutor(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role, @Param("userId") long userId, @Param("actorId") long actorId);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND parent_id=#{requirementId} AND type=#{type} AND deleted_at IS NULL")
    int countTasks(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND parent_id=#{requirementId} AND type=#{type} AND deleted_at IS NULL AND status!='DONE'")
    int countIncompleteTasks(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("type") String type);

    @Select("SELECT EXISTS(SELECT 1 FROM test_runs run WHERE run.organization_id=#{organizationId} AND run.requirement_id=#{requirementId} AND run.status='COMPLETED' "
            + "AND run.id=(SELECT MAX(r.id) FROM test_runs r WHERE r.organization_id=#{organizationId} AND r.requirement_id=#{requirementId}) "
            + "AND EXISTS(SELECT 1 FROM test_cases c WHERE c.organization_id=#{organizationId} AND c.work_item_id=#{requirementId} AND c.status='ACTIVE') "
            + "AND NOT EXISTS(SELECT 1 FROM test_cases c WHERE c.organization_id=#{organizationId} AND c.work_item_id=#{requirementId} AND c.status='ACTIVE' "
            + "AND NOT EXISTS(SELECT 1 FROM test_results tr WHERE tr.organization_id=#{organizationId} AND tr.test_run_id=run.id AND tr.test_case_id=c.id AND tr.status IN ('PASS','FAIL'))))")
    boolean allCasesExecuted(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT COUNT(*) FROM work_items WHERE organization_id=#{organizationId} AND parent_id=#{requirementId} AND type='BUG' AND status!='DONE' AND deleted_at IS NULL")
    int incompleteBugs(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Delete("DELETE FROM requirement_review_votes WHERE organization_id=#{organizationId} AND requirement_id=#{requirementId}")
    void clearVotes(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Insert("INSERT INTO requirement_review_votes(organization_id,requirement_id,role_code,reviewer_user_id,created_at) VALUES(#{organizationId},#{requirementId},#{role},#{userId},UTC_TIMESTAMP(6))")
    void vote(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role, @Param("userId") long userId);

    @Select("SELECT COUNT(*) FROM requirement_review_votes WHERE organization_id=#{organizationId} AND requirement_id=#{requirementId}")
    int countVotes(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);

    @Select("SELECT EXISTS(SELECT 1 FROM requirement_review_votes WHERE organization_id=#{organizationId} AND requirement_id=#{requirementId} AND role_code=#{role})")
    boolean hasVote(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId, @Param("role") String role);

    @Update("UPDATE work_items SET version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE organization_id=#{organizationId} AND id=#{requirementId} AND type='REQUIREMENT'")
    void touchRequirement(@Param("organizationId") long organizationId, @Param("requirementId") long requirementId);
}
