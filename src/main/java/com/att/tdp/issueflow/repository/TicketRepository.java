package com.att.tdp.issueflow.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketStatus;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

	List<Ticket> findByProjectIdAndDeletedFalse(Long projectId);

	List<Ticket> findByProjectIdAndDeletedTrue(Long projectId);

	long countByProjectIdAndAssigneeIdAndStatusNotAndDeletedFalse(
			Long projectId,
			Long assigneeId,
			TicketStatus status);

	List<Ticket> findByDueDateBeforeAndStatusNotAndDeletedFalse(Instant now, TicketStatus status);
}
