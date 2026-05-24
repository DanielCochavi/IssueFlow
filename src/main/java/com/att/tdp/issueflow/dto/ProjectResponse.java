package com.att.tdp.issueflow.dto;

public record ProjectResponse(
		Long id,
		String name,
		String description,
		Long ownerId) {
}
