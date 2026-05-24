package com.att.tdp.issueflow.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
		name = "mentions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_mentions_comment_user",
				columnNames = { "comment_id", "mentioned_user_id" }))
public class Mention extends RecordBase {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "comment_id", nullable = false)
	private Comment comment;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "mentioned_user_id", nullable = false)
	private User mentionedUser;
}
