package com.att.tdp.issueflow.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.WorkloadResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.Role;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class WorkloadService {

	private final ProjectRepository projectRepository;

	private final TicketRepository ticketRepository;

	private final UserRepository userRepository;

	public WorkloadService(
			ProjectRepository projectRepository,
			TicketRepository ticketRepository,
			UserRepository userRepository) {
		this.projectRepository = projectRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
	}

	public List<WorkloadResponse> getProjectWorkload(Long projectId) {
		getActiveProject(projectId);
		return rankedDeveloperWorkloads(projectId)
			.stream()
			.map(DeveloperWorkload::toResponse)
			.toList();
	}

	public Optional<User> findLeastLoadedDeveloper(Long projectId) {
		return rankedDeveloperWorkloads(projectId)
			.stream()
			.findFirst()
			.map(DeveloperWorkload::user);
	}

	private List<DeveloperWorkload> rankedDeveloperWorkloads(Long projectId) {
		return userRepository.findByRoleOrderByCreatedAtAsc(Role.DEVELOPER)
			.stream()
			.map(user -> new DeveloperWorkload(
					user,
					ticketRepository.countByProjectIdAndAssigneeIdAndStatusNotAndDeletedFalse(
							projectId,
							user.getId(),
							TicketStatus.DONE)))
			.sorted(Comparator
				.comparingLong(DeveloperWorkload::openTicketCount)
				.thenComparing(workload -> workload.user().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(workload -> workload.user().getId()))
			.toList();
	}

	private Project getActiveProject(Long projectId) {
		return projectRepository.findById(projectId)
			.filter(project -> !project.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private record DeveloperWorkload(User user, long openTicketCount) {

		WorkloadResponse toResponse() {
			return new WorkloadResponse(user.getId(), user.getUsername(), openTicketCount);
		}
	}
}
