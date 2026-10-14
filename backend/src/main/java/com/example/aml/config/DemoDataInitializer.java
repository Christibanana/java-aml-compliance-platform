package com.example.aml.config;

import com.example.aml.user.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.*; import org.springframework.context.annotation.Profile; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component; import org.springframework.transaction.annotation.Transactional;

@Component @Profile("dev")
public class DemoDataInitializer implements ApplicationRunner {
    private final UserRepository users; private final RoleRepository roles; private final PasswordEncoder passwords;
    @Value("${aml.demo.enabled:false}") boolean enabled; @Value("${aml.demo.username:admin}") String username; @Value("${aml.demo.password:}") String password;
    public DemoDataInitializer(UserRepository users,RoleRepository roles,PasswordEncoder passwords){this.users=users;this.roles=roles;this.passwords=passwords;}
    @Override @Transactional public void run(ApplicationArguments args){
        if(!enabled||users.findByUsername(username).isPresent())return;
        if(password==null||password.length()<10)throw new IllegalStateException("演示密码至少需要 10 个字符");
        User user=new User(username,passwords.encode(password),"本地演示管理员"); user.getRoles().add(roles.findByCode("SYSTEM_ADMIN").orElseThrow()); users.save(user);
    }
}
