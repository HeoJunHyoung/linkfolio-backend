package com.example.chatservice.config;

import com.example.commonmodule.dto.security.AuthUser;
import io.swagger.v3.oas.models.Components;
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
        // WebSocket 명세를 Description에 포함
        String description = """
                LinkFolio 채팅 서비스 API 명세서
                
                Run 'WebSocket' Connection Info:
                - **Endpoint**: /ws-chat
                - **Subscribe**: /topic/chatroom/{roomId}
                - **Publish (Send)**: /app/chat/send
                - **Auth**: Header 'Authorization': 'Bearer {token}' (Handshake 시)
                """;

        Info info = new Info()
                .title("Chat Service API")
                .version("v1.0.0")
                .description(description);

        String jwtSchemeName = "BearerAuthentication";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .info(info)
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}