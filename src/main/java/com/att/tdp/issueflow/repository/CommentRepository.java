package com.att.tdp.issueflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	Optional<Comment> findByIdAndDeletedFalse(Long id);

	Optional<Comment> findByIdAndTicketIdAndDeletedFalse(Long id, Long ticketId);

	List<Comment> findByTicketIdAndDeletedFalseOrderByCreatedAtAsc(Long ticketId);
}
