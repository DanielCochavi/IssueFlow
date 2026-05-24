package com.att.tdp.issueflow.dto.request;

import java.time.Instant;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;

public record UpdateTicketRequest(
		String title,
		String description,
		TicketStatus status,
		TicketPriority priority,
		Long assigneeId,
		Instant dueDate) {
}
