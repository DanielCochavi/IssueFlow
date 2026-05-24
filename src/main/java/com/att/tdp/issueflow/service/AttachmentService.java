package com.att.tdp.issueflow.service;

import java.io.IOException;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.dto.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;

@Service
@Transactional(readOnly = true)
public class AttachmentService {

	private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
			"image/png",
			"image/jpeg",
			"application/pdf",
			"text/plain");

	private final AttachmentRepository attachmentRepository;

	private final TicketRepository ticketRepository;

	private final AuditLogService auditLogService;

	public AttachmentService(
			AttachmentRepository attachmentRepository,
			TicketRepository ticketRepository,
			AuditLogService auditLogService) {
		this.attachmentRepository = attachmentRepository;
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file) {
		Ticket ticket = getActiveTicket(ticketId);
		validateFile(file);

		Attachment attachment = new Attachment();
		attachment.setTicket(ticket);
		attachment.setFilename(resolveFilename(file.getOriginalFilename()));
		attachment.setContentType(file.getContentType());
		attachment.setSizeBytes(file.getSize());
		attachment.setData(readFileBytes(file));

		Attachment savedAttachment = attachmentRepository.save(attachment);
		auditLogService.recordCurrentUserAction(
				AuditAction.ADD_ATTACHMENT,
				AuditEntityType.ATTACHMENT,
				savedAttachment.getId());
		return toResponse(savedAttachment);
	}

	@Transactional
	public void deleteAttachment(Long ticketId, Long attachmentId) {
		getActiveTicket(ticketId);
		Attachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
		Long deletedAttachmentId = attachment.getId();

		attachmentRepository.delete(attachment);
		auditLogService.recordCurrentUserAction(
				AuditAction.DELETE_ATTACHMENT,
				AuditEntityType.ATTACHMENT,
				deletedAttachmentId);
	}

	private Ticket getActiveTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.filter(ticket -> !ticket.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private static void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Attachment file is required");
		}
		// The assignment limits uploads to 10 MB and a small fixed allow-list of content types.
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new BadRequestException("Attachment file exceeds 10 MB");
		}
		if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new BadRequestException("Attachment content type is not allowed");
		}
	}

	private static byte[] readFileBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new BadRequestException("Attachment file is required");
		}
	}

	private static String resolveFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			return "attachment";
		}
		return originalFilename.trim();
	}

	private static AttachmentResponse toResponse(Attachment attachment) {
		return new AttachmentResponse(
				attachment.getId(),
				attachment.getTicket().getId(),
				attachment.getFilename(),
				attachment.getContentType());
	}
}
