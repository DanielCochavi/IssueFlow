package com.att.tdp.issueflow.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.request.CreateUserRequest;
import com.att.tdp.issueflow.dto.request.UpdateUserRequest;
import com.att.tdp.issueflow.dto.response.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

	private static final String REFERENCED_USER_DELETE_MESSAGE =
			"User cannot be deleted because it is referenced by existing projects, tickets, comments, or mentions";

	private final UserRepository userRepository;

	private final ProjectRepository projectRepository;

	private final TicketRepository ticketRepository;

	private final CommentRepository commentRepository;

	private final MentionRepository mentionRepository;

	private final PasswordEncoder passwordEncoder;

	private final AuditLogService auditLogService;

	public UserService(
			UserRepository userRepository,
			ProjectRepository projectRepository,
			TicketRepository ticketRepository,
			CommentRepository commentRepository,
			MentionRepository mentionRepository,
			PasswordEncoder passwordEncoder,
			AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.ticketRepository = ticketRepository;
		this.commentRepository = commentRepository;
		this.mentionRepository = mentionRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditLogService = auditLogService;
	}

	public List<UserResponse> getUsers() {
		return userRepository.findAll()
			.stream()
			.map(UserService::toResponse)
			.toList();
	}

	public UserResponse getUser(Long userId) {
		return toResponse(getRequiredUser(userId));
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		String username = normalize(request.username());
		String email = normalize(request.email());

		if (userRepository.existsByUsernameIgnoreCase(username)) {
			throw new BadRequestException("Username already exists");
		}
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new BadRequestException("Email already exists");
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(normalize(request.fullName()));
		user.setRole(request.role());
		// User creation accepts a password so /auth/login can validate credentials; only the BCrypt hash is stored.
		user.setPasswordHash(passwordEncoder.encode(request.password()));

		User savedUser = userRepository.save(user);
		auditLogService.recordUserAction(
				AuditAction.CREATE,
				AuditEntityType.USER,
				savedUser.getId(),
				savedUser.getId());
		return toResponse(savedUser);
	}

	@Transactional
	public void updateUser(Long userId, UpdateUserRequest request) {
		User user = getRequiredUser(userId);
		user.setFullName(normalize(request.fullName()));
		user.setRole(request.role());
		auditLogService.recordCurrentUserAction(AuditAction.UPDATE, AuditEntityType.USER, user.getId());
	}

	@Transactional
	public void deleteUser(Long userId) {
		User user = getRequiredUser(userId);
		validateUserCanBeDeleted(user.getId());
		auditLogService.recordCurrentUserAction(AuditAction.DELETE, AuditEntityType.USER, user.getId());
		userRepository.delete(user);
	}

	public User getRequiredUserByUsername(String username) {
		return userRepository.findByUsernameIgnoreCase(username)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	public static UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getUsername(),
				user.getEmail(),
				user.getFullName(),
				user.getRole());
	}

	private User getRequiredUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private void validateUserCanBeDeleted(Long userId) {
		// Business assumption: users are hard-deleted only when they are not referenced by business records.
		// Referenced users are kept to preserve project, ticket, comment, and mention history.
		if (projectRepository.existsByOwnerId(userId)
				|| ticketRepository.existsByAssigneeId(userId)
				|| commentRepository.existsByAuthorId(userId)
				|| mentionRepository.existsByMentionedUserId(userId)) {
			throw new BadRequestException(REFERENCED_USER_DELETE_MESSAGE);
		}
	}

	private String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
