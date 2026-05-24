package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserMentionsResponse;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.service.CommentService;
import com.att.tdp.issueflow.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	private final CommentService commentService;

	public UserController(UserService userService, CommentService commentService) {
		this.userService = userService;
		this.commentService = commentService;
	}

	@GetMapping
	public List<UserResponse> getUsers() {
		return userService.getUsers();
	}

	@GetMapping("/{userId}")
	public UserResponse getUser(@PathVariable Long userId) {
		return userService.getUser(userId);
	}

	@GetMapping("/{userId}/mentions")
	public UserMentionsResponse getMentions(
			@PathVariable Long userId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return commentService.getMentionsForUser(userId, page, pageSize);
	}

	@PostMapping
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}

	@PostMapping("/update/{userId}")
	public ResponseEntity<Void> updateUser(
			@PathVariable Long userId,
			@Valid @RequestBody UpdateUserRequest request) {
		userService.updateUser(userId, request);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
		userService.deleteUser(userId);
		return ResponseEntity.ok().build();
	}
}
