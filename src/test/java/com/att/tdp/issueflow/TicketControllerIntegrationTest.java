package com.att.tdp.issueflow;

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

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketPriority;
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
class TicketControllerIntegrationTest {

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
	void ticketEndpointsRequireJwt() throws Exception {
		mockMvc.perform(get("/tickets").param("projectId", "1"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/tickets/{ticketId}", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/tickets/{ticketId}", 1L))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedUserCanCreateAndReadTicket() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketcreator", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Ticket Project", "Ticket project", developer.id());
		Long ticketId = createTicket(
				developer.token(),
				projectId,
				developer.id(),
				" Fix login bug ",
				" Ticket description ",
				"TODO",
				"HIGH");

		mockMvc.perform(get("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(ticketId.intValue()))
			.andExpect(jsonPath("$[0].title").value("Fix login bug"))
			.andExpect(jsonPath("$[0].description").value("Ticket description"))
			.andExpect(jsonPath("$[0].status").value("TODO"))
			.andExpect(jsonPath("$[0].priority").value("HIGH"))
			.andExpect(jsonPath("$[0].type").value("BUG"))
			.andExpect(jsonPath("$[0].projectId").value(projectId.intValue()))
			.andExpect(jsonPath("$[0].assigneeId").value(developer.id().intValue()))
			.andExpect(jsonPath("$[0].dueDate").isNotEmpty())
			.andExpect(jsonPath("$[0].isOverdue").value(false));

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ticketId.intValue()))
			.andExpect(jsonPath("$.title").value("Fix login bug"));
	}

	@Test
	void createTicketValidatesProjectAndAssignee() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketvalidation", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Validation Project", "Validation project", developer.id());

		mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ticketRequest(999999L, developer.id(), "Invalid Project"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));

		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ticketRequest(projectId, developer.id(), "Deleted Project"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));

		Long activeProjectId = createProject(developer.token(), "Active Project", "Active project", developer.id());
		mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ticketRequest(activeProjectId, 999999L, "Invalid Assignee"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Assignee user not found"));
	}

	@Test
	void createTicketAllowsMissingAssignee() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("unassignedticket", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Unassigned Project", "Unassigned project", developer.id());

		Long ticketId = createTicket(
				developer.token(),
				projectId,
				null,
				"Unassigned ticket",
				"Unassigned ticket",
				"TODO",
				"MEDIUM");

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").doesNotExist());
	}

	@Test
	void patchTicketUpdatesProvidedFieldsAndValidatesInput() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketpatch", "DEVELOPER");
		AuthenticatedUser assignee = createAuthenticatedUser("ticketpatchassignee", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Patch Ticket Project", "Patch ticket project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, null, "Original", "Original", "TODO", "LOW");

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"description", " Updated description ",
						"assigneeId", assignee.id()))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Original"))
			.andExpect(jsonPath("$.description").value("Updated description"))
			.andExpect(jsonPath("$.assigneeId").value(assignee.id().intValue()));

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("At least one ticket field must be provided"));

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket title must not be blank"));

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("assigneeId", 999999L))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Assignee user not found"));
	}

	@Test
	void ticketStatusLifecycleMovesForwardOnlyAndDoneIsImmutable() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketlifecycle", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Lifecycle Project", "Lifecycle project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Lifecycle", "Lifecycle", "TODO", "MEDIUM");

		patchTicketStatus(developer.token(), ticketId, "IN_PROGRESS");
		patchTicketStatus(developer.token(), ticketId, "IN_REVIEW");
		patchTicketStatus(developer.token(), ticketId, "DONE");

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Should fail"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket cannot be updated once it is DONE"));
	}

	@Test
	void ticketStatusLifecycleRejectsBackwardAndSkippedTransitions() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketinvalidlifecycle", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Invalid Lifecycle Project", "Invalid lifecycle project", developer.id());
		Long skippedTicketId = createTicket(developer.token(), projectId, developer.id(), "Skipped", "Skipped", "TODO", "MEDIUM");

		mockMvc.perform(patch("/tickets/{ticketId}", skippedTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "IN_REVIEW"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid ticket status transition"));

		Long backwardTicketId = createTicket(developer.token(), projectId, developer.id(), "Backward", "Backward", "TODO", "MEDIUM");
		patchTicketStatus(developer.token(), backwardTicketId, "IN_PROGRESS");

		mockMvc.perform(patch("/tickets/{ticketId}", backwardTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "TODO"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid ticket status transition"));
	}

	@Test
	void manualPriorityChangeClearsOverdueFlag() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketpriority", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Priority Project", "Priority project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Priority", "Priority", "TODO", "LOW");

		Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
		ticket.setOverdue(true);
		ticketRepository.save(ticket);

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("priority", TicketPriority.HIGH))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.isOverdue").value(false));
	}

	@Test
	void ticketSoftDeleteHidesTicketAndRestoreRequiresAdmin() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketsoftdelete", "DEVELOPER");
		AuthenticatedUser admin = createAuthenticatedUser("ticketrestoreadmin", "ADMIN");
		Long projectId = createProject(developer.token(), "Soft Delete Project", "Soft delete project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Soft Delete", "Soft delete", "TODO", "MEDIUM");

		mockMvc.perform(delete("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		mockMvc.perform(get("/tickets/deleted")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/tickets/deleted")
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(ticketId.intValue()));

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(ticketId.intValue()));
	}

	@Test
	void ticketStateChangesWriteAuditLogs() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("ticketauditdeveloper", "DEVELOPER");
		AuthenticatedUser admin = createAuthenticatedUser("ticketauditadmin", "ADMIN");
		Long projectId = createProject(developer.token(), "Audit Ticket Project", "Audit ticket project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Audit Ticket", "Audit ticket", "TODO", "MEDIUM");

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Updated Audit Ticket"))))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "TICKET")
				.param("entityId", ticketId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[0].action").value("RESTORE"))
			.andExpect(jsonPath("$[0].performedBy").value(admin.id().intValue()))
			.andExpect(jsonPath("$[1].action").value("DELETE"))
			.andExpect(jsonPath("$[2].action").value("UPDATE"))
			.andExpect(jsonPath("$[3].action").value("CREATE"));
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

	private Long createTicket(
			String token,
			Long projectId,
			Long assigneeId,
			String title,
			String description,
			String statusValue,
			String priorityValue) throws Exception {
		MvcResult result = mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(ticketRequest(
						projectId,
						assigneeId,
						title,
						description,
						statusValue,
						priorityValue))))
			.andExpect(status().isOk())
			.andReturn();

		return readId(result);
	}

	private Map<String, Object> ticketRequest(Long projectId, Long assigneeId, String title) {
		return ticketRequest(projectId, assigneeId, title, "Ticket description", "TODO", "MEDIUM");
	}

	private Map<String, Object> ticketRequest(
			Long projectId,
			Long assigneeId,
			String title,
			String description,
			String statusValue,
			String priorityValue) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("title", title);
		request.put("description", description);
		request.put("status", statusValue);
		request.put("priority", priorityValue);
		request.put("type", "BUG");
		request.put("projectId", projectId);
		if (assigneeId != null) {
			request.put("assigneeId", assigneeId);
		}
		request.put("dueDate", "2026-04-01T00:00:00Z");
		return request;
	}

	private void patchTicketStatus(String token, Long ticketId, String statusValue) throws Exception {
		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", statusValue))))
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
