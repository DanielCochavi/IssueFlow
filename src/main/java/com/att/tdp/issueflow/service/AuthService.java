package com.att.tdp.issueflow.service;

import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.AuthResponse;
import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.UnauthorizedException;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.JwtService;
import com.att.tdp.issueflow.security.TokenDenylistService;

import io.jsonwebtoken.JwtException;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final String BEARER_PREFIX = "Bearer ";

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final JwtService jwtService;

	private final TokenDenylistService tokenDenylistService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			TokenDenylistService tokenDenylistService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.tokenDenylistService = tokenDenylistService;
	}

	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByUsernameIgnoreCase(request.username())
			.orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

		if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UnauthorizedException("Invalid username or password");
		}

		return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds());
	}

	public void logout(String authorizationHeader) {
		String token = extractBearerToken(authorizationHeader);
		try {
			Instant expiresAt = jwtService.extractExpiration(token);
			tokenDenylistService.denylist(token, expiresAt);
		}
		catch (JwtException | IllegalArgumentException exception) {
			throw new UnauthorizedException("Invalid bearer token");
		}
	}

	public UserResponse currentUser(String username) {
		User user = userRepository.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
		return UserService.toResponse(user);
	}

	private String extractBearerToken(String authorizationHeader) {
		if (authorizationHeader == null || authorizationHeader.isBlank()) {
			throw new UnauthorizedException("Missing " + HttpHeaders.AUTHORIZATION + " header");
		}
		if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
			throw new UnauthorizedException("Missing bearer token");
		}
		String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
		if (token.isEmpty()) {
			throw new UnauthorizedException("Missing bearer token");
		}
		return token;
	}
}
