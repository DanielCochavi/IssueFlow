package com.att.tdp.issueflow.dto;

import java.time.Instant;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;

public record TicketResponse(
		Long id,
		String title,
		String description,
		TicketStatus status,
		TicketPriority priority,
		TicketType type,
		Long projectId,
		Long assigneeId,
		Instant dueDate,
		boolean isOverdue) {
}
