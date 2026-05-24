package com.att.tdp.issueflow.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.AddTicketDependencyRequest;
import com.att.tdp.issueflow.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;

@Service
@Transactional(readOnly = true)
public class TicketDependencyService {

	private final TicketDependencyRepository ticketDependencyRepository;

	private final TicketRepository ticketRepository;

	private final AuditLogService auditLogService;

	public TicketDependencyService(
			TicketDependencyRepository ticketDependencyRepository,
			TicketRepository ticketRepository,
			AuditLogService auditLogService) {
		this.ticketDependencyRepository = ticketDependencyRepository;
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public void addDependency(Long ticketId, AddTicketDependencyRequest request) {
		Long blockedById = request.blockedBy();
		if (ticketId.equals(blockedById)) {
			throw new BadRequestException("Ticket cannot depend on itself");
		}

		Ticket ticket = getActiveTicket(ticketId);
		Ticket blocker = getActiveTicket(blockedById);
		validateSameProject(ticket, blocker);

		if (ticketDependencyRepository.existsByTicketIdAndBlockedById(ticketId, blockedById)) {
			throw new BadRequestException("Ticket dependency already exists");
		}
		if (ticketDependencyRepository.existsByTicketIdAndBlockedById(blockedById, ticketId)) {
			throw new BadRequestException("Circular ticket dependency is not allowed");
		}

		TicketDependency dependency = new TicketDependency();
		dependency.setTicket(ticket);
		dependency.setBlockedBy(blocker);

		TicketDependency savedDependency = ticketDependencyRepository.save(dependency);
		auditLogService.recordCurrentUserAction(
				AuditAction.ADD_DEPENDENCY,
				AuditEntityType.TICKET_DEPENDENCY,
				savedDependency.getId());
	}

	public List<TicketDependencyResponse> getDependencies(Long ticketId) {
		getActiveTicket(ticketId);
		return ticketDependencyRepository.findByTicketId(ticketId)
			.stream()
			.map(TicketDependency::getBlockedBy)
			.filter(blocker -> !blocker.isDeleted())
			.sorted(Comparator.comparing(Ticket::getId))
			.map(TicketDependencyService::toResponse)
			.toList();
	}

	@Transactional
	public void removeDependency(Long ticketId, Long blockerId) {
		getActiveTicket(ticketId);
		getRequiredTicket(blockerId);

		TicketDependency dependency = ticketDependencyRepository.findByTicketIdAndBlockedById(ticketId, blockerId)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket dependency not found"));
		Long dependencyId = dependency.getId();

		ticketDependencyRepository.delete(dependency);
		auditLogService.recordCurrentUserAction(
				AuditAction.REMOVE_DEPENDENCY,
				AuditEntityType.TICKET_DEPENDENCY,
				dependencyId);
	}

	private Ticket getActiveTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.filter(ticket -> !ticket.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private Ticket getRequiredTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private void validateSameProject(Ticket ticket, Ticket blocker) {
		// Dependency rules are project-scoped so blocker relationships do not cross project boundaries.
		if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
			throw new BadRequestException("Dependent tickets must belong to the same project");
		}
	}

	private static TicketDependencyResponse toResponse(Ticket blocker) {
		return new TicketDependencyResponse(
				blocker.getId(),
				blocker.getTitle(),
				blocker.getStatus());
	}
}
