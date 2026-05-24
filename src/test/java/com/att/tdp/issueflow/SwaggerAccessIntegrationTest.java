package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerAccessIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void swaggerAndOpenApiEndpointsArePublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.openapi").exists())
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

		mockMvc.perform(get("/swagger-ui/index.html"))
			.andExpect(status().isOk());
	}

	@Test
	void businessEndpointsStillRequireJwt() throws Exception {
		mockMvc.perform(get("/users"))
			.andExpect(status().isUnauthorized());
	}
}
