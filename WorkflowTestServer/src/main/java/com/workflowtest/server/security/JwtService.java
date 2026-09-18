package com.workflowtest.server.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private final SecurityProperties properties;
    private final SecretKey key;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        byte[] source = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (source.length < 32) throw new IllegalStateException("JWT 密钥至少需要 32 字节");
        this.key = Keys.hmacShaKeyFor(source);
    }

    public String issue(CurrentUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.id()).claim("username", user.username())
                .claim("displayName", user.displayName()).claim("systemRole", user.systemRole().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(properties.tokenMinutes() * 60)))
                .signWith(key).compact();
    }

    public CurrentUser parse(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new CurrentUser(claims.getSubject(), claims.get("username", String.class),
                claims.get("displayName", String.class),
                com.workflowtest.server.domain.Roles.SystemRole.valueOf(claims.get("systemRole", String.class)));
    }
}
