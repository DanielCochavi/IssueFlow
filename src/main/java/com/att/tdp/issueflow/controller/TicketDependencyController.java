package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.dto.AddTicketDependencyRequest;
import com.att.tdp.issueflow.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.service.TicketDependencyService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
public class TicketDependencyController {

	private final TicketDependencyService ticketDependencyService;

	public TicketDependencyController(TicketDependencyService ticketDependencyService) {
		this.ticketDependencyService = ticketDependencyService;
	}

	@PostMapping
	public ResponseEntity<Void> addDependency(
			@PathVariable Long ticketId,
			@Valid @RequestBody AddTicketDependencyRequest request) {
		ticketDependencyService.addDependency(ticketId, request);
		return ResponseEntity.ok().build();
	}

	@GetMapping
	public List<TicketDependencyResponse> getDependencies(@PathVariable Long ticketId) {
		return ticketDependencyService.getDependencies(ticketId);
	}

	@DeleteMapping("/{blockerId}")
	public ResponseEntity<Void> removeDependency(
			@PathVariable Long ticketId,
			@PathVariable Long blockerId) {
		ticketDependencyService.removeDependency(ticketId, blockerId);
		return ResponseEntity.ok().build();
	}
}
