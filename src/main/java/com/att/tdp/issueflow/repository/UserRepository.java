package com.att.tdp.issueflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.Role;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsernameIgnoreCase(String username);

	boolean existsByUsernameIgnoreCase(String username);

	boolean existsByEmailIgnoreCase(String email);

	List<User> findByRoleOrderByCreatedAtAsc(Role role);
}
