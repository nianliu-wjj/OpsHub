package com.nianliu.opshub.bootstrap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * OpenAPI 元信息及 HTTP Basic 认证说明。
 *
 * @author nianliu-wjj
 * @since 2026-11-18
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
    @Bean
    public OpenAPI opsHubOpenApi() {
        return new OpenAPI()
                .info(new Info().title("OpsHub 接口文档").version("1.0.0")
                        .description("运维平台 REST API，使用 Result 统一响应。写请求需要 CSRF 令牌和会话 Cookie。")
                        .contact(new Contact().name("nianliu-wjj")))
                .components(new Components().addSecuritySchemes("basicAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth"));
    }
}
