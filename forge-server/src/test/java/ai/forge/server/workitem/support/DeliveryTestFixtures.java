package ai.forge.server.workitem.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class DeliveryTestFixtures {
    private DeliveryTestFixtures() {}

    public static void assignAllDomains(JdbcTemplate jdbc, long organizationId, long requirementId, long userId) {
        for (String role : List.of("PRODUCT", "UX", "DEVELOPER", "QA")) {
            jdbc.update("INSERT IGNORE INTO member_roles(organization_member_id,role_id,created_at) "
                    + "SELECT om.id,r.id,UTC_TIMESTAMP(6) FROM organization_members om JOIN roles r ON r.code=? "
                    + "WHERE om.organization_id=? AND om.user_id=?", role + "_ADMIN", organizationId, userId);
            jdbc.update("INSERT INTO requirement_participants(organization_id,requirement_id,role_code,user_id,assigned_by,created_at,updated_at) "
                    + "VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
                    organizationId, requirementId, role, userId, userId);
            jdbc.update("INSERT IGNORE INTO requirement_executors(organization_id,requirement_id,role_code,user_id,assigned_by,created_at) "
                    + "VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))", organizationId, requirementId, role, userId, userId);
        }
    }
}
