package ai.forge.server.auth.controller;

import ai.forge.server.auth.application.CompanyLogoStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test-unit")
@RequestMapping("/api/v1/branding")
public class CompanyLogoController {

    /* 公司品牌图片的后端本地文件存储。 */
    private final CompanyLogoStorage logoStorage;

    public CompanyLogoController(CompanyLogoStorage logoStorage) {
        this.logoStorage = logoStorage;
    }

    @GetMapping(value = "/company-logo", produces = "image/webp")
    @Operation(summary = "读取公司 Logo", description = "公开读取初始化时保存的公司 WebP Logo。")
    @ApiResponse(responseCode = "200", description = "返回公司 WebP Logo")
    public ResponseEntity<Resource> logo() {
        Resource logo = logoStorage.load();
        if (!logo.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .contentType(MediaType.parseMediaType("image/webp"))
                .body(logo);
    }
}
