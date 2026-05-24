package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	List<Comment> findByTicketIdAndDeletedFalseOrderByCreatedAtAsc(Long ticketId);
}
