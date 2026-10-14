package com.example.aml.user;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="sys_user")
public class User extends BaseEntity {
    @Column(nullable=false, unique=true) private String username;
    @Column(name="password_hash", nullable=false) private String passwordHash;
    @Column(name="display_name", nullable=false) private String displayName;
    @Column(nullable=false) private boolean enabled = true;
    @Column(name="failed_attempts", nullable=false) private int failedAttempts;
    @Column(name="locked_until") private Instant lockedUntil;
    @Column(name="last_login_at") private Instant lastLoginAt;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="sys_user_role", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id"))
    private Set<Role> roles = new HashSet<>();
    protected User() {}
    public User(String username, String passwordHash, String displayName) { this.username=username; this.passwordHash=passwordHash; this.displayName=displayName; }
    public String getUsername(){return username;} public String getPasswordHash(){return passwordHash;} public String getDisplayName(){return displayName;}
    public boolean isEnabled(){return enabled;} public int getFailedAttempts(){return failedAttempts;} public Instant getLockedUntil(){return lockedUntil;}
    public Set<Role> getRoles(){return roles;}
    public boolean isLocked(){return lockedUntil != null && lockedUntil.isAfter(Instant.now());}
    public void loginSucceeded(){failedAttempts=0; lockedUntil=null; lastLoginAt=Instant.now();}
    public void loginFailed(){failedAttempts++; if(failedAttempts>=5) lockedUntil=Instant.now().plusSeconds(15*60);}
}
