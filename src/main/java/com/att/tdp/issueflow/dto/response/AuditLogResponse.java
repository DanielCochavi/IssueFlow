package com.att.tdp.issueflow.dto.response;

import java.time.Instant;

import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;

public record AuditLogResponse(
		Long id,
		AuditAction action,
		AuditEntityType entityType,
		Long entityId,
		Long performedBy,
		AuditActor actor,
		Instant timestamp) {
}
