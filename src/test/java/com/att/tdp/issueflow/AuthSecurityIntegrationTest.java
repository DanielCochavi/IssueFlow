package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void clearUsers() {
		userRepository.deleteAll();
	}

	@Test
	void postUsersAndLoginArePublic() throws Exception {
		createUser("publicuser", "publicuser@example.com");

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", "publicuser",
						"password", "secret"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void getUsersRequiresJwt() throws Exception {
		mockMvc.perform(get("/users"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void loginFailsWithWrongPassword() throws Exception {
		createUser("wrongpass", "wrongpass@example.com");

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", "wrongpass",
						"password", "incorrect"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void generatedJwtCanAuthenticateAuthMe() throws Exception {
		createUser("meuser", "meuser@example.com");
		String token = login("meuser", "secret");

		mockMvc.perform(get("/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("meuser"))
			.andExpect(jsonPath("$.email").value("meuser@example.com"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void logoutDeniesTheSameToken() throws Exception {
		createUser("logoutuser", "logoutuser@example.com");
		String token = login("logoutuser", "secret");

		mockMvc.perform(post("/auth/logout")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	private void createUser(String username, String email) throws Exception {
		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"email", email,
						"fullName", "Test User",
						"role", "DEVELOPER",
						"password", "secret"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value(username))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	private String login(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"username", username,
						"password", password))))
			.andExpect(status().isOk())
			.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
			.get("accessToken")
			.asText();
	}
}
