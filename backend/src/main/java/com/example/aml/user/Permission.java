package com.example.aml.user;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name="sys_permission")
public class Permission extends BaseEntity {
    @Column(nullable=false, unique=true) private String code;
    @Column(nullable=false) private String name;
    public String getCode() { return code; }
    public String getName() { return name; }
}
