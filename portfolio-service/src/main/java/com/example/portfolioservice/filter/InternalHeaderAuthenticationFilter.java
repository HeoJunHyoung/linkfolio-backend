package com.example.portfolioservice.filter;

import com.example.portfolioservice.dto.AuthUser;
import com.example.portfolioservice.entity.enumerate.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class InternalHeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        String email = request.getHeader("X-User-Email");
        String roleHeader = request.getHeader("X-User-Role");

        if (userId != null && !userId.isEmpty() &&
                email != null && !email.isEmpty() &&
                roleHeader != null && !roleHeader.isEmpty()) {

            try {
                Role role = Role.valueOf(roleHeader);
                UserDetails userDetails = AuthUser.fromGatewayHeader(
                        Long.parseLong(userId),
                        email,
                        role
                );

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                logger.warn("Failed to process internal headers", e);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}