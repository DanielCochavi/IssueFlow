package com.att.tdp.issueflow.entity;

import java.time.Instant;

import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
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
	private AuditEntityType entityType;

	private Long entityId;

	// Store the actor id as a scalar so audit history survives user deletion.
	private Long performedBy;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuditActor actor;

	@Column(nullable = false, updatable = false)
	private Instant timestamp;

	@PrePersist
	void setTimestampOnCreate() {
		if (timestamp == null) {
			timestamp = Instant.now();
		}
	}
}
