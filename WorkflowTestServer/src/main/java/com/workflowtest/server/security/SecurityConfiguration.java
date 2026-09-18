package com.workflowtest.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwt, ObjectMapper mapper) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/login", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), Map.of("code", "UNAUTHORIZED", "message", "请先登录"));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            mapper.writeValue(response.getOutputStream(), Map.of("code", "FORBIDDEN", "message", "没有操作权限"));
                        }))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
    }
}
