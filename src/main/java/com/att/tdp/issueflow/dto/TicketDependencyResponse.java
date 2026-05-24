package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.TicketStatus;

public record TicketDependencyResponse(
		Long id,
		String title,
		TicketStatus status) {
}
