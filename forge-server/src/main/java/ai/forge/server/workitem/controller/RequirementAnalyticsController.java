package ai.forge.server.workitem.controller;

import ai.forge.server.auth.controller.AuthController;
import ai.forge.server.workitem.application.RequirementAnalyticsModels;
import ai.forge.server.workitem.application.RequirementAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Profile("!test-unit")
@RequestMapping("/api/v1/requirements/analytics")
@Tag(name = "需求", description = "实例唯一公司的需求创建、查询与协作入口")
public class RequirementAnalyticsController {

    /* 复用需求聚合查询服务；公司 scope 与读取权限全部在服务端解析。 */
    private final RequirementAnalyticsService service;

    public RequirementAnalyticsController(RequirementAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "读取全局仪表盘聚合数据",
            description = "一次返回需求指标、分布、趋势与质量风险；权限：requirement.read。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回当前公司的需求交付聚合视图"),
        @ApiResponse(responseCode = "400", description = "统计窗口天数超出允许范围")
    })
    public RequirementAnalyticsModels.Analytics analytics(
            @RequestParam(defaultValue = "30") @Min(1) @Max(RequirementAnalyticsService.MAX_RANGE_DAYS) int range,
            HttpServletRequest request) {
        return service.analytics(AuthController.requireContext(request).userId(), range);
    }
}
