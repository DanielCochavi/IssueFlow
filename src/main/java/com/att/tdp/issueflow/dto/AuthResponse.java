package com.att.tdp.issueflow.dto;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn) {
}
