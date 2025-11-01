package com.example.userservice.config;

import com.example.userservice.filter.InternalHeaderAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // [삭제] OAuth2, Handler, RedisRepo, AuthenticationManager 등 모든 Bean 삭제

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/welcome", "/actuator/**","/swagger-ui/**", "/v3/api-docs/**","/swagger-resources/**").permitAll()
                        .anyRequest().authenticated()
                );

        // Gateway가 주입한 헤더를 읽어 인증 객체를 만드는 필터
        http.addFilterBefore(new InternalHeaderAuthenticationFilter(), AuthorizationFilter.class);

        return http.build();
    }
}