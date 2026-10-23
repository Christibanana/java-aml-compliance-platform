package com.example.aml.auth;

import com.example.aml.security.JwtService; import com.example.aml.user.*;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt; private final LoginStateService loginState;
    public AuthController(UserRepository users,PasswordEncoder passwords,JwtService jwt,LoginStateService loginState){this.users=users;this.passwords=passwords;this.jwt=jwt;this.loginState=loginState;}
    public record LoginRequest(@NotBlank String username,@NotBlank String password){}
    public record LoginResponse(String token,String tokenType,String displayName,Set<String> roles,Set<String> permissions){}
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        Optional<User> found=users.findByUsername(request.username());
        if(found.isEmpty()){
            loginState.recordFailure(null);
            throw new InvalidCredentialsException();
        }
        User user=found.get();
        if(!user.isEnabled()||user.isLocked()||!passwords.matches(request.password(),user.getPasswordHash())){
            loginState.recordFailure(user.getId());
            throw new InvalidCredentialsException();
        }
        user=loginState.recordSuccess(user.getId()).orElseThrow(InvalidCredentialsException::new);
        Set<String> roles=new TreeSet<>(), permissions=new TreeSet<>();
        user.getRoles().forEach(r->{roles.add(r.getCode());r.getPermissions().forEach(p->permissions.add(p.getCode()));});
        return new LoginResponse(jwt.issue(user.getUsername()),"Bearer",user.getDisplayName(),roles,permissions);
    }
}
