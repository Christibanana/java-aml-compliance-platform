package com.example.aml.auth;

import com.example.aml.audit.AuditService; import com.example.aml.security.JwtService; import com.example.aml.user.*;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt; private final AuditService audit;
    public AuthController(UserRepository users,PasswordEncoder passwords,JwtService jwt,AuditService audit){this.users=users;this.passwords=passwords;this.jwt=jwt;this.audit=audit;}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record LoginResponse(String token,String tokenType,String displayName,Set<String> roles,Set<String> permissions){}
    @PostMapping("/login") @Transactional
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        User user=users.findByUsername(request.username()).orElseThrow(()->new IllegalArgumentException("用户名或密码错误"));
        if(!user.isEnabled()||user.isLocked()||!passwords.matches(request.password(),user.getPasswordHash())){
            user.loginFailed(); audit.record("LOGIN", "USER", user.getId(), "FAILED", Map.of("reason","bad_credentials_or_locked")); throw new IllegalArgumentException("用户名或密码错误，连续失败 5 次将锁定 15 分钟");
        }
        user.loginSucceeded(); audit.record("LOGIN","USER",user.getId(),"SUCCESS",Map.of());
        Set<String> roles=new TreeSet<>(), permissions=new TreeSet<>();
        user.getRoles().forEach(r->{roles.add(r.getCode());r.getPermissions().forEach(p->permissions.add(p.getCode()));});
        return new LoginResponse(jwt.issue(user.getUsername()),"Bearer",user.getDisplayName(),roles,permissions);
    }
}
