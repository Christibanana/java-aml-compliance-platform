package com.example.aml.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt; private final PlatformUserDetailsService users;
    public JwtAuthenticationFilter(JwtService jwt, PlatformUserDetailsService users){this.jwt=jwt;this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String header=req.getHeader("Authorization");
        if(header!=null&&header.startsWith("Bearer ")&&SecurityContextHolder.getContext().getAuthentication()==null){
            try { String username=jwt.subject(header.substring(7)); UserDetails details=users.loadUserByUsername(username); SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,null,details.getAuthorities())); }
            catch(JwtException|UsernameNotFoundException ignored){ SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(req,res);
    }
}
