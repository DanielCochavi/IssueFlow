package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
		@NotBlank(message = "Comment content must not be blank") String content) {
}
