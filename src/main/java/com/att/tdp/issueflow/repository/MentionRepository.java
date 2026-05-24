package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Mention;

public interface MentionRepository extends JpaRepository<Mention, Long> {

	List<Mention> findByCommentId(Long commentId);

	List<Mention> findByMentionedUserIdOrderByCreatedAtDesc(Long userId);

	void deleteByCommentId(Long commentId);
}
