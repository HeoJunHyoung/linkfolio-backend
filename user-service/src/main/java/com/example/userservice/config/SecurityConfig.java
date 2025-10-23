package com.example.userservice.config;

import com.example.userservice.filter.CustomAuthenticationFilter;
import com.example.userservice.filter.InternalHeaderAuthenticationFilter;
import com.example.userservice.service.CustomOAuth2UserService;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter; // ✅ Jakarta EE 9+ import 확인
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 최신 방식 import
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// ✅ 아래 RequestMatcher 관련 import 확인
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // --- 의존성 주입 (기존과 동일) ---
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration_time}")
    private String expirationTime;

    // --- Bean 등록 ---

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 내부 API 인증 필터를 Bean으로 등록 (Spring이 관리하도록 함)
     */
    @Bean
    public InternalHeaderAuthenticationFilter internalHeaderAuthenticationFilter() {
        return new InternalHeaderAuthenticationFilter();
    }

    // --- RequestMatcher 정의 ---

    /**
     * 인증 없이 접근 가능한 경로(permitAll) 목록 정의
     * AntPathRequestMatcher 사용에 문제가 없다면 이대로 사용합니다.
     */
    private final RequestMatcher permitAllPaths = new OrRequestMatcher(
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-resources/**"),
            new AntPathRequestMatcher("/users/signup"),
            new AntPathRequestMatcher("/users/login"),
            new AntPathRequestMatcher("/welcome"),
            new AntPathRequestMatcher("/oauth2/**"),       // OAuth2 관련 경로
            new AntPathRequestMatcher("/login/oauth2/**")  // OAuth2 콜백 관련 경로
    );

    /**
     * InternalHeaderAuthenticationFilter를 적용해야 하는 API 경로 정의
     * (permitAllPaths에 해당하지 않는 모든 경로)
     */
    private final RequestMatcher internalApiPaths = new NegatedRequestMatcher(permitAllPaths);


    // --- SecurityFilterChain 설정 ---

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
        http
                // CSRF 비활성화 (Stateless)
                .csrf(AbstractHttpConfigurer::disable)
                // 세션 관리: Stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HTTP 요청 인가 규칙
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(permitAllPaths).permitAll() // permitAll 경로는 모두 허용
                        .anyRequest().authenticated()               // 그 외 모든 요청은 인증 필요
                );

        // OAuth2 로그인 설정 (기존과 동일)
        http.oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(auth -> auth
                        .baseUri("/oauth2/authorization")
                )
                .redirectionEndpoint(redirect -> redirect
                        .baseUri("/login/oauth2/code")
                )
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                )
                .successHandler(oAuth2LoginSuccessHandler)
        );

        // 커스텀 로그인 필터 설정 (기존과 동일)
        CustomAuthenticationFilter customAuthenticationFilter = new CustomAuthenticationFilter(
                authenticationManager,
                userService,
                objectMapper,
                jwtTokenProvider
        );
        customAuthenticationFilter.setFilterProcessesUrl("/users/login");

        // 필터 체인 구성
        http
                // 1. 커스텀 로그인 필터는 UsernamePasswordAuthenticationFilter 자리에 위치
                .addFilterAt(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 2. 내부 API 헤더 인증 필터는 RequestMatcherFilter로 감싸서,
                //    internalApiPaths에 해당하는 요청에만 적용되도록 하고,
                //    UsernamePasswordAuthenticationFilter 이전에 위치시킴
                .addFilterBefore(
                        new RequestMatcherFilter(internalApiPaths, internalHeaderAuthenticationFilter()), // ✅ Bean 주입 사용
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * 특정 RequestMatcher와 일치하는 경우에만 지정된 Filter를 실행하는 래퍼 필터.
     * SecurityConfig 내부에 private static class로 선언.
     */
    @RequiredArgsConstructor
    private static class RequestMatcherFilter extends OncePerRequestFilter {

        private final RequestMatcher requestMatcher; // internalApiPaths가 주입될 것임
        private final Filter delegate;               // internalHeaderAuthenticationFilter Bean이 주입될 것임

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            // requestMatcher(internalApiPaths)와 일치하는 요청에 대해서만 delegate(InternalHeaderAuthenticationFilter) 실행
            if (requestMatcher.matches(request)) {
                delegate.doFilter(request, response, filterChain);
            } else {
                // 일치하지 않는 요청(permitAllPaths 등)은 이 필터를 그냥 통과시킴
                filterChain.doFilter(request, response);
            }
        }
    }
}