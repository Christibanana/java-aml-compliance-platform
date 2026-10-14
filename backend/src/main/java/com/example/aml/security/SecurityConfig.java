package com.example.aml.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration @EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean SecurityFilterChain security(HttpSecurity http,JwtAuthenticationFilter filter)throws Exception{
        return http.csrf(c->c.disable()).cors(c->{ }).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/api/auth/login","/actuator/health","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll().anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->r.sendError(HttpStatus.UNAUTHORIZED.value())).accessDeniedHandler((q,r,x)->r.sendError(HttpStatus.FORBIDDEN.value())))
            .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
    }
}
