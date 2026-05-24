package com.att.tdp.issueflow.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public List<UserResponse> getUsers() {
		return userRepository.findAll()
			.stream()
			.map(UserService::toResponse)
			.toList();
	}

	public UserResponse getUser(Long userId) {
		return toResponse(getRequiredUser(userId));
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		String username = normalize(request.username());
		String email = normalize(request.email());

		if (userRepository.existsByUsernameIgnoreCase(username)) {
			throw new BadRequestException("Username already exists");
		}
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new BadRequestException("Email already exists");
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(normalize(request.fullName()));
		user.setRole(request.role());
		user.setPasswordHash(passwordEncoder.encode(request.password()));

		return toResponse(userRepository.save(user));
	}

	@Transactional
	public void updateUser(Long userId, UpdateUserRequest request) {
		User user = getRequiredUser(userId);
		user.setFullName(normalize(request.fullName()));
		user.setRole(request.role());
	}

	@Transactional
	public void deleteUser(Long userId) {
		User user = getRequiredUser(userId);
		userRepository.delete(user);
	}

	public User getRequiredUserByUsername(String username) {
		return userRepository.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	public static UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getUsername(),
				user.getEmail(),
				user.getFullName(),
				user.getRole());
	}

	private User getRequiredUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
