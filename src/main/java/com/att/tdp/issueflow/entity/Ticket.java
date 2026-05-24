package com.att.tdp.issueflow.entity;

import java.time.Instant;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket extends RecordBase {

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketStatus status;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketPriority priority;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TicketType type;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	private Instant dueDate;

	@Column(nullable = false)
	private boolean overdue;

	@Column(nullable = false)
	private boolean deleted;

	private Instant deletedAt;
}
