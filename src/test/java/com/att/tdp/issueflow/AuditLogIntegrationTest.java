package com.att.tdp.issueflow;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
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
class AuditLogIntegrationTest {

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
	private ProjectRepository projectRepository;

	@Autowired
	private TicketRepository ticketRepository;

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
	void getAuditLogsRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/audit-logs"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void creatingUserWritesCreateUserAuditLogWithoutCredentialFields() throws Exception {
		AuthenticatedUser user = createAuthenticatedUser("auditcreateuser", "DEVELOPER");

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(user.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action").value("CREATE"))
			.andExpect(jsonPath("$[0].entityType").value("USER"))
			.andExpect(jsonPath("$[0].entityId").value(user.id().intValue()))
			.andExpect(jsonPath("$[0].performedBy").value(user.id().intValue()))
			.andExpect(jsonPath("$[0].actor").value("USER"))
			.andExpect(jsonPath("$[0].timestamp").isNotEmpty())
			.andExpect(jsonPath("$[0].password").doesNotExist())
			.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void updatingUserWritesUpdateUserAuditLog() throws Exception {
		AuthenticatedUser user = createAuthenticatedUser("auditupdateuser", "DEVELOPER");

		mockMvc.perform(post("/users/update/{userId}", user.id())
				.header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"fullName", "Updated User",
						"role", "ADMIN"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
				.param("action", "UPDATE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].entityType").value("USER"))
			.andExpect(jsonPath("$[0].entityId").value(user.id().intValue()))
			.andExpect(jsonPath("$[0].performedBy").value(user.id().intValue()));
	}

	@Test
	void projectCreateUpdateDeleteAndRestoreWriteAuditLogs() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("auditprojectdeveloper", "DEVELOPER");
		AuthenticatedUser admin = createAuthenticatedUser("auditprojectadmin", "ADMIN");
		Long projectId = createProject(developer.token(), "Audited Project", "Audited project", developer.id());

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"name", "Updated Audited Project"))))
			.andExpect(status().isOk());

		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/projects/{projectId}/restore", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "PROJECT")
				.param("entityId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(4)))
			.andExpect(jsonPath("$[0].action").value("RESTORE"))
			.andExpect(jsonPath("$[0].performedBy").value(admin.id().intValue()))
			.andExpect(jsonPath("$[1].action").value("DELETE"))
			.andExpect(jsonPath("$[1].performedBy").value(developer.id().intValue()))
			.andExpect(jsonPath("$[2].action").value("UPDATE"))
			.andExpect(jsonPath("$[3].action").value("CREATE"));
	}

	@Test
	void auditLogsReturnNewestFirst() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("auditordering", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Ordering Project", "Ordering project", developer.id());

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"description", "Latest change"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].action").value("UPDATE"))
			.andExpect(jsonPath("$[0].entityType").value("PROJECT"))
			.andExpect(jsonPath("$[1].action").value("CREATE"))
			.andExpect(jsonPath("$[1].entityType").value("PROJECT"))
			.andExpect(jsonPath("$[2].action").value("CREATE"))
			.andExpect(jsonPath("$[2].entityType").value("USER"));
	}

	@Test
	void auditLogFiltersWork() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("auditfilters", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Filtered Project", "Filtered project", developer.id());

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"description", "Filtered update"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "PROJECT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].entityType").value("PROJECT"))
			.andExpect(jsonPath("$[1].entityType").value("PROJECT"));

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].entityId").value(projectId.intValue()));

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("action", "UPDATE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action").value("UPDATE"));

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("actor", "USER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(not(0))))
			.andExpect(jsonPath("$[0].actor").value("USER"));
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
