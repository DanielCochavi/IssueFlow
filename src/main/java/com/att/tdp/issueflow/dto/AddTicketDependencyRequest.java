package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotNull;

public record AddTicketDependencyRequest(
		@NotNull Long blockedBy) {
}
