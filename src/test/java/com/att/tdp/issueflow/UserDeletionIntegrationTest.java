package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class UserDeletionIntegrationTest {

	private static final String REFERENCED_USER_DELETE_MESSAGE =
			"User cannot be deleted because it is referenced by existing projects, tickets, comments, or mentions";

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
	void deletingUnreferencedUserSucceeds() throws Exception {
		AuthenticatedUser actor = createAuthenticatedUser("deleteactor");
		AuthenticatedUser target = createAuthenticatedUser("unreferencedtarget");

		mockMvc.perform(delete("/users/{userId}", target.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(actor.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}", target.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(actor.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("User not found"));
	}

	@Test
	void deletingUserWhoOwnsProjectReturnsClearBadRequest() throws Exception {
		AuthenticatedUser actor = createAuthenticatedUser("projectdeleteactor");
		AuthenticatedUser target = createAuthenticatedUser("projectownertarget");
		createProject(actor.token(), "Referenced Project", target.id());

		assertReferencedUserCannotBeDeleted(actor.token(), target.id());
	}

	@Test
	void deletingUserAssignedToTicketReturnsClearBadRequest() throws Exception {
		AuthenticatedUser actor = createAuthenticatedUser("ticketdeleteactor");
		AuthenticatedUser target = createAuthenticatedUser("ticketassigneetarget");
		Long projectId = createProject(actor.token(), "Ticket Reference Project", actor.id());
		createTicket(actor.token(), projectId, target.id(), "Assigned Ticket");

		assertReferencedUserCannotBeDeleted(actor.token(), target.id());
	}

	@Test
	void deletingUserWhoAuthoredCommentReturnsClearBadRequest() throws Exception {
		AuthenticatedUser actor = createAuthenticatedUser("commentdeleteactor");
		AuthenticatedUser target = createAuthenticatedUser("commentauthortarget");
		Long projectId = createProject(actor.token(), "Comment Reference Project", actor.id());
		Long ticketId = createTicket(actor.token(), projectId, actor.id(), "Commented Ticket");
		addComment(actor.token(), ticketId, target.id(), "Comment by target");

		assertReferencedUserCannotBeDeleted(actor.token(), target.id());
	}

	@Test
	void deletingMentionedUserReturnsClearBadRequest() throws Exception {
		AuthenticatedUser actor = createAuthenticatedUser("mentiondeleteactor");
		AuthenticatedUser target = createAuthenticatedUser("mentionedtarget");
		Long projectId = createProject(actor.token(), "Mention Reference Project", actor.id());
		Long ticketId = createTicket(actor.token(), projectId, actor.id(), "Mentioned Ticket");
		addComment(actor.token(), ticketId, actor.id(), "Please review this @mentionedtarget");

		assertReferencedUserCannotBeDeleted(actor.token(), target.id());
	}

	private void assertReferencedUserCannotBeDeleted(String actorToken, Long targetUserId) throws Exception {
		mockMvc.perform(delete("/users/{userId}", targetUserId)
				.header(HttpHeaders.AUTHORIZATION, bearer(actorToken)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(REFERENCED_USER_DELETE_MESSAGE));
	}

	private AuthenticatedUser createAuthenticatedUser(String username) throws Exception {
		MvcResult createResult = mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"email", username + "@example.com",
						"fullName", "Test User",
						"role", "DEVELOPER",
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

	private Long createProject(String token, String name, Long ownerId) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("name", name);
		request.put("description", "Project description");
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
		request.put("description", "Ticket description");
		request.put("status", "TODO");
		request.put("priority", "MEDIUM");
		request.put("type", "BUG");
		request.put("projectId", projectId);
		request.put("assigneeId", assigneeId);

		MvcResult result = mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();

		return readId(result);
	}

	private void addComment(String token, Long ticketId, Long authorId, String content) throws Exception {
		mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"authorId", authorId,
						"content", content))))
			.andExpect(status().isOk());
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
