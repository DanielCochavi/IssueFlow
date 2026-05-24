package com.att.tdp.issueflow.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.repository.TicketRepository;

@Service
public class EscalationService {

	private final TicketRepository ticketRepository;

	private final AuditLogService auditLogService;

	private final Clock clock;

	private final boolean scheduledEscalationEnabled;

	public EscalationService(
			TicketRepository ticketRepository,
			AuditLogService auditLogService,
			Clock clock,
			@Value("${issueflow.escalation.enabled:true}") boolean scheduledEscalationEnabled) {
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
		this.clock = clock;
		this.scheduledEscalationEnabled = scheduledEscalationEnabled;
	}

	@Scheduled(
			initialDelayString = "${issueflow.escalation.initial-delay-ms:60000}",
			fixedDelayString = "${issueflow.escalation.fixed-delay-ms:60000}")
	@Transactional
	public void runScheduledEscalationCycle() {
		if (scheduledEscalationEnabled) {
			runEscalationCycleInternal();
		}
	}

	@Transactional
	public int runEscalationCycle() {
		return runEscalationCycleInternal();
	}

	private int runEscalationCycleInternal() {
		Instant now = clock.instant();
		int changedTickets = 0;
		for (Ticket ticket : ticketRepository.findByDueDateBeforeAndStatusNotAndDeletedFalse(now, TicketStatus.DONE)) {
			if (applyEscalation(ticket)) {
				auditLogService.recordSystemAction(AuditAction.AUTO_ESCALATE, AuditEntityType.TICKET, ticket.getId());
				changedTickets++;
			}
		}
		return changedTickets;
	}

	private static boolean applyEscalation(Ticket ticket) {
		// Each scheduler cycle applies at most one escalation step; CRITICAL only changes when overdue is first marked.
		return switch (ticket.getPriority()) {
			case LOW -> escalatePriority(ticket, TicketPriority.MEDIUM, false);
			case MEDIUM -> escalatePriority(ticket, TicketPriority.HIGH, false);
			case HIGH -> escalatePriority(ticket, TicketPriority.CRITICAL, true);
			case CRITICAL -> markCriticalOverdue(ticket);
		};
	}

	private static boolean escalatePriority(Ticket ticket, TicketPriority priority, boolean overdue) {
		ticket.setPriority(priority);
		ticket.setOverdue(overdue);
		return true;
	}

	private static boolean markCriticalOverdue(Ticket ticket) {
		if (ticket.isOverdue()) {
			return false;
		}
		ticket.setOverdue(true);
		return true;
	}
}
