package com.att.tdp.issueflow;

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
class ProjectControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private TicketDependencyRepository ticketDependencyRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@BeforeEach
	void clearData() {
		auditLogRepository.deleteAll();
		ticketDependencyRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void authenticatedUserCanCreateProjectAndReadIt() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("projectcreator", "DEVELOPER");

		MvcResult createResult = mockMvc.perform(post("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"name", " Sample Project ",
						"description", " A sample project ",
						"ownerId", developer.id()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Sample Project"))
			.andExpect(jsonPath("$.description").value("A sample project"))
			.andExpect(jsonPath("$.ownerId").value(developer.id().intValue()))
			.andReturn();

		Long projectId = readId(createResult);

		mockMvc.perform(get("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(projectId.intValue()))
			.andExpect(jsonPath("$[0].name").value("Sample Project"));

		mockMvc.perform(get("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(projectId.intValue()))
			.andExpect(jsonPath("$.ownerId").value(developer.id().intValue()));
	}

	@Test
	void createProjectFailsWhenOwnerDoesNotExist() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("missingowner", "DEVELOPER");

		mockMvc.perform(post("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"name", "Orphan Project",
						"ownerId", 999999L))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Owner user not found"));
	}

	@Test
	void patchProjectUpdatesOnlyProvidedFields() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("patchowner", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Original Name", "Original description", developer.id());

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"description", " Updated description "))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Original Name"))
			.andExpect(jsonPath("$.description").value("Updated description"));
	}

	@Test
	void patchProjectRejectsBlankNameAndEmptyBody() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("invalidpatch", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Patch Target", "Patch target", developer.id());

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Project name must not be blank"));

		mockMvc.perform(patch("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("At least one project field must be provided"));
	}

	@Test
	void deleteProjectSoftDeletesAndHidesFromStandardResponses() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("deleteowner", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Deleted Project", "Deleted description", developer.id());

		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());

		mockMvc.perform(get("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));
	}

	@Test
	void deletedProjectsEndpointRequiresAdminAndReturnsDeletedProjects() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("deleteddeveloper", "DEVELOPER");
		AuthenticatedUser admin = createAuthenticatedUser("deletedadmin", "ADMIN");
		Long projectId = createProject(developer.token(), "Deleted Listing", "Deleted listing", developer.id());

		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects/deleted")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/projects/deleted")
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(projectId.intValue()))
			.andExpect(jsonPath("$[0].name").value("Deleted Listing"));
	}

	@Test
	void restoreEndpointRequiresAdminAndRestoresProject() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("restoredeveloper", "DEVELOPER");
		AuthenticatedUser admin = createAuthenticatedUser("restoreadmin", "ADMIN");
		Long projectId = createProject(developer.token(), "Restored Project", "Restored description", developer.id());

		mockMvc.perform(delete("/projects/{projectId}", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(post("/projects/{projectId}/restore", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/projects/{projectId}/restore", projectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(projectId.intValue()))
			.andExpect(jsonPath("$[0].name").value("Restored Project"));
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
