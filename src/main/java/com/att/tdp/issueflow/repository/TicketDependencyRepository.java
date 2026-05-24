package com.att.tdp.issueflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.enums.TicketStatus;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

	boolean existsByTicketIdAndBlockedById(Long ticketId, Long blockedById);

	Optional<TicketDependency> findByTicketIdAndBlockedById(Long ticketId, Long blockedById);

	List<TicketDependency> findByTicketId(Long ticketId);

	void deleteByTicketIdAndBlockedById(Long ticketId, Long blockedById);

	@Query("""
			select count(dependency) > 0
			from TicketDependency dependency
			where dependency.ticket.id = :ticketId
				and dependency.blockedBy.deleted = false
				and dependency.blockedBy.status <> :resolvedStatus
			""")
	boolean existsUnresolvedBlocker(Long ticketId, TicketStatus resolvedStatus);
}
