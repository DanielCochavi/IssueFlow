package com.att.tdp.issueflow;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CommentIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private MentionRepository mentionRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private TicketDependencyRepository ticketDependencyRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void clearData() {
		auditLogRepository.deleteAll();
		attachmentRepository.deleteAll();
		mentionRepository.deleteAll();
		commentRepository.deleteAll();
		ticketDependencyRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void commentAndMentionEndpointsRequireJwt() throws Exception {
		mockMvc.perform(get("/tickets/{ticketId}/comments", 1L))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/tickets/{ticketId}/comments", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(1L, "Hello"))))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", 1L, 2L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Updated"))))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", 1L, 2L))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/users/{userId}/mentions", 1L))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedUserCanAddAndListCommentsInCreatedOrder() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("commentorder", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Comment Order Project", "Commented Ticket");
		Long firstCommentId = createComment(author.token(), ticketId, author.id(), " First comment ");
		Long secondCommentId = createComment(author.token(), ticketId, author.id(), "Second comment");

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].id").value(firstCommentId.intValue()))
			.andExpect(jsonPath("$[0].ticketId").value(ticketId.intValue()))
			.andExpect(jsonPath("$[0].authorId").value(author.id().intValue()))
			.andExpect(jsonPath("$[0].content").value("First comment"))
			.andExpect(jsonPath("$[0].mentionedUsers").isEmpty())
			.andExpect(jsonPath("$[1].id").value(secondCommentId.intValue()))
			.andExpect(jsonPath("$[1].content").value("Second comment"));
	}

	@Test
	void addCommentValidatesTicketAuthorAndContent() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("commentvalidation", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Comment Validation Project", "Validation Ticket");

		mockMvc.perform(post("/tickets/{ticketId}/comments", 999999L)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(author.id(), "Missing ticket"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		mockMvc.perform(delete("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(author.id(), "Deleted ticket"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		Long activeTicketId = createTicketForUser(author, "Active Comment Project", "Active Ticket");

		mockMvc.perform(post("/tickets/{ticketId}/comments", activeTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(999999L, "Missing author"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Author user not found"));

		mockMvc.perform(post("/tickets/{ticketId}/comments", activeTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(author.id(), "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("Comment content must not be blank")));
	}

	@Test
	void updateAndDeleteCommentValidateRelationshipAndHideDeletedComments() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("commentupdate", "DEVELOPER");
		Long projectId = createProject(author.token(), "Comment Update Project", "Comment update project", author.id());
		Long ticketId = createTicket(author.token(), projectId, author.id(), "Commented Ticket");
		Long otherTicketId = createTicket(author.token(), projectId, author.id(), "Other Ticket");
		Long commentId = createComment(author.token(), ticketId, author.id(), "Original comment");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", otherTicketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Wrong ticket"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Comment not found"));

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", " Updated comment "))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].content").value("Updated comment"));

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("Comment content must not be blank")));

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void mentionsAreParsedCaseInsensitivelyDeduplicatedAndIgnoreUnknownUsers() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("mentionauthor", "DEVELOPER");
		AuthenticatedUser mentioned = createAuthenticatedUser("mentiontarget", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Mention Project", "Mention Ticket");

		MvcResult result = mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(
						author.id(),
						"Hello @MENTIONTARGET and @mentiontarget, ignore @unknown and test@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$.mentionedUsers[0].id").value(mentioned.id().intValue()))
			.andExpect(jsonPath("$.mentionedUsers[0].username").value("mentiontarget"))
			.andReturn();

		Long commentId = readId(result);
		mockMvc.perform(get("/users/{userId}/mentions", mentioned.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.data[0].id").value(commentId.intValue()))
			.andExpect(jsonPath("$.data[0].mentionedUsers", hasSize(1)));
	}

	@Test
	void mentionsReturnNewestFirstAndSupportSimplePagination() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("mentionpageauthor", "DEVELOPER");
		AuthenticatedUser mentioned = createAuthenticatedUser("mentionpageuser", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Mention Page Project", "Mention Page Ticket");
		Long firstCommentId = createComment(author.token(), ticketId, author.id(), "First @mentionpageuser");
		Long secondCommentId = createComment(author.token(), ticketId, author.id(), "Second @mentionpageuser");

		mockMvc.perform(get("/users/{userId}/mentions", mentioned.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.param("page", "1")
				.param("pageSize", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].id").value(secondCommentId.intValue()));

		mockMvc.perform(get("/users/{userId}/mentions", mentioned.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.param("page", "2")
				.param("pageSize", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.data[0].id").value(firstCommentId.intValue()));

		mockMvc.perform(get("/users/{userId}/mentions", mentioned.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid pagination parameters"));
	}

	@Test
	void updatingCommentReevaluatesMentionsAndDeletingCommentRemovesMentions() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("mentionupdateauthor", "DEVELOPER");
		AuthenticatedUser removed = createAuthenticatedUser("mentionremoved", "DEVELOPER");
		AuthenticatedUser added = createAuthenticatedUser("mentionadded", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Mention Update Project", "Mention Update Ticket");
		Long commentId = createComment(author.token(), ticketId, author.id(), "Hello @mentionremoved");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Hello @mentionadded"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}/mentions", removed.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isEmpty())
			.andExpect(jsonPath("$.total").value(0));

		mockMvc.perform(get("/users/{userId}/mentions", added.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].mentionedUsers[0].username").value("mentionadded"));

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}/mentions", added.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isEmpty())
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void commentStateChangesWriteAuditLogs() throws Exception {
		AuthenticatedUser author = createAuthenticatedUser("commentaudit", "DEVELOPER");
		Long ticketId = createTicketForUser(author, "Comment Audit Project", "Comment Audit Ticket");
		Long commentId = createComment(author.token(), ticketId, author.id(), "Audited comment");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Updated audited comment"))))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
				.param("entityType", "COMMENT")
				.param("entityId", commentId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].action").value("DELETE_COMMENT"))
			.andExpect(jsonPath("$[1].action").value("UPDATE_COMMENT"))
			.andExpect(jsonPath("$[2].action").value("ADD_COMMENT"));
	}

	private AuthenticatedUser createAuthenticatedUser(String username, String role) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"email", username + "@example.com",
						"fullName", "Test User",
						"role", role,
						"password", "secret"))))
			.andExpect(status().isOk())
			.andReturn();

		return new AuthenticatedUser(readId(createResult), login(username));
	}

	private String login(String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"password", "secret"))))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("accessToken")
			.asText();
	}

	private Long createTicketForUser(AuthenticatedUser user, String projectName, String ticketTitle) throws Exception {
		Long projectId = createProject(user.token(), projectName, projectName + " description", user.id());
		return createTicket(user.token(), projectId, user.id(), ticketTitle);
	}

	private Long createProject(String token, String name, String description, Long ownerId) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("name", name);
		request.put("description", description);
		request.put("ownerId", ownerId);

		MvcResult result = mockMvc.perform(post("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();

		return readId(result);
	}

	private Long createTicket(String token, Long projectId, Long assigneeId, String title) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("title", title);
		request.put("description", title + " description");
		request.put("status", "TODO");
		request.put("priority", "MEDIUM");
		request.put("type", "BUG");
		request.put("projectId", projectId);
		request.put("assigneeId", assigneeId);
		request.put("dueDate", "2026-04-01T00:00:00Z");

		MvcResult result = mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();

		return readId(result);
	}

	private Long createComment(String token, Long ticketId, Long authorId, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(commentRequest(authorId, content))))
			.andExpect(status().isOk())
			.andReturn();

		return readId(result);
	}

	private Map<String, Object> commentRequest(Long authorId, String content) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("authorId", authorId);
		request.put("content", content);
		return request;
	}

	private Long readId(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("id")
			.asLong();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record AuthenticatedUser(Long id, String token) {
	}
}
