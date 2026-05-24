package com.att.tdp.issueflow.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.att.tdp.issueflow.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class JwtService {

	private final SecretKey signingKey;

	private final long expirationSeconds;

	public JwtService(String secret, long expirationSeconds) {
		this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expirationSeconds = expirationSeconds;
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(expirationSeconds);

		return Jwts.builder()
			.subject(user.getUsername())
			.claim("userId", user.getId())
			.claim("role", user.getRole().name())
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiresAt))
			.signWith(signingKey)
			.compact();
	}

	public String extractUsername(String token) {
		return extractClaims(token).getSubject();
	}

	public Instant extractExpiration(String token) {
		return extractClaims(token).getExpiration().toInstant();
	}

	public boolean isTokenValid(String token) {
		try {
			return extractExpiration(token).isAfter(Instant.now());
		}
		catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}

	private Claims extractClaims(String token) {
		return Jwts.parser()
			.verifyWith(signingKey)
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}
}
