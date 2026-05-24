package com.att.tdp.issueflow.entity;

import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.EntityType;

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
@Table(name = "audit_logs")
public class AuditLog extends RecordBase {

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuditAction action;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private EntityType entityType;

	private Long entityId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "performed_by_id")
	private User performedBy;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuditActor actor;

	@Column(columnDefinition = "TEXT")
	private String details;
}
