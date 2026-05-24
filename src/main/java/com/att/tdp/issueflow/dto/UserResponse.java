package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.Role;

public record UserResponse(
		Long id,
		String username,
		String email,
		String fullName,
		Role role) {
}
