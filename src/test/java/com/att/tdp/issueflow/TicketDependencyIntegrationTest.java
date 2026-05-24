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

import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TicketDependencyIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuditLogRepository auditLogRepository;

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
		ticketDependencyRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void dependencyEndpointsRequireJwt() throws Exception {
		mockMvc.perform(post("/tickets/{ticketId}/dependencies", 1L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(2L))))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", 1L))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/tickets/{ticketId}/dependencies/{blockerId}", 1L, 2L))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedUserCanAddListAndRemoveDependency() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencycrud", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Dependency Project", "Dependency project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Blocked Ticket");
		Long blockerId = createTicket(developer.token(), projectId, developer.id(), "Blocking Ticket");

		addDependency(developer.token(), ticketId, blockerId);

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(blockerId.intValue()))
			.andExpect(jsonPath("$[0].title").value("Blocking Ticket"))
			.andExpect(jsonPath("$[0].status").value("TODO"));

		mockMvc.perform(delete("/tickets/{ticketId}/dependencies/{blockerId}", ticketId, blockerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void addDependencyValidatesTicketsAndRules() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencyvalidation", "DEVELOPER");
		Long firstProjectId = createProject(developer.token(), "First Project", "First project", developer.id());
		Long secondProjectId = createProject(developer.token(), "Second Project", "Second project", developer.id());
		Long ticketId = createTicket(developer.token(), firstProjectId, developer.id(), "Target Ticket");
		Long blockerId = createTicket(developer.token(), firstProjectId, developer.id(), "Blocker Ticket");
		Long otherProjectTicketId = createTicket(developer.token(), secondProjectId, developer.id(), "Other Project Ticket");

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", 999999L)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(blockerId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(999999L))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		Long deletedTargetId = createTicket(developer.token(), firstProjectId, developer.id(), "Deleted Target");
		mockMvc.perform(delete("/tickets/{ticketId}", deletedTargetId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", deletedTargetId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(blockerId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		Long deletedBlockerId = createTicket(developer.token(), firstProjectId, developer.id(), "Deleted Blocker");
		mockMvc.perform(delete("/tickets/{ticketId}", deletedBlockerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(deletedBlockerId))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(otherProjectTicketId))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Dependent tickets must belong to the same project"));

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(ticketId))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket cannot depend on itself"));

		addDependency(developer.token(), ticketId, blockerId);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(blockerId))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket dependency already exists"));

		Long circularTicketId = createTicket(developer.token(), firstProjectId, developer.id(), "Circular Ticket");
		Long circularBlockerId = createTicket(developer.token(), firstProjectId, developer.id(), "Circular Blocker");
		addDependency(developer.token(), circularTicketId, circularBlockerId);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", circularBlockerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(circularTicketId))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Circular ticket dependency is not allowed"));
	}

	@Test
	void unresolvedBlockerPreventsDoneButNotEarlierTransitions() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencydoneblocked", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Blocked Done Project", "Blocked done project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Blocked Done Ticket");
		Long blockerId = createTicket(developer.token(), projectId, developer.id(), "Unresolved Blocker");
		addDependency(developer.token(), ticketId, blockerId);

		patchTicketStatus(developer.token(), ticketId, "IN_PROGRESS");
		patchTicketStatus(developer.token(), ticketId, "IN_REVIEW");

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket cannot be moved to DONE while it has unresolved blockers"));
	}

	@Test
	void ticketCanMoveToDoneAfterBlockerIsDone() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencydoneresolved", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Resolved Done Project", "Resolved done project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Resolved Target");
		Long blockerId = createTicket(developer.token(), projectId, developer.id(), "Resolved Blocker");
		addDependency(developer.token(), ticketId, blockerId);

		patchTicketStatus(developer.token(), blockerId, "IN_PROGRESS");
		patchTicketStatus(developer.token(), blockerId, "IN_REVIEW");
		patchTicketStatus(developer.token(), blockerId, "DONE");
		patchTicketStatus(developer.token(), ticketId, "IN_PROGRESS");
		patchTicketStatus(developer.token(), ticketId, "IN_REVIEW");
		patchTicketStatus(developer.token(), ticketId, "DONE");

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void ticketCanMoveToDoneAfterBlockerIsSoftDeleted() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencydonedeleted", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Deleted Blocker Project", "Deleted blocker project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Deleted Blocker Target");
		Long blockerId = createTicket(developer.token(), projectId, developer.id(), "Deleted Blocker");
		addDependency(developer.token(), ticketId, blockerId);

		mockMvc.perform(delete("/tickets/{ticketId}", blockerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		patchTicketStatus(developer.token(), ticketId, "IN_PROGRESS");
		patchTicketStatus(developer.token(), ticketId, "IN_REVIEW");
		patchTicketStatus(developer.token(), ticketId, "DONE");

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"));
	}

	@Test
	void dependencyStateChangesWriteAuditLogs() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("dependencyaudit", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Dependency Audit Project", "Dependency audit project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Audited Target");
		Long blockerId = createTicket(developer.token(), projectId, developer.id(), "Audited Blocker");

		addDependency(developer.token(), ticketId, blockerId);

		mockMvc.perform(delete("/tickets/{ticketId}/dependencies/{blockerId}", ticketId, blockerId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "TICKET_DEPENDENCY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].action").value("REMOVE_DEPENDENCY"))
			.andExpect(jsonPath("$[0].performedBy").value(developer.id().intValue()))
			.andExpect(jsonPath("$[1].action").value("ADD_DEPENDENCY"))
			.andExpect(jsonPath("$[1].performedBy").value(developer.id().intValue()));
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

	private void addDependency(String token, Long ticketId, Long blockerId) throws Exception {
		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dependencyRequest(blockerId))))
			.andExpect(status().isOk());
	}

	private Map<String, Object> dependencyRequest(Long blockerId) {
		return Map.of("blockedBy", blockerId);
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
