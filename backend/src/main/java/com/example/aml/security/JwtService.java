package com.example.aml.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;

@Service
public class JwtService {
    private final JwtProperties properties; private final SecretKey key;
    public JwtService(JwtProperties properties) {
        this.properties=properties;
        if(properties.secret()==null || properties.secret().getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("AML_JWT_SECRET 至少需要 32 字节");
        this.key=Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }
    public String issue(String username) {
        Instant now=Instant.now();
        return Jwts.builder().subject(username).issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(properties.expirationMinutes())))).signWith(key).compact();
    }
    public String subject(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}
