package com.example.portfolioservice.config;

import com.example.commonmodule.filter.InternalHeaderAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // Swagger UI 및 Actuator 허용
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()

                        // 인증 필요 API
                        .requestMatchers(HttpMethod.GET, "/me").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/me/likes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/portfolios/{portfolioId:\\d+}/like").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/portfolios/{portfolioId:\\d+}/like").authenticated()

                        // 메인 페이지 목록 및 상세 조회는 인증 없이 허용
                        .requestMatchers(HttpMethod.GET, "/portfolios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/portfolios/{userId:\\d+}").permitAll() // like가 아닌 숫자 ID만 허용

                        // 그 외 모든 요청은 기본적으로 인증 필요
                        .anyRequest().authenticated()
                );

        // Gateway가 주입한 헤더를 읽어 인증 객체를 만드는 필터
        http.addFilterBefore(new InternalHeaderAuthenticationFilter(), AuthorizationFilter.class);

        return http.build();
    }
}