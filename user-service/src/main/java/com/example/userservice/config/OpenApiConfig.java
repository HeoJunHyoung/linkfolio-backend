package com.example.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("User Service API") // API 타이틀
                .version("v1.0.0")
                .description("LinkFolio 사용자 정보 서비스 API 명세서");

        // JWT (Bearer Token) 인증을 위한 SecurityScheme 정의
        String jwtSchemeName = "Bearer Authentication";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP) // HTTP 타입
                        .scheme("bearer") // 스킴은 bearer
                        .bearerFormat("JWT")); // 베어러 포맷은 JWT

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement) // 전역적으로 SecurityRequirement 추가
                .components(components); // SecurityScheme 컴포넌트 추가
    }
}