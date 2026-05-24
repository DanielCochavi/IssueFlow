package com.att.tdp.issueflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.Role;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.service.UserService;

@SpringBootTest
class UserServiceTest {

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

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
	void createUserSuccessfullyHashesPassword() {
		UserResponse response = userService.createUser(createRequest("jdoe", "jdoe@example.com"));

		assertThat(response.id()).isNotNull();
		assertThat(response.username()).isEqualTo("jdoe");
		assertThat(response.email()).isEqualTo("jdoe@example.com");
		assertThat(response.fullName()).isEqualTo("John Doe");
		assertThat(response.role()).isEqualTo(Role.DEVELOPER);

		User stored = userRepository.findByUsernameIgnoreCase("jdoe").orElseThrow();
		assertThat(stored.getPasswordHash()).isNotBlank();
		assertThat(stored.getPasswordHash()).isNotEqualTo("secret");
		assertThat(stored.getPasswordHash()).startsWith("$2");
	}

	@Test
	void rejectDuplicateUsernameIgnoringCase() {
		userService.createUser(createRequest("jdoe", "jdoe@example.com"));

		CreateUserRequest duplicate = new CreateUserRequest(
				"JDOE",
				"other@example.com",
				"Jane Doe",
				Role.DEVELOPER,
				"secret");

		assertThatThrownBy(() -> userService.createUser(duplicate))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Username already exists");
	}

	@Test
	void rejectDuplicateEmailIgnoringCase() {
		userService.createUser(createRequest("jdoe", "jdoe@example.com"));

		CreateUserRequest duplicate = new CreateUserRequest(
				"asmith",
				"JDOE@example.com",
				"Ann Smith",
				Role.DEVELOPER,
				"secret");

		assertThatThrownBy(() -> userService.createUser(duplicate))
			.isInstanceOf(BadRequestException.class)
			.hasMessageContaining("Email already exists");
	}

	@Test
	void getMissingUserReturnsNotFound() {
		assertThatThrownBy(() -> userService.getUser(999L))
			.isInstanceOf(ResourceNotFoundException.class)
			.hasMessageContaining("User not found");
	}

	@Test
	void updateUserFullNameAndRole() {
		UserResponse created = userService.createUser(createRequest("jdoe", "jdoe@example.com"));

		userService.updateUser(created.id(), new UpdateUserRequest("Jane Doe", Role.ADMIN));

		UserResponse updated = userService.getUser(created.id());
		assertThat(updated.fullName()).isEqualTo("Jane Doe");
		assertThat(updated.role()).isEqualTo(Role.ADMIN);
	}

	@Test
	void userResponseNeverExposesPasswordFields() {
		List<String> componentNames = Arrays.stream(UserResponse.class.getRecordComponents())
			.map(RecordComponent::getName)
			.toList();

		assertThat(componentNames).doesNotContain("password", "passwordHash");
	}

	private CreateUserRequest createRequest(String username, String email) {
		return new CreateUserRequest(
				username,
				email,
				"John Doe",
				Role.DEVELOPER,
				"secret");
	}
}
