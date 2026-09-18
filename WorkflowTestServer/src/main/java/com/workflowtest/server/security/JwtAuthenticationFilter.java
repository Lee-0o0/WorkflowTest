package com.workflowtest.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import com.workflowtest.server.persistence.UserMapper;
import com.workflowtest.server.domain.Roles.SystemRole;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserMapper users;
    public JwtAuthenticationFilter(JwtService jwtService, UserMapper users) { this.jwtService = jwtService; this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String value = request.getHeader("Authorization");
        if (value != null && value.startsWith("Bearer ")) {
            try {
                CurrentUser tokenUser = jwtService.parse(value.substring(7));
                var entity = users.selectById(tokenUser.id());
                if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) throw new IllegalStateException("用户已禁用");
                CurrentUser user = new CurrentUser(entity.getId(), entity.getUsername(), entity.getDisplayName(),
                        SystemRole.valueOf(entity.getSystemRole()));
                var auth = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.systemRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
