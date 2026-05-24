package com.att.tdp.issueflow.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.dto.response.AttachmentResponse;
import com.att.tdp.issueflow.service.AttachmentService;

@RestController
@RequestMapping("/tickets/{ticketId}/attachments")
public class AttachmentController {

	private final AttachmentService attachmentService;

	public AttachmentController(AttachmentService attachmentService) {
		this.attachmentService = attachmentService;
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AttachmentResponse uploadAttachment(
			@PathVariable Long ticketId,
			@RequestParam(name = "file", required = false) MultipartFile file) {
		return attachmentService.uploadAttachment(ticketId, file);
	}

	@DeleteMapping("/{attachmentId}")
	public ResponseEntity<Void> deleteAttachment(
			@PathVariable Long ticketId,
			@PathVariable Long attachmentId) {
		attachmentService.deleteAttachment(ticketId, attachmentId);
		return ResponseEntity.ok().build();
	}
}
