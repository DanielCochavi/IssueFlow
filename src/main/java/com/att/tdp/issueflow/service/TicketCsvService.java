package com.att.tdp.issueflow.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.att.tdp.issueflow.dto.TicketImportSummaryResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class TicketCsvService {

	private static final String[] CSV_HEADERS = {
			"id",
			"title",
			"description",
			"status",
			"priority",
			"type",
			"assigneeId"
	};

	private static final String HEADER_MESSAGE = String.join(", ", CSV_HEADERS);

	private final TicketRepository ticketRepository;

	private final ProjectRepository projectRepository;

	private final UserRepository userRepository;

	private final AuditLogService auditLogService;

	public TicketCsvService(
			TicketRepository ticketRepository,
			ProjectRepository projectRepository,
			UserRepository userRepository,
			AuditLogService auditLogService) {
		this.ticketRepository = ticketRepository;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	public String exportTickets(Long projectId) {
		getActiveProject(projectId);

		StringWriter writer = new StringWriter();
		try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
			.setHeader(CSV_HEADERS)
			.build())) {
			for (Ticket ticket : ticketRepository.findByProjectIdAndDeletedFalse(projectId)) {
				printer.printRecord(
						ticket.getId(),
						ticket.getTitle(),
						ticket.getDescription(),
						ticket.getStatus().name(),
						ticket.getPriority().name(),
						ticket.getType().name(),
						ticket.getAssignee() == null ? null : ticket.getAssignee().getId());
			}
		}
		catch (IOException exception) {
			throw new BadRequestException("Unable to export tickets");
		}
		return writer.toString();
	}

	@Transactional
	public TicketImportSummaryResponse importTickets(Long projectId, MultipartFile file) {
		Project project = getActiveProject(projectId);
		validateImportFile(file);

		int created = 0;
		List<String> errors = new ArrayList<>();

		try (CSVParser parser = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.build()
			.parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			validateHeaders(parser.getHeaderNames());

			for (CSVRecord record : parser) {
				try {
					Ticket ticket = toTicket(record, project);
					Ticket savedTicket = ticketRepository.save(ticket);
					auditLogService.recordCurrentUserAction(
							AuditAction.IMPORT_TICKETS,
							AuditEntityType.TICKET,
							savedTicket.getId());
					created++;
				}
				catch (RowValidationException exception) {
					errors.add(exception.getMessage());
				}
			}
		}
		catch (IOException | UncheckedIOException | IllegalArgumentException | IllegalStateException exception) {
			throw new BadRequestException("Malformed CSV file");
		}

		return new TicketImportSummaryResponse(created, errors.size(), errors);
	}

	private Ticket toTicket(CSVRecord record, Project project) {
		long rowNumber = record.getRecordNumber() + 1;
		String title = normalize(requiredValue(record, "title", rowNumber));
		if (title.isBlank()) {
			throw new RowValidationException(rowNumber, "title must not be blank");
		}

		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription(normalize(optionalValue(record, "description", rowNumber)));
		ticket.setStatus(parseEnum(record, "status", TicketStatus.class, rowNumber));
		ticket.setPriority(parseEnum(record, "priority", TicketPriority.class, rowNumber));
		ticket.setType(parseEnum(record, "type", TicketType.class, rowNumber));
		ticket.setProject(project);
		ticket.setAssignee(resolveAssignee(record, rowNumber));
		ticket.setOverdue(false);
		ticket.setDeleted(false);
		return ticket;
	}

	private User resolveAssignee(CSVRecord record, long rowNumber) {
		String rawAssigneeId = normalize(optionalValue(record, "assigneeId", rowNumber));
		if (rawAssigneeId.isBlank()) {
			return null;
		}

		Long assigneeId;
		try {
			assigneeId = Long.valueOf(rawAssigneeId);
		}
		catch (NumberFormatException exception) {
			throw new RowValidationException(rowNumber, "assigneeId must be a number");
		}

		return userRepository.findById(assigneeId)
			.orElseThrow(() -> new RowValidationException(rowNumber, "assignee user not found"));
	}

	private static <E extends Enum<E>> E parseEnum(
			CSVRecord record,
			String header,
			Class<E> enumType,
			long rowNumber) {
		String rawValue = normalize(requiredValue(record, header, rowNumber));
		if (rawValue.isBlank()) {
			throw new RowValidationException(rowNumber, header + " must be one of " + enumValues(enumType));
		}

		try {
			return Enum.valueOf(enumType, rawValue);
		}
		catch (IllegalArgumentException exception) {
			throw new RowValidationException(rowNumber, header + " must be one of " + enumValues(enumType));
		}
	}

	private Project getActiveProject(Long projectId) {
		if (projectId == null) {
			throw new BadRequestException("Project id is required");
		}

		return projectRepository.findById(projectId)
			.filter(project -> !project.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private static void validateImportFile(MultipartFile file) {
		if (file == null) {
			throw new BadRequestException("CSV file is required");
		}
		if (file.isEmpty()) {
			throw new BadRequestException("CSV file is empty");
		}
	}

	private static void validateHeaders(List<String> actualHeaders) {
		if (!actualHeaders.equals(Arrays.asList(CSV_HEADERS))) {
			throw new BadRequestException("CSV file headers must be: " + HEADER_MESSAGE);
		}
	}

	private static String requiredValue(CSVRecord record, String header, long rowNumber) {
		return optionalValue(record, header, rowNumber);
	}

	private static String optionalValue(CSVRecord record, String header, long rowNumber) {
		try {
			String value = record.get(header);
			return value == null ? "" : value;
		}
		catch (IllegalArgumentException exception) {
			throw new RowValidationException(rowNumber, "malformed CSV row");
		}
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private static <E extends Enum<E>> String enumValues(Class<E> enumType) {
		return String.join(", ", Arrays.stream(enumType.getEnumConstants())
			.map(Enum::name)
			.toList());
	}

	private static class RowValidationException extends RuntimeException {

		RowValidationException(long rowNumber, String message) {
			super("Row " + rowNumber + ": " + message);
		}
	}
}
