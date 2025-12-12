package com.example.communityservice.config;

import com.example.commonmodule.filter.InternalHeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                .authorizeHttpRequests(auth -> auth
                        // Swagger & Actuator
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // 테스트용 백도어 허용
                        .requestMatchers("/community/test/**").permitAll()

                        // 게시글 조회(GET)는 인증 없이 허용
                        .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()

                        // 그 외 모든 요청(작성, 수정, 삭제, 좋아요 등)은 인증 필요
                        .anyRequest().authenticated()
                )
                // Gateway에서 넘어오는 헤더(X-User-Id 등)를 기반으로 인증 처리
                .addFilterBefore(new InternalHeaderAuthenticationFilter(), AuthorizationFilter.class);

        return http.build();
    }
}