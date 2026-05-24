package com.att.tdp.issueflow.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class TicketService {

	private final TicketRepository ticketRepository;

	private final ProjectRepository projectRepository;

	private final UserRepository userRepository;

	private final AuditLogService auditLogService;

	public TicketService(
			TicketRepository ticketRepository,
			ProjectRepository projectRepository,
			UserRepository userRepository,
			AuditLogService auditLogService) {
		this.ticketRepository = ticketRepository;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	public List<TicketResponse> getTicketsByProject(Long projectId) {
		getActiveProject(projectId);
		return ticketRepository.findByProjectIdAndDeletedFalse(projectId)
			.stream()
			.map(TicketService::toResponse)
			.toList();
	}

	public TicketResponse getTicket(Long ticketId) {
		return toResponse(getActiveTicket(ticketId));
	}

	@Transactional
	public TicketResponse createTicket(CreateTicketRequest request) {
		Project project = getActiveProject(request.projectId());
		User assignee = request.assigneeId() == null ? null : getRequiredAssignee(request.assigneeId());

		Ticket ticket = new Ticket();
		ticket.setTitle(normalize(request.title()));
		ticket.setDescription(normalize(request.description()));
		ticket.setStatus(request.status());
		ticket.setPriority(request.priority());
		ticket.setType(request.type());
		ticket.setProject(project);
		ticket.setAssignee(assignee);
		ticket.setDueDate(request.dueDate());
		ticket.setOverdue(false);
		ticket.setDeleted(false);

		Ticket savedTicket = ticketRepository.save(ticket);
		auditLogService.recordCurrentUserAction(AuditAction.CREATE, AuditEntityType.TICKET, savedTicket.getId());
		return toResponse(savedTicket);
	}

	@Transactional
	public void updateTicket(Long ticketId, UpdateTicketRequest request) {
		if (isEmptyUpdate(request)) {
			throw new BadRequestException("At least one ticket field must be provided");
		}

		Ticket ticket = getActiveTicket(ticketId);
		if (ticket.getStatus() == TicketStatus.DONE) {
			throw new BadRequestException("Ticket cannot be updated once it is DONE");
		}

		if (request.title() != null) {
			String title = normalize(request.title());
			if (title.isBlank()) {
				throw new BadRequestException("Ticket title must not be blank");
			}
			ticket.setTitle(title);
		}

		if (request.description() != null) {
			ticket.setDescription(normalize(request.description()));
		}

		if (request.status() != null) {
			validateStatusTransition(ticket.getStatus(), request.status(), hasNonStatusUpdate(request));
			ticket.setStatus(request.status());
		}

		if (request.priority() != null) {
			if (request.priority() != ticket.getPriority()) {
				ticket.setOverdue(false);
			}
			ticket.setPriority(request.priority());
		}

		if (request.assigneeId() != null) {
			ticket.setAssignee(getRequiredAssignee(request.assigneeId()));
		}

		if (request.dueDate() != null) {
			ticket.setDueDate(request.dueDate());
		}

		auditLogService.recordCurrentUserAction(AuditAction.UPDATE, AuditEntityType.TICKET, ticket.getId());
	}

	@Transactional
	public void deleteTicket(Long ticketId) {
		Ticket ticket = getActiveTicket(ticketId);
		ticket.setDeleted(true);
		ticket.setDeletedAt(Instant.now());
		auditLogService.recordCurrentUserAction(AuditAction.DELETE, AuditEntityType.TICKET, ticket.getId());
	}

	public List<TicketResponse> getDeletedTickets(Long projectId) {
		getRequiredProject(projectId);
		return ticketRepository.findByProjectIdAndDeletedTrue(projectId)
			.stream()
			.map(TicketService::toResponse)
			.toList();
	}

	@Transactional
	public void restoreTicket(Long ticketId) {
		Ticket ticket = getDeletedTicket(ticketId);
		ticket.setDeleted(false);
		ticket.setDeletedAt(null);
		auditLogService.recordCurrentUserAction(AuditAction.RESTORE, AuditEntityType.TICKET, ticket.getId());
	}

	private Ticket getActiveTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.filter(ticket -> !ticket.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private Ticket getDeletedTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.filter(Ticket::isDeleted)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private Project getActiveProject(Long projectId) {
		return projectRepository.findById(projectId)
			.filter(project -> !project.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private Project getRequiredProject(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private User getRequiredAssignee(Long assigneeId) {
		return userRepository.findById(assigneeId)
			.orElseThrow(() -> new ResourceNotFoundException("Assignee user not found"));
	}

	private void validateStatusTransition(
			TicketStatus current,
			TicketStatus requested,
			boolean hasNonStatusUpdate) {
		if (requested == current) {
			if (!hasNonStatusUpdate) {
				throw new BadRequestException("At least one non-status ticket field must be provided");
			}
			return;
		}

		// The assignment permits only one-step forward movement through the ticket lifecycle.
		if (expectedNextStatus(current) != requested) {
			throw new BadRequestException("Invalid ticket status transition");
		}
	}

	private TicketStatus expectedNextStatus(TicketStatus current) {
		return switch (current) {
			case TODO -> TicketStatus.IN_PROGRESS;
			case IN_PROGRESS -> TicketStatus.IN_REVIEW;
			case IN_REVIEW -> TicketStatus.DONE;
			case DONE -> null;
		};
	}

	private static boolean isEmptyUpdate(UpdateTicketRequest request) {
		return request == null
				|| (request.title() == null
				&& request.description() == null
				&& request.status() == null
				&& request.priority() == null
				&& request.assigneeId() == null
				&& request.dueDate() == null);
	}

	private static boolean hasNonStatusUpdate(UpdateTicketRequest request) {
		return request.title() != null
				|| request.description() != null
				|| request.priority() != null
				|| request.assigneeId() != null
				|| request.dueDate() != null;
	}

	private static TicketResponse toResponse(Ticket ticket) {
		return new TicketResponse(
				ticket.getId(),
				ticket.getTitle(),
				ticket.getDescription(),
				ticket.getStatus(),
				ticket.getPriority(),
				ticket.getType(),
				ticket.getProject().getId(),
				ticket.getAssignee() == null ? null : ticket.getAssignee().getId(),
				ticket.getDueDate(),
				ticket.isOverdue());
	}

	private static String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
