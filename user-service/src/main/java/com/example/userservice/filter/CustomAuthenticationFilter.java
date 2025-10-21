package com.example.userservice.filter;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserLoginRequest;
import com.example.userservice.service.UserService;
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

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;

@Slf4j
public class CustomAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final UserService userService;
    private final String secret;
    private final String expirationTime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomAuthenticationFilter(AuthenticationManager authenticationManager, UserService userService,
                                      String secret, String expirationTime) {
        super(authenticationManager);
        this.userService = userService;
        this.secret = secret;
        this.expirationTime = expirationTime;
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

        String userIdentifier = ((User) authResult.getPrincipal()).getUsername(); // Principal에 사용되는 username은 email
        UserDto userDetails = userService.getUserDetailsByEmail(userIdentifier);  // UserDto는 userId가 포함된 DTO

        byte[] secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = Keys.hmacShaKeyFor(secretKeyBytes);

        long expirationTimeMillis = Long.parseLong(expirationTime);
        Date expirationDate = new Date(System.currentTimeMillis() + expirationTimeMillis);

        // JWT Subject : userId
        // JWT Claims  : email
        // 필요시 권한 정보 추가 : .claim("roles", authResult.getAuthorities())
        String token = Jwts.builder()
                .setSubject(userDetails.getId().toString())
                .claim("username", userDetails.getEmail())
                .setExpiration(expirationDate)
                .signWith(secretKey)
                .compact();

        response.addHeader("Authorization", "Bearer " + token);
        response.addHeader("userId", userDetails.getId().toString());
    }
}
