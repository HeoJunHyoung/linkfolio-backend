package com.example.userservice.config;

import com.example.userservice.filter.CustomAuthenticationFilter;
import com.example.userservice.filter.InternalHeaderAuthenticationFilter;
import com.example.userservice.service.CustomOAuth2UserService;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final LocalLoginSuccessHandler localLoginSuccessHandler;


    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration_time}")
    private String expirationTime;

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
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        .requestMatchers("/users/signup", "/users/login", "/welcome").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                );

        // login -> users/login 으로 커스터 마이징
        CustomAuthenticationFilter authenticationFilter = new CustomAuthenticationFilter(
                authenticationManager,
                objectMapper,
                localLoginSuccessHandler // [Refactor] LocalLoginSuccessHandler 주입
        );
        authenticationFilter.setFilterProcessesUrl("/users/login");

        // OAuth2 로그인 설정
        http.oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(auth -> auth
                        .baseUri("/oauth2/authorization") // 프론트에서 호출할 인증 요청 기본 URI
                )
                .redirectionEndpoint(redirect -> redirect
                        .baseUri("/login/oauth2/code/*") // 소셜 로그인 후 콜백 URI
                )
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService) // 커스텀 유저 서비스 지정
                )
                .successHandler(oAuth2LoginSuccessHandler) // JWT 발급 핸들러 지정
        );

        // 필터 추가
        http
                .addFilter(authenticationFilter)
                .addFilterBefore(new InternalHeaderAuthenticationFilter(), AuthorizationFilter.class);
//                .addFilterBefore(new InternalHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
