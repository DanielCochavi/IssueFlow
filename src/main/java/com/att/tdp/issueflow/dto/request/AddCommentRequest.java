package com.att.tdp.issueflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddCommentRequest(
		@NotNull Long authorId,
		@NotBlank(message = "Comment content must not be blank") String content) {
}
