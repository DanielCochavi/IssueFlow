package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.dto.request.AddCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.service.CommentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController {

	private final CommentService commentService;

	public CommentController(CommentService commentService) {
		this.commentService = commentService;
	}

	@GetMapping
	public List<CommentResponse> getComments(@PathVariable Long ticketId) {
		return commentService.getCommentsForTicket(ticketId);
	}

	@PostMapping
	public CommentResponse addComment(
			@PathVariable Long ticketId,
			@Valid @RequestBody AddCommentRequest request) {
		return commentService.addComment(ticketId, request);
	}

	@PatchMapping("/{commentId}")
	public ResponseEntity<Void> updateComment(
			@PathVariable Long ticketId,
			@PathVariable Long commentId,
			@Valid @RequestBody UpdateCommentRequest request) {
		commentService.updateComment(ticketId, commentId, request);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{commentId}")
	public ResponseEntity<Void> deleteComment(
			@PathVariable Long ticketId,
			@PathVariable Long commentId) {
		commentService.deleteComment(ticketId, commentId);
		return ResponseEntity.ok().build();
	}
}
