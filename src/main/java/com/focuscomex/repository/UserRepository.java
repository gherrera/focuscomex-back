package com.focuscomex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.focuscomex.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
	List<User> findAllByOrderByUsernameAsc();
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}