package ai.forge.server.authorization.infrastructure.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PermissionMapper {

    @Select("SELECT DISTINCT p.code FROM organization_members om "
            + "JOIN member_roles mr ON mr.organization_member_id = om.id "
            + "JOIN role_permissions rp ON rp.role_id = mr.role_id "
            + "JOIN permissions p ON p.id = rp.permission_id "
            + "WHERE om.organization_id = #{organizationId} AND om.user_id = #{userId} AND om.status = 'ACTIVE'")
    List<String> organizationPermissions(
            @Param("userId") long userId, @Param("organizationId") long organizationId);
}
