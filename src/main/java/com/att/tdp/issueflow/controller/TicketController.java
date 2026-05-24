package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.dto.request.CreateTicketRequest;
import com.att.tdp.issueflow.dto.response.TicketImportSummaryResponse;
import com.att.tdp.issueflow.dto.response.TicketResponse;
import com.att.tdp.issueflow.dto.request.UpdateTicketRequest;
import com.att.tdp.issueflow.service.TicketCsvService;
import com.att.tdp.issueflow.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tickets")
public class TicketController {

	private final TicketService ticketService;

	private final TicketCsvService ticketCsvService;

	public TicketController(TicketService ticketService, TicketCsvService ticketCsvService) {
		this.ticketService = ticketService;
		this.ticketCsvService = ticketCsvService;
	}

	@GetMapping
	public List<TicketResponse> getTickets(@RequestParam Long projectId) {
		return ticketService.getTicketsByProject(projectId);
	}

	@GetMapping("/deleted")
	@PreAuthorize("hasRole('ADMIN')")
	public List<TicketResponse> getDeletedTickets(@RequestParam Long projectId) {
		return ticketService.getDeletedTickets(projectId);
	}

	@GetMapping(value = "/export", produces = "text/csv")
	public ResponseEntity<String> exportTickets(@RequestParam(required = false) Long projectId) {
		String csv = ticketCsvService.exportTickets(projectId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/csv"))
			.header(
					HttpHeaders.CONTENT_DISPOSITION,
					"attachment; filename=\"tickets-project-" + projectId + ".csv\"")
			.body(csv);
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TicketImportSummaryResponse importTickets(
			@RequestParam(name = "file", required = false) MultipartFile file,
			@RequestParam(required = false) Long projectId) {
		return ticketCsvService.importTickets(projectId, file);
	}

	@GetMapping("/{ticketId}")
	public TicketResponse getTicket(@PathVariable Long ticketId) {
		return ticketService.getTicket(ticketId);
	}

	@PostMapping
	public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
		return ticketService.createTicket(request);
	}

	@PatchMapping("/{ticketId}")
	public ResponseEntity<Void> updateTicket(
			@PathVariable Long ticketId,
			@RequestBody UpdateTicketRequest request) {
		ticketService.updateTicket(ticketId, request);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{ticketId}")
	public ResponseEntity<Void> deleteTicket(@PathVariable Long ticketId) {
		ticketService.deleteTicket(ticketId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{ticketId}/restore")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> restoreTicket(@PathVariable Long ticketId) {
		ticketService.restoreTicket(ticketId);
		return ResponseEntity.ok().build();
	}
}
