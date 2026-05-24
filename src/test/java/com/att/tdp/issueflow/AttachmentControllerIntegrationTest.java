package com.att.tdp.issueflow;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AttachmentControllerIntegrationTest {

	private static final int ELEVEN_MB = (10 * 1024 * 1024) + 1;

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
	void uploadAndDeleteRequireJwt() throws Exception {
		MockMultipartFile file = attachmentFile("screenshot.png", "image/png", "image".getBytes());

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", 1L)
				.file(file))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", 1L, 2L))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void uploadAttachmentToActiveTicketSucceeds() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("attachmentupload", "DEVELOPER");
		Long ticketId = createTicketForUser(developer, "Attachment Project", "Attachment Ticket");

		MvcResult result = uploadAttachment(
				developer.token(),
				ticketId,
				attachmentFile("screenshot.png", "image/png", "image".getBytes()));

		Long attachmentId = readId(result);
		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "ATTACHMENT")
				.param("entityId", attachmentId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action").value("ADD_ATTACHMENT"));

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "ATTACHMENT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].entityId").value(attachmentId.intValue()));

		assertUploadResponse(result, attachmentId, ticketId, "screenshot.png", "image/png");
	}

	@Test
	void uploadValidatesTicketAndFile() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("attachmentvalidation", "DEVELOPER");
		Long ticketId = createTicketForUser(developer, "Attachment Validation Project", "Attachment Validation Ticket");

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", 999999L)
				.file(attachmentFile("note.txt", "text/plain", "note".getBytes()))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		mockMvc.perform(delete("/tickets/{ticketId}", ticketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
				.file(attachmentFile("note.txt", "text/plain", "note".getBytes()))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found"));

		Long activeTicketId = createTicketForUser(developer, "Attachment Active Project", "Attachment Active Ticket");

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", activeTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Attachment file is required"));

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", activeTicketId)
				.file(attachmentFile("empty.txt", "text/plain", new byte[0]))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Attachment file is required"));

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", activeTicketId)
				.file(attachmentFile("large.txt", "text/plain", new byte[ELEVEN_MB]))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Attachment file exceeds 10 MB"));

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", activeTicketId)
				.file(attachmentFile("data.json", "application/json", "{}".getBytes()))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Attachment content type is not allowed"));
	}

	@Test
	void deleteAttachmentSucceedsAndValidatesOwnership() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("attachmentdelete", "DEVELOPER");
		Long projectId = createProject(developer.token(), "Attachment Delete Project", "Attachment delete project", developer.id());
		Long ticketId = createTicket(developer.token(), projectId, developer.id(), "Attachment Delete Ticket");
		Long otherTicketId = createTicket(developer.token(), projectId, developer.id(), "Other Attachment Ticket");
		Long attachmentId = readId(uploadAttachment(
				developer.token(),
				ticketId,
				attachmentFile("note.txt", "text/plain", "note".getBytes())));

		mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", otherTicketId, attachmentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Attachment not found"));

		mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", ticketId, 999999L)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Attachment not found"));

		mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", ticketId, attachmentId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "ATTACHMENT")
				.param("entityId", attachmentId.toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].action").value("DELETE_ATTACHMENT"))
			.andExpect(jsonPath("$[1].action").value("ADD_ATTACHMENT"));
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

	private MvcResult uploadAttachment(String token, Long ticketId, MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
				.file(file)
				.header(HttpHeaders.AUTHORIZATION, bearer(token)))
			.andExpect(status().isOk())
			.andReturn();
	}

	private MockMultipartFile attachmentFile(String filename, String contentType, byte[] content) {
		return new MockMultipartFile("file", filename, contentType, content);
	}

	private void assertUploadResponse(
			MvcResult result,
			Long attachmentId,
			Long ticketId,
			String filename,
			String contentType) throws Exception {
		String response = result.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(response).get("id").asLong()).isEqualTo(attachmentId);
		org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(response).get("ticketId").asLong()).isEqualTo(ticketId);
		org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(response).get("filename").asText()).isEqualTo(filename);
		org.assertj.core.api.Assertions.assertThat(objectMapper.readTree(response).get("contentType").asText()).isEqualTo(contentType);
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
