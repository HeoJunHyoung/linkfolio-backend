package com.example.authservice.config;

import com.example.authservice.filter.CustomAuthenticationFilter;
import com.example.authservice.handler.LocalLoginSuccessHandler;
import com.example.authservice.handler.OAuth2LoginSuccessHandler;
import com.example.authservice.service.CustomOAuth2UserService;
import com.example.authservice.util.RedisBasedAuthorizationRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final LocalLoginSuccessHandler localLoginSuccessHandler;
    private final RedisBasedAuthorizationRequestRepository redisBasedAuthorizationRequestRepository;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/actuator/**").permitAll()
                        // 모든 /auth/** 경로는 인증 없이 허용
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                );

        // login -> auth/login 으로 커스터 마이징
        CustomAuthenticationFilter authenticationFilter = new CustomAuthenticationFilter(
                authenticationManager,
                objectMapper,
                localLoginSuccessHandler
        );
        authenticationFilter.setFilterProcessesUrl("/auth/login");

        // OAuth2 로그인 설정
        http.oauth2Login(oauth2 -> oauth2

                // 1. OAuth2 인증 요청 URL과 인증 요청을 저장하는 저장소 설정
                .authorizationEndpoint(auth -> auth
                        .baseUri("/oauth2/authorization")
                        .authorizationRequestRepository(redisBasedAuthorizationRequestRepository) // 인증 요청 파라미터(state 포함)를 Redis에 저장
                )

                // 2. OAuth 기반 로그인 후 돌려받는 URL 지정
                .redirectionEndpoint(redirect -> redirect
                        .baseUri("/login/oauth2/code/*")
                )

                // 3. 프로바이더에서 사용자 정보 가져오는 서비스 지정
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                )

                // 4. OAuth 로그인 성공 후 후처리
                .successHandler(oAuth2LoginSuccessHandler)
        );

        // 필터 추가
        http.addFilter(authenticationFilter);

        return http.build();
    }
}