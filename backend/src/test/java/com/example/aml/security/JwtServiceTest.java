package com.example.aml.security;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
class JwtServiceTest {
    @Test void shouldIssueAndParseToken(){JwtService service=new JwtService(new JwtProperties("test-secret-key-that-is-longer-than-thirty-two-bytes",10));String token=service.issue("analyst");assertThat(service.subject(token)).isEqualTo("analyst");}
    @Test void shouldRejectShortSecret(){assertThatThrownBy(()->new JwtService(new JwtProperties("short",10))).isInstanceOf(IllegalStateException.class);}
}
