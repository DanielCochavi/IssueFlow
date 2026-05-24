package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.NotNull;

public record AddTicketDependencyRequest(
		@NotNull Long blockedBy) {
}
