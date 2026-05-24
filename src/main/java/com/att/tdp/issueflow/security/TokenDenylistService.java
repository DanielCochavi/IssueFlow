package com.att.tdp.issueflow.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class TokenDenylistService {

	private final Map<String, Instant> denylistedTokens = new ConcurrentHashMap<>();

	public void denylist(String token, Instant expiresAt) {
		removeExpiredTokens();
		denylistedTokens.put(token, expiresAt);
	}

	public boolean isDenylisted(String token) {
		removeExpiredTokens();
		Instant expiresAt = denylistedTokens.get(token);
		return expiresAt != null && expiresAt.isAfter(Instant.now());
	}

	private void removeExpiredTokens() {
		Instant now = Instant.now();
		denylistedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
	}
}
