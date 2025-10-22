package com.example.userservice.config;

import com.example.userservice.filter.CustomAuthenticationFilter;
import com.example.userservice.filter.InternalHeaderAuthenticationFilter;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(UserService userService, ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

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
                        .anyRequest().authenticated()
                );

        // login -> users/login 으로 커스터 마이징
        CustomAuthenticationFilter authenticationFilter = new CustomAuthenticationFilter(
                authenticationManager,
                userService,
                objectMapper,
                jwtTokenProvider
        );
        authenticationFilter.setFilterProcessesUrl("/users/login");

        // 필터 추가
        http
                .addFilter(authenticationFilter)
                .addFilterBefore(new InternalHeaderAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

}
