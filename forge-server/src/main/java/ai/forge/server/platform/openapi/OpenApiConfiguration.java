package ai.forge.server.platform.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI publicOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(
                        "cookieSession",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("FORGE_SESSION"))
                .addSecuritySchemes(
                        "csrfHeader",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-TOKEN"));

        return new OpenAPI()
                .info(new Info()
                        .title("ForgeAI Public API")
                        .version("v1")
                        .description("forge-server 对外 REST 契约；仅包含 /api/v1/**。"))
                .components(components);
    }
}
