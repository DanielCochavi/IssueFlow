package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.TicketDependency;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

	boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);

	List<TicketDependency> findByTicketId(Long ticketId);

	void deleteByTicketIdAndBlockedById(Long ticketId, Long blockedById);
}
