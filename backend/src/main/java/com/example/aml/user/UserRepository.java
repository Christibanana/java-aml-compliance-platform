package com.example.aml.user;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<User, UUID> { Optional<User> findByUsername(String username); }
