package com.example.authservice.config;

import io.swagger.v3.oas.models.Components;
import com.example.authservice.dto.AuthUser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser.class);
    }

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Auth Service API") // API 타이틀
                .version("v1.0.0")
                .description("LinkFolio 인증/인가 서비스 API 명세서");

        // JWT (Bearer Token) 인증을 위한 SecurityScheme 정의
        String jwtSchemeName = "BearerAuthentication";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP) // HTTP 타입
                        .scheme("bearer") // 스킴은 bearer
                        .bearerFormat("JWT")); // 베어러 포맷은 JWT

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement) // 전역적으로 SecurityRequirement 추가
                .components(components); // SecurityScheme 컴포넌트 추가
    }
}