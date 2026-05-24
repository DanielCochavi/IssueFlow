package com.att.tdp.issueflow.dto.request;

import com.att.tdp.issueflow.enums.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
		@NotBlank String fullName,
		@NotNull Role role) {
}
