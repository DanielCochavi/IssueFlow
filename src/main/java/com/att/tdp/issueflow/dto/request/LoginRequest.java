package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@NotBlank String username,
		@NotBlank String password) {
}
