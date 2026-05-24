package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.MentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.service.EscalationService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AutoEscalationIntegrationTest.FixedClockConfig.class)
class AutoEscalationIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-05-24T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EscalationService escalationService;

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
	void escalationIgnoresTicketsThatAreNotCandidatesAndDoesNotAuditNoOps() throws Exception {
		Fixture fixture = createFixture("ignored");
		createTicket(
				fixture.project(), fixture.user(), "No due date", TicketPriority.LOW, TicketStatus.TODO, null, false, false);
		createTicket(
				fixture.project(),
				fixture.user(),
				"Future due date",
				TicketPriority.LOW,
				TicketStatus.TODO,
				NOW.plusSeconds(60),
				false,
				false);
		createTicket(
				fixture.project(),
				fixture.user(),
				"Done ticket",
				TicketPriority.LOW,
				TicketStatus.DONE,
				NOW.minusSeconds(60),
				false,
				false);
		createTicket(
				fixture.project(),
				fixture.user(),
				"Deleted ticket",
				TicketPriority.LOW,
				TicketStatus.TODO,
				NOW.minusSeconds(60),
				false,
				true);
		Ticket criticalOverdue = createTicket(
				fixture.project(),
				fixture.user(),
				"Already critical overdue",
				TicketPriority.CRITICAL,
				TicketStatus.TODO,
				NOW.minusSeconds(60),
				true,
				false);

		assertThat(escalationService.runEscalationCycle()).isZero();

		assertThat(ticketRepository.findById(criticalOverdue.getId()).orElseThrow().getPriority())
			.isEqualTo(TicketPriority.CRITICAL);
		assertThat(autoEscalateAudits()).isEmpty();
	}

	@Test
	void escalationUpdatesEachChangedTicketOneStepAndWritesSystemAudit() throws Exception {
		Fixture fixture = createFixture("prioritylevels");
		Ticket low = createTicket(
				fixture.project(),
				fixture.user(),
				"Low",
				TicketPriority.LOW,
				TicketStatus.IN_PROGRESS,
				NOW.minusSeconds(60),
				false,
				false);
		Ticket medium = createTicket(
				fixture.project(),
				fixture.user(),
				"Medium",
				TicketPriority.MEDIUM,
				TicketStatus.IN_PROGRESS,
				NOW.minusSeconds(60),
				false,
				false);
		Ticket high = createTicket(
				fixture.project(),
				fixture.user(),
				"High",
				TicketPriority.HIGH,
				TicketStatus.IN_PROGRESS,
				NOW.minusSeconds(60),
				false,
				false);
		Ticket critical = createTicket(
				fixture.project(),
				fixture.user(),
				"Critical",
				TicketPriority.CRITICAL,
				TicketStatus.IN_PROGRESS,
				NOW.minusSeconds(60),
				false,
				false);

		assertThat(escalationService.runEscalationCycle()).isEqualTo(4);

		assertTicket(low.getId(), TicketPriority.MEDIUM, TicketStatus.IN_PROGRESS, false);
		assertTicket(medium.getId(), TicketPriority.HIGH, TicketStatus.IN_PROGRESS, false);
		assertTicket(high.getId(), TicketPriority.CRITICAL, TicketStatus.IN_PROGRESS, true);
		assertTicket(critical.getId(), TicketPriority.CRITICAL, TicketStatus.IN_PROGRESS, true);

		assertThat(autoEscalateAudits())
			.hasSize(4)
			.allSatisfy(audit -> {
				assertThat(audit.getActor()).isEqualTo(AuditActor.SYSTEM);
				assertThat(audit.getEntityType()).isEqualTo(AuditEntityType.TICKET);
			});
	}

	@Test
	void multipleCyclesMoveLowToCriticalThenStopAuditingNoOps() throws Exception {
		Fixture fixture = createFixture("multiplecycles");
		Ticket ticket = createTicket(
				fixture.project(),
				fixture.user(),
				"Multiple cycles",
				TicketPriority.LOW,
				TicketStatus.TODO,
				NOW.minusSeconds(60),
				false,
				false);

		assertThat(escalationService.runEscalationCycle()).isEqualTo(1);
		assertTicket(ticket.getId(), TicketPriority.MEDIUM, TicketStatus.TODO, false);

		assertThat(escalationService.runEscalationCycle()).isEqualTo(1);
		assertTicket(ticket.getId(), TicketPriority.HIGH, TicketStatus.TODO, false);

		assertThat(escalationService.runEscalationCycle()).isEqualTo(1);
		assertTicket(ticket.getId(), TicketPriority.CRITICAL, TicketStatus.TODO, true);
		assertThat(autoEscalateAudits()).hasSize(3);

		assertThat(escalationService.runEscalationCycle()).isZero();
		assertTicket(ticket.getId(), TicketPriority.CRITICAL, TicketStatus.TODO, true);
		assertThat(autoEscalateAudits()).hasSize(3);
	}

	@Test
	void manualPriorityChangeClearsOverdueAndNextCycleReevaluatesFromNewPriority() throws Exception {
		Fixture fixture = createFixture("manualreset");
		Ticket ticket = createTicket(
				fixture.project(),
				fixture.user(),
				"Manual reset",
				TicketPriority.CRITICAL,
				TicketStatus.TODO,
				NOW.minusSeconds(60),
				true,
				false);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(fixture.token()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("priority", "LOW"))))
			.andExpect(status().isOk());

		assertTicket(ticket.getId(), TicketPriority.LOW, TicketStatus.TODO, false);

		assertThat(escalationService.runEscalationCycle()).isEqualTo(1);

		assertTicket(ticket.getId(), TicketPriority.MEDIUM, TicketStatus.TODO, false);
		assertThat(autoEscalateAudits()).hasSize(1);
	}

	private Fixture createFixture(String suffix) throws Exception {
		AuthenticatedUser authenticatedUser = createAuthenticatedUser("escalation" + suffix, "DEVELOPER");
		Long projectId = createProject(
				authenticatedUser.token(), "Escalation " + suffix, "Escalation project", authenticatedUser.id());
		return new Fixture(
				userRepository.findById(authenticatedUser.id()).orElseThrow(),
				projectRepository.findById(projectId).orElseThrow(),
				authenticatedUser.token());
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

	private Ticket createTicket(
			Project project,
			User assignee,
			String title,
			TicketPriority priority,
			TicketStatus status,
			Instant dueDate,
			boolean overdue,
			boolean deleted) {
		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription(title + " description");
		ticket.setStatus(status);
		ticket.setPriority(priority);
		ticket.setType(TicketType.BUG);
		ticket.setProject(project);
		ticket.setAssignee(assignee);
		ticket.setDueDate(dueDate);
		ticket.setOverdue(overdue);
		ticket.setDeleted(deleted);
		ticket.setDeletedAt(deleted ? NOW : null);
		return ticketRepository.saveAndFlush(ticket);
	}

	private void assertTicket(Long ticketId, TicketPriority priority, TicketStatus status, boolean overdue) {
		Ticket ticket = ticketRepository.findById(ticketId).orElseThrow();
		assertThat(ticket.getPriority()).isEqualTo(priority);
		assertThat(ticket.getStatus()).isEqualTo(status);
		assertThat(ticket.isOverdue()).isEqualTo(overdue);
	}

	private List<AuditLog> autoEscalateAudits() {
		return auditLogRepository.findByFilters(
				AuditEntityType.TICKET,
				null,
				AuditAction.AUTO_ESCALATE,
				AuditActor.SYSTEM);
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

	private record Fixture(User user, Project project, String token) {
	}

	@TestConfiguration
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
