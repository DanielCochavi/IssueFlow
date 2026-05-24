package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class WorkloadAndAutoAssignmentIntegrationTest {

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
	void workloadRequiresJwt() throws Exception {
		mockMvc.perform(get("/projects/{projectId}/workload", 1L))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void workloadValidatesMissingAndDeletedProject() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("workloadprojectowner", "DEVELOPER");

		mockMvc.perform(get("/projects/{projectId}/workload", 999999L)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));

		Long projectId = createProject(developer.token(), "Deleted Workload Project", "Deleted workload project", developer.id());
		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}/workload", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));
	}

	@Test
	void workloadReturnsDevelopersOnlyCountsOpenProjectTicketsAndSorts() throws Exception {
		AuthenticatedUser developerA = createAuthenticatedUser("workloaddeva", "DEVELOPER");
		AuthenticatedUser developerB = createAuthenticatedUser("workloaddevb", "DEVELOPER");
		AuthenticatedUser developerC = createAuthenticatedUser("workloaddevc", "DEVELOPER");
		createAuthenticatedUser("workloadadmin", "ADMIN");
		Long projectId = createProject(developerA.token(), "Workload Project", "Workload project", developerA.id());
		Long otherProjectId = createProject(developerA.token(), "Other Workload Project", "Other workload project", developerA.id());

		createTicket(developerA.token(), projectId, developerA.id(), "Open for A", "TODO");
		createTicket(developerA.token(), otherProjectId, developerB.id(), "Other project for B", "TODO");
		createTicket(developerA.token(), projectId, developerA.id(), "Done for A", "DONE");
		Long deletedTicketId = createTicket(developerA.token(), projectId, developerA.id(), "Deleted for A", "TODO");
		mockMvc.perform(delete("/tickets/{ticketId}", deletedTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}/workload", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].userId").value(developerB.id().intValue()))
			.andExpect(jsonPath("$[0].username").value(developerB.username()))
			.andExpect(jsonPath("$[0].openTicketCount").value(0))
			.andExpect(jsonPath("$[1].userId").value(developerC.id().intValue()))
			.andExpect(jsonPath("$[1].openTicketCount").value(0))
			.andExpect(jsonPath("$[2].userId").value(developerA.id().intValue()))
			.andExpect(jsonPath("$[2].openTicketCount").value(1));
	}

	@Test
	void ticketCreationAutoAssignsByTieBreakerAndLeastWorkloadAndWritesSystemAudit() throws Exception {
		AuthenticatedUser developerA = createAuthenticatedUser("autoassigndevold", "DEVELOPER");
		AuthenticatedUser developerB = createAuthenticatedUser("autoassigndevnew", "DEVELOPER");
		Long projectId = createProject(developerA.token(), "Auto Assignment Project", "Auto assignment project", developerA.id());

		MvcResult firstResult = createTicketResult(developerA.token(), projectId, null, "Tie-break assigned", "TODO");
		Long firstTicketId = readId(firstResult);
		assertAssignee(firstResult, developerA.id());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token()))
				.param("entityType", "TICKET")
				.param("entityId", firstTicketId.toString())
				.param("action", "AUTO_ASSIGN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].actor").value("SYSTEM"))
			.andExpect(jsonPath("$[0].entityId").value(firstTicketId.intValue()));

		MvcResult secondResult = createTicketResult(developerA.token(), projectId, null, "Least loaded assigned", "TODO");
		assertAssignee(secondResult, developerB.id());
	}

	@Test
	void ticketCreationWithoutDeveloperLeavesTicketUnassignedAndDoesNotAuditAutoAssign() throws Exception {
		AuthenticatedUser admin = createAuthenticatedUser("noautoassignadmin", "ADMIN");
		Long projectId = createProject(admin.token(), "No Developer Project", "No developer project", admin.id());

		MvcResult result = createTicketResult(admin.token(), projectId, null, "No developer candidate", "TODO");
		JsonNode assigneeId = objectMapper.readTree(result.getResponse().getContentAsString()).path("assigneeId");
		assertThat(assigneeId.isMissingNode() || assigneeId.isNull()).isTrue();

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
				.param("entityType", "TICKET")
				.param("action", "AUTO_ASSIGN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void explicitCreateAndPatchAssignmentsDoNotTriggerAutoAssignment() throws Exception {
		AuthenticatedUser developerA = createAuthenticatedUser("explicitassigneeold", "DEVELOPER");
		AuthenticatedUser developerB = createAuthenticatedUser("explicitassigneenew", "DEVELOPER");
		Long projectId = createProject(developerA.token(), "Explicit Assignment Project", "Explicit assignment project", developerA.id());

		MvcResult createResult = createTicketResult(developerA.token(), projectId, developerB.id(), "Explicit assignee", "TODO");
		Long ticketId = readId(createResult);
		assertAssignee(createResult, developerB.id());

		mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("assigneeId", developerA.id()))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(developerA.id().intValue()));

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developerA.token()))
				.param("entityType", "TICKET")
				.param("action", "AUTO_ASSIGN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void csvImportWithBlankAssigneeDoesNotAutoAssign() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvnoautoassign", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV No Auto Project", "CSV no auto project", developer.id());
		String csv = """
				id,title,description,status,priority,type,assigneeId
				1,Imported without assignee,Description,TODO,MEDIUM,BUG,
				""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(1))
			.andExpect(jsonPath("$.failed").value(0));

		Ticket importedTicket = ticketRepository.findByProjectIdAndDeletedFalse(projectId).getFirst();
		assertThat(importedTicket.getAssignee()).isNull();

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "TICKET")
				.param("action", "AUTO_ASSIGN"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
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

		return new AuthenticatedUser(readId(createResult), username, login(username));
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

	private Long createTicket(String token, Long projectId, Long assigneeId, String title, String statusValue) throws Exception {
		return readId(createTicketResult(token, projectId, assigneeId, title, statusValue));
	}

	private MvcResult createTicketResult(
			String token,
			Long projectId,
			Long assigneeId,
			String title,
			String statusValue) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("title", title);
		request.put("description", title + " description");
		request.put("status", statusValue);
		request.put("priority", "MEDIUM");
		request.put("type", "BUG");
		request.put("projectId", projectId);
		if (assigneeId != null) {
			request.put("assigneeId", assigneeId);
		}
		request.put("dueDate", "2026-04-01T00:00:00Z");

		return mockMvc.perform(post("/tickets")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andReturn();
	}

	private MockMultipartFile csvFile(String csv) {
		return new MockMultipartFile(
				"file",
				"tickets.csv",
				"text/csv",
				csv.getBytes(StandardCharsets.UTF_8));
	}

	private void assertAssignee(MvcResult result, Long assigneeId) throws Exception {
		assertThat(objectMapper.readTree(result.getResponse().getContentAsString())
			.get("assigneeId")
			.asLong()).isEqualTo(assigneeId);
	}

	private Long readId(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("id")
			.asLong();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record AuthenticatedUser(Long id, String username, String token) {
	}
}
