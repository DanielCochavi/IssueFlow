package com.att.tdp.issueflow.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.request.AddCommentRequest;
import com.att.tdp.issueflow.dto.response.CommentResponse;
import com.att.tdp.issueflow.dto.response.MentionedUserResponse;
import com.att.tdp.issueflow.dto.request.UpdateCommentRequest;
import com.att.tdp.issueflow.dto.response.UserMentionsResponse;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Mention;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class CommentService {

	private static final int MAX_PAGE_SIZE = 100;

	private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_.%+-])@([A-Za-z0-9_.-]+)");

	private final CommentRepository commentRepository;

	private final MentionRepository mentionRepository;

	private final TicketRepository ticketRepository;

	private final UserRepository userRepository;

	private final AuditLogService auditLogService;

	public CommentService(
			CommentRepository commentRepository,
			MentionRepository mentionRepository,
			TicketRepository ticketRepository,
			UserRepository userRepository,
			AuditLogService auditLogService) {
		this.commentRepository = commentRepository;
		this.mentionRepository = mentionRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	public List<CommentResponse> getCommentsForTicket(Long ticketId) {
		getActiveTicket(ticketId);
		return commentRepository.findByTicketIdAndDeletedFalseOrderByCreatedAtAsc(ticketId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public CommentResponse addComment(Long ticketId, AddCommentRequest request) {
		Ticket ticket = getActiveTicket(ticketId);
		// Assignment contract: add-comment accepts authorId in the request body.
		// In production, authorship should usually come from the authenticated JWT context.
		User author = userRepository.findById(request.authorId())
			.orElseThrow(() -> new ResourceNotFoundException("Author user not found"));
		String content = normalizeContent(request.content());

		Comment comment = new Comment();
		comment.setTicket(ticket);
		comment.setAuthor(author);
		comment.setContent(content);
		comment.setDeleted(false);

		Comment savedComment = commentRepository.save(comment);
		syncMentions(savedComment);
		auditLogService.recordCurrentUserAction(AuditAction.ADD_COMMENT, AuditEntityType.COMMENT, savedComment.getId());
		return toResponse(savedComment);
	}

	@Transactional
	public void updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
		getActiveTicket(ticketId);
		Comment comment = getActiveCommentForTicket(commentId, ticketId);
		comment.setContent(normalizeContent(request.content()));
		syncMentions(comment);
		auditLogService.recordCurrentUserAction(AuditAction.UPDATE_COMMENT, AuditEntityType.COMMENT, comment.getId());
	}

	@Transactional
	public void deleteComment(Long ticketId, Long commentId) {
		getActiveTicket(ticketId);
		Comment comment = getActiveCommentForTicket(commentId, ticketId);
		comment.setDeleted(true);
		comment.setDeletedAt(Instant.now());
		mentionRepository.deleteByCommentId(commentId);
		auditLogService.recordCurrentUserAction(AuditAction.DELETE_COMMENT, AuditEntityType.COMMENT, comment.getId());
	}

	public UserMentionsResponse getMentionsForUser(Long userId, int page, int pageSize) {
		validatePagination(page, pageSize);
		userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		List<CommentResponse> responses = mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(userId)
			.stream()
			.map(Mention::getComment)
			.filter(comment -> !comment.isDeleted())
			.sorted(Comparator.comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(Comment::getId, Comparator.reverseOrder()))
			.map(this::toResponse)
			.toList();

		int total = responses.size();
		int fromIndex = Math.min((page - 1) * pageSize, total);
		int toIndex = Math.min(fromIndex + pageSize, total);
		return new UserMentionsResponse(responses.subList(fromIndex, toIndex), total, page);
	}

	private Ticket getActiveTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.filter(ticket -> !ticket.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
	}

	private Comment getActiveCommentForTicket(Long commentId, Long ticketId) {
		return commentRepository.findByIdAndTicketIdAndDeletedFalse(commentId, ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
	}

	private void syncMentions(Comment comment) {
		Set<Long> requestedUserIds = resolveMentionedUsers(comment.getContent())
			.stream()
			.map(User::getId)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		List<Mention> existingMentions = mentionRepository.findByCommentId(comment.getId());
		Map<Long, Mention> existingByUserId = existingMentions.stream()
			.collect(Collectors.toMap(mention -> mention.getMentionedUser().getId(), Function.identity()));

		for (Mention existingMention : existingMentions) {
			if (!requestedUserIds.contains(existingMention.getMentionedUser().getId())) {
				mentionRepository.delete(existingMention);
			}
		}

		for (User mentionedUser : resolveMentionedUsers(comment.getContent())) {
			if (!existingByUserId.containsKey(mentionedUser.getId())) {
				Mention mention = new Mention();
				mention.setComment(comment);
				mention.setMentionedUser(mentionedUser);
				mentionRepository.save(mention);
			}
		}
	}

	private List<User> resolveMentionedUsers(String content) {
		return extractMentionUsernames(content)
			.stream()
			.map(username -> userRepository.findByUsernameIgnoreCase(username))
			.flatMap(java.util.Optional::stream)
			.collect(Collectors.toMap(User::getId, Function.identity(), (first, ignored) -> first))
			.values()
			.stream()
			.sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static Set<String> extractMentionUsernames(String content) {
		Set<String> usernames = new LinkedHashSet<>();
		Matcher matcher = MENTION_PATTERN.matcher(content);
		while (matcher.find()) {
			// Username matching is case-insensitive so @John and @john refer to the same account.
			usernames.add(matcher.group(1).toLowerCase());
		}
		return usernames;
	}

	private CommentResponse toResponse(Comment comment) {
		List<MentionedUserResponse> mentionedUsers = mentionRepository.findByCommentId(comment.getId())
			.stream()
			.map(Mention::getMentionedUser)
			.sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
			.map(CommentService::toMentionedUserResponse)
			.toList();

		return new CommentResponse(
				comment.getId(),
				comment.getTicket().getId(),
				comment.getAuthor().getId(),
				comment.getContent(),
				mentionedUsers);
	}

	private static MentionedUserResponse toMentionedUserResponse(User user) {
		return new MentionedUserResponse(user.getId(), user.getUsername(), user.getFullName());
	}

	private static String normalizeContent(String content) {
		String normalized = content == null ? "" : content.trim();
		if (normalized.isBlank()) {
			throw new BadRequestException("Comment content must not be blank");
		}
		return normalized;
	}

	private static void validatePagination(int page, int pageSize) {
		if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new BadRequestException("Invalid pagination parameters");
		}
	}
}
