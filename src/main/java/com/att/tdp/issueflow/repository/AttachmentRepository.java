package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Attachment;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	List<Attachment> findByTicketId(Long ticketId);
}
