package com.att.tdp.issueflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
