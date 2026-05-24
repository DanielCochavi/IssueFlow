package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	@Query("""
			select auditLog
			from AuditLog auditLog
			where (:entityType is null or auditLog.entityType = :entityType)
				and (:entityId is null or auditLog.entityId = :entityId)
				and (:action is null or auditLog.action = :action)
				and (:actor is null or auditLog.actor = :actor)
			order by auditLog.timestamp desc, auditLog.id desc
			""")
	List<AuditLog> findByFilters(
			@Param("entityType") AuditEntityType entityType,
			@Param("entityId") Long entityId,
			@Param("action") AuditAction action,
			@Param("actor") AuditActor actor);
}
