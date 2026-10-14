package com.example.aml.security;

import com.example.aml.user.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Stream;

@Service
public class PlatformUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public PlatformUserDetailsService(UserRepository users){this.users=users;}
    @Override public UserDetails loadUserByUsername(String username) {
        com.example.aml.user.User user=users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException("用户不存在"));
        var authorities=user.getRoles().stream().flatMap(r -> Stream.concat(Stream.of(new SimpleGrantedAuthority("ROLE_"+r.getCode())), r.getPermissions().stream().map(p->new SimpleGrantedAuthority(p.getCode())))).distinct().toList();
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername()).password(user.getPasswordHash()).authorities(authorities)
            .disabled(!user.isEnabled()).accountLocked(user.isLocked()).build();
    }
}
