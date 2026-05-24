package com.att.tdp.issueflow.dto;

import java.time.Instant;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTicketRequest(
		@NotBlank String title,
		String description,
		@NotNull TicketStatus status,
		@NotNull TicketPriority priority,
		@NotNull TicketType type,
		@NotNull Long projectId,
		Long assigneeId,
		Instant dueDate) {
}
