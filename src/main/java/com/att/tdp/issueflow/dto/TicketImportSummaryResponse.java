package com.att.tdp.issueflow.dto;

import java.util.List;

public record TicketImportSummaryResponse(
		int created,
		int failed,
		List<String> errors) {
}
