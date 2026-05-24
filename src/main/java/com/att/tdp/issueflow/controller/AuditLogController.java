package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.dto.AuditLogResponse;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.service.AuditLogService;

@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

	private final AuditLogService auditLogService;

	public AuditLogController(AuditLogService auditLogService) {
		this.auditLogService = auditLogService;
	}

	@GetMapping
	public List<AuditLogResponse> getAuditLogs(
			@RequestParam(required = false) AuditEntityType entityType,
			@RequestParam(required = false) Long entityId,
			@RequestParam(required = false) AuditAction action,
			@RequestParam(required = false) AuditActor actor) {
		return auditLogService.getAuditLogs(entityType, entityId, action, actor);
	}
}
