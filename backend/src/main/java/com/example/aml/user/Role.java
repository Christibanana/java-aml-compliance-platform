package com.example.aml.user;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="sys_role")
public class Role extends BaseEntity {
    @Column(nullable=false, unique=true) private String code;
    @Column(nullable=false) private String name;
    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="sys_role_permission", joinColumns=@JoinColumn(name="role_id"), inverseJoinColumns=@JoinColumn(name="permission_id"))
    private Set<Permission> permissions = new HashSet<>();
    public String getCode() { return code; }
    public String getName() { return name; }
    public Set<Permission> getPermissions() { return permissions; }
}
