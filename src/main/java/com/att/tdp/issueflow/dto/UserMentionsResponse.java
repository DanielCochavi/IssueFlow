package com.att.tdp.issueflow.dto;

import java.util.List;

public record UserMentionsResponse(
		List<CommentResponse> data,
		int total,
		int page) {
}
