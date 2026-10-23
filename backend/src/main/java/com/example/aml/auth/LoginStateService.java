package com.example.aml.auth;

import com.example.aml.audit.AuditService;
import com.example.aml.user.User;
import com.example.aml.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginStateService {
    private final UserRepository users;
    private final AuditService audit;

    public LoginStateService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        if (userId == null) {
            audit.record("LOGIN", "USER", null, "FAILED", Map.of("reason", "invalid_credentials"));
            return;
        }
        User user = users.findByIdForUpdate(userId).orElseThrow();
        if (user.isEnabled() && !user.isLocked()) user.loginFailed();
        audit.record("LOGIN", "USER", userId, "FAILED", Map.of("reason", "invalid_credentials_or_locked"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<User> recordSuccess(UUID userId) {
        User user = users.findByIdForUpdate(userId).orElseThrow();
        if (!user.isEnabled() || user.isLocked()) {
            audit.record("LOGIN", "USER", userId, "FAILED", Map.of("reason", "invalid_credentials_or_locked"));
            return Optional.empty();
        }
        user.loginSucceeded();
        audit.record("LOGIN", "USER", userId, "SUCCESS", Map.of());
        return Optional.of(user);
    }
}
