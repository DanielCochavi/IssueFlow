package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class TicketCsvIntegrationTest {

	private static final List<String> CSV_HEADERS = List.of(
			"id",
			"title",
			"description",
			"status",
			"priority",
			"type",
			"assigneeId");

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
	void csvEndpointsRequireJwt() throws Exception {
		mockMvc.perform(get("/tickets/export").param("projectId", "1"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(validCsv("", "")))
				.param("projectId", "1"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void exportActiveProjectTicketsAsCsvWithEscapedValues() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvexporter", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV Export Project", "CSV export project", developer.id());
		Long firstTicketId = createTicket(
				developer.token(),
				projectId,
				developer.id(),
				"Simple ticket",
				"Simple description",
				"TODO",
				"MEDIUM",
				"BUG");
		Long secondTicketId = createTicket(
				developer.token(),
				projectId,
				developer.id(),
				"Title, with \"quotes\"",
				"Description, with \"quotes\"",
				"IN_PROGRESS",
				"HIGH",
				"FEATURE");

		MvcResult result = mockMvc.perform(get("/tickets/export")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/csv"))
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("tickets-project-" + projectId + ".csv")))
			.andReturn();

		try (CSVParser parser = parseCsv(result.getResponse().getContentAsString())) {
			assertThat(parser.getHeaderNames()).containsExactlyElementsOf(CSV_HEADERS);
			List<CSVRecord> records = parser.getRecords();
			assertThat(records).hasSize(2);
			assertThat(records.get(0).get("id")).isEqualTo(firstTicketId.toString());
			assertThat(records.get(0).get("title")).isEqualTo("Simple ticket");
			assertThat(records.get(1).get("id")).isEqualTo(secondTicketId.toString());
			assertThat(records.get(1).get("title")).isEqualTo("Title, with \"quotes\"");
			assertThat(records.get(1).get("description")).isEqualTo("Description, with \"quotes\"");
		}
	}

	@Test
	void exportExcludesDeletedTicketsAndValidatesProject() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvexportvalidation", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV Export Validation", "CSV export validation", developer.id());
		Long visibleTicketId = createTicket(developer.token(), projectId, developer.id(), "Visible", "Visible", "TODO", "MEDIUM", "BUG");
		Long deletedTicketId = createTicket(developer.token(), projectId, developer.id(), "Deleted", "Deleted", "TODO", "MEDIUM", "BUG");

		mockMvc.perform(delete("/tickets/{ticketId}", deletedTicketId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/tickets/export")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", projectId.toString()))
			.andExpect(status().isOk())
			.andReturn();

		try (CSVParser parser = parseCsv(result.getResponse().getContentAsString())) {
			List<CSVRecord> records = parser.getRecords();
			assertThat(records).hasSize(1);
			assertThat(records.get(0).get("id")).isEqualTo(visibleTicketId.toString());
		}

		mockMvc.perform(get("/tickets/export")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", "999999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));

		Long deletedProjectId = createProject(developer.token(), "Deleted CSV Project", "Deleted CSV project", developer.id());
		mockMvc.perform(delete("/projects/{projectId}", deletedProjectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/export")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("projectId", deletedProjectId.toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));
	}

	@Test
	void importValidCsvCreatesTicketsIgnoresCsvIdAndWritesAuditLogs() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvimporter", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV Import Project", "CSV import project", developer.id());
		String csv = ("id,title,description,status,priority,type,assigneeId\n"
				+ "999,\"Imported, \"\"quoted\"\" title\",\"Description, \"\"quoted\"\"\",TODO,HIGH,BUG,%s\n"
				+ "123,Unassigned,,IN_PROGRESS,LOW,FEATURE,\n")
			.formatted(developer.id());

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(2))
			.andExpect(jsonPath("$.failed").value(0))
			.andExpect(jsonPath("$.errors").isEmpty());

		List<Ticket> importedTickets = ticketRepository.findByProjectIdAndDeletedFalse(projectId)
			.stream()
			.sorted(Comparator.comparing(Ticket::getId))
			.toList();
		assertThat(importedTickets).hasSize(2);
		assertThat(importedTickets)
			.extracting(Ticket::getId)
			.doesNotContain(999L, 123L);
		assertThat(importedTickets)
			.extracting(Ticket::getTitle)
			.containsExactly("Imported, \"quoted\" title", "Unassigned");
		assertThat(importedTickets.get(0).getDescription()).isEqualTo("Description, \"quoted\"");
		assertThat(importedTickets.get(0).getAssignee().getId()).isEqualTo(developer.id());
		assertThat(importedTickets.get(1).getAssignee()).isNull();

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "TICKET")
				.param("action", "IMPORT_TICKETS"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)));
	}

	@Test
	void importContinuesAfterRowFailures() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvpartialimport", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV Partial Project", "CSV partial project", developer.id());
		String csv = """
				id,title,description,status,priority,type,assigneeId
				1,Valid,Valid description,TODO,MEDIUM,BUG,
				2,Bad status,Description,INVALID,MEDIUM,BUG,
				3,   ,Description,TODO,MEDIUM,BUG,
				4,Missing assignee,Description,TODO,MEDIUM,BUG,999999
				""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(1))
			.andExpect(jsonPath("$.failed").value(3))
			.andExpect(jsonPath("$.errors", hasSize(3)))
			.andExpect(jsonPath("$.errors[0]", containsString("status must be one of")))
			.andExpect(jsonPath("$.errors[1]", containsString("title must not be blank")))
			.andExpect(jsonPath("$.errors[2]", containsString("assignee user not found")));

		assertThat(ticketRepository.findByProjectIdAndDeletedFalse(projectId))
			.extracting(Ticket::getTitle)
			.containsExactly("Valid");

		mockMvc.perform(get("/audit-logs")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token()))
				.param("entityType", "TICKET")
				.param("action", "IMPORT_TICKETS"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void importRejectsInvalidRequestsAndMalformedCsv() throws Exception {
		AuthenticatedUser developer = createAuthenticatedUser("csvinvalidimport", "DEVELOPER");
		Long projectId = createProject(developer.token(), "CSV Invalid Project", "CSV invalid project", developer.id());

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(validCsv("Valid", "")))
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Project id is required"));

		mockMvc.perform(multipart("/tickets/import")
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("CSV file is required"));

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(""))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("CSV file is empty"));

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile("title,status\nOnly title,TODO\n"))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("CSV file headers must be: id, title, description, status, priority, type, assigneeId"));

		String malformedCsv = "id,title,description,status,priority,type,assigneeId\n1,\"unterminated,Description,TODO,MEDIUM,BUG,\n";
		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(malformedCsv))
				.param("projectId", projectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Malformed CSV file"));

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(validCsv("Missing project", "")))
				.param("projectId", "999999")
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));

		Long deletedProjectId = createProject(developer.token(), "Deleted Import Project", "Deleted import project", developer.id());
		mockMvc.perform(delete("/projects/{projectId}", deletedProjectId)
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isOk());

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(validCsv("Deleted project", "")))
				.param("projectId", deletedProjectId.toString())
				.header(HttpHeaders.AUTHORIZATION, bearer(developer.token())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found"));
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
			String priorityValue,
			String typeValue) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("title", title);
		request.put("description", description);
		request.put("status", statusValue);
		request.put("priority", priorityValue);
		request.put("type", typeValue);
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

	private MockMultipartFile csvFile(String csv) {
		return new MockMultipartFile(
				"file",
				"tickets.csv",
				"text/csv",
				csv.getBytes(StandardCharsets.UTF_8));
	}

	private CSVParser parseCsv(String csv) throws Exception {
		return CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.build()
			.parse(new StringReader(csv));
	}

	private String validCsv(String title, String assigneeId) {
		return """
				id,title,description,status,priority,type,assigneeId
				1,%s,Description,TODO,MEDIUM,BUG,%s
				""".formatted(title, assigneeId);
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
