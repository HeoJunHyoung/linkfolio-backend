package com.example.chatservice.config;

import com.example.commonmodule.filter.InternalHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/ws-chat/**").permitAll() // WebSocket Handshake는 자체 인터셉터로 처리하기 때문에 permitAll 설정
                        .anyRequest().authenticated()
                )
                // HTTP 요청 헤더 인증 필터 (Gateway 신뢰)
                .addFilterBefore(new InternalHeaderAuthenticationFilter(), AuthorizationFilter.class);

        return http.build();
    }
}