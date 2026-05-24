package com.att.tdp.issueflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	Optional<Attachment> findByIdAndTicketId(Long id, Long ticketId);

	List<Attachment> findByTicketId(Long ticketId);
}
