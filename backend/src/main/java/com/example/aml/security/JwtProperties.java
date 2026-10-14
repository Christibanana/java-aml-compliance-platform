package com.example.aml.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties("aml.jwt")
public record JwtProperties(String secret, long expirationMinutes) {}
