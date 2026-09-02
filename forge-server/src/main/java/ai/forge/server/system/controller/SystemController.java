package ai.forge.server.system.controller;

import ai.forge.server.system.application.SystemStatusQuery;
import ai.forge.server.system.domain.SystemStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "系统", description = "公开的服务基础信息")
public class SystemController {

    /* 系统状态查询入口，确保 Controller 不直接承担领域或持久化职责。 */
    private final SystemStatusQuery systemStatusQuery;

    public SystemController(SystemStatusQuery systemStatusQuery) {
        this.systemStatusQuery = systemStatusQuery;
    }

    @GetMapping("/status")
    @Operation(summary = "查询服务状态", description = "权限：无需登录；返回 forge-server 基础运行状态。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "服务上下文可用")
    })
    public SystemStatusResponse getStatus() {
        SystemStatus status = systemStatusQuery.getStatus();
        return new SystemStatusResponse(status.application(), status.status());
    }

    public record SystemStatusResponse(
            /* 对外标识当前响应来源的应用名称。 */
            String application,
            /* 对外报告的基础运行状态。 */
            String status) {}
}
