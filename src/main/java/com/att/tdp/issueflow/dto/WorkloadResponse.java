package com.att.tdp.issueflow.dto;

public record WorkloadResponse(
		Long userId,
		String username,
		Long openTicketCount) {
}
