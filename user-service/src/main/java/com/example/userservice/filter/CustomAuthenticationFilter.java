package com.example.userservice.filter;

import com.example.userservice.dto.AuthUser;
import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserLoginRequest;
import com.example.userservice.service.UserService;
import com.example.userservice.util.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.util.ArrayList;

@Slf4j
public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager, UserService userService,
                                      ObjectMapper objectMapper, JwtTokenProvider jwtTokenProvider) {
        super(authenticationManager);
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 로그인 요청 시, UsernamePasswordAuthenticationFilter가 요청을 가로채서 attemptAuthentication() 메서드 실행
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {

            UserLoginRequest creds = objectMapper.readValue(request.getInputStream(), UserLoginRequest.class);

            return getAuthenticationManager()
                    .authenticate(
                            new UsernamePasswordAuthenticationToken(creds.getEmail(), creds.getPassword(), new ArrayList<>())
                    );
        } catch (IOException e) {
            log.error("Authentication failed during request parsing", e);
            throw new RuntimeException("Authentication failed", e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain chain,
                                            Authentication authResult) throws IOException, ServletException {

        String userIdentifier = ((AuthUser) authResult.getPrincipal()).getUsername(); // Principal에 사용되는 username은 email
        UserDto userDetails = userService.getUserDetailsByEmail(userIdentifier);  // UserDto는 userId가 포함된 DTO

        String token = jwtTokenProvider.generateToken(userDetails);

        response.addHeader("Authorization", "Bearer " + token);
    }
}
