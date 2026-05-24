package com.att.tdp.issueflow.service;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.response.AuditLogResponse;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	private final UserRepository userRepository;

	public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
		this.auditLogRepository = auditLogRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public void recordCurrentUserAction(AuditAction action, AuditEntityType entityType, Long entityId) {
		saveAuditLog(action, entityType, entityId, resolveCurrentUserId(), AuditActor.USER);
	}

	@Transactional
	public void recordUserAction(AuditAction action, AuditEntityType entityType, Long entityId, Long performedBy) {
		saveAuditLog(action, entityType, entityId, performedBy, AuditActor.USER);
	}

	@Transactional
	public void recordSystemAction(AuditAction action, AuditEntityType entityType, Long entityId) {
		saveAuditLog(action, entityType, entityId, null, AuditActor.SYSTEM);
	}

	public List<AuditLogResponse> getAuditLogs(
			AuditEntityType entityType,
			Long entityId,
			AuditAction action,
			AuditActor actor) {
		return auditLogRepository.findByFilters(entityType, entityId, action, actor)
			.stream()
			.map(AuditLogService::toResponse)
			.toList();
	}

	private void saveAuditLog(
			AuditAction action,
			AuditEntityType entityType,
			Long entityId,
			Long performedBy,
			AuditActor actor) {
		AuditLog auditLog = new AuditLog();
		auditLog.setAction(action);
		auditLog.setEntityType(entityType);
		auditLog.setEntityId(entityId);
		auditLog.setPerformedBy(performedBy);
		auditLog.setActor(actor);
		auditLogRepository.save(auditLog);
	}

	private Long resolveCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return null;
		}

		String username = authentication.getName();
		if (username == null || username.isBlank()) {
			return null;
		}

		return userRepository.findByUsernameIgnoreCase(username)
			.map(user -> user.getId())
			.orElse(null);
	}

	private static AuditLogResponse toResponse(AuditLog auditLog) {
		return new AuditLogResponse(
				auditLog.getId(),
				auditLog.getAction(),
				auditLog.getEntityType(),
				auditLog.getEntityId(),
				auditLog.getPerformedBy(),
				auditLog.getActor(),
				auditLog.getTimestamp());
	}
}
