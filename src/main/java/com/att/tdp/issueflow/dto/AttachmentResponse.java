package com.att.tdp.issueflow.dto;

public record AttachmentResponse(
		Long id,
		Long ticketId,
		String filename,
		String contentType) {
}
