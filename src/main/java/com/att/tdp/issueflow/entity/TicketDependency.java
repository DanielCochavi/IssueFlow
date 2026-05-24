package com.att.tdp.issueflow.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
		name = "ticket_dependencies",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_ticket_dependencies_ticket_blocked_by",
				columnNames = { "ticket_id", "blocked_by_id" }))
public class TicketDependency extends RecordBase {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ticket_id", nullable = false)
	private Ticket ticket;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "blocked_by_id", nullable = false)
	private Ticket blockedBy;
}
