package com.att.tdp.issueflow.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.BadRequestException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;

	private final UserRepository userRepository;

	public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
	}

	public List<ProjectResponse> getProjects() {
		return projectRepository.findByDeletedFalse()
			.stream()
			.map(ProjectService::toResponse)
			.toList();
	}

	public ProjectResponse getProject(Long projectId) {
		return toResponse(getActiveProject(projectId));
	}

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		User owner = userRepository.findById(request.ownerId())
			.orElseThrow(() -> new ResourceNotFoundException("Owner user not found"));

		Project project = new Project();
		project.setName(normalize(request.name()));
		project.setDescription(normalize(request.description()));
		project.setOwner(owner);
		project.setDeleted(false);

		return toResponse(projectRepository.save(project));
	}

	@Transactional
	public void updateProject(Long projectId, UpdateProjectRequest request) {
		if (request == null || (request.name() == null && request.description() == null)) {
			throw new BadRequestException("At least one project field must be provided");
		}

		Project project = getActiveProject(projectId);

		if (request.name() != null) {
			String name = normalize(request.name());
			if (name.isBlank()) {
				throw new BadRequestException("Project name must not be blank");
			}
			project.setName(name);
		}

		if (request.description() != null) {
			project.setDescription(normalize(request.description()));
		}
	}

	@Transactional
	public void deleteProject(Long projectId) {
		Project project = getActiveProject(projectId);
		project.setDeleted(true);
		project.setDeletedAt(Instant.now());
	}

	public List<ProjectResponse> getDeletedProjects() {
		return projectRepository.findByDeletedTrue()
			.stream()
			.map(ProjectService::toResponse)
			.toList();
	}

	@Transactional
	public void restoreProject(Long projectId) {
		Project project = getDeletedProject(projectId);
		project.setDeleted(false);
		project.setDeletedAt(null);
	}

	private Project getActiveProject(Long projectId) {
		return projectRepository.findById(projectId)
			.filter(project -> !project.isDeleted())
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private Project getDeletedProject(Long projectId) {
		return projectRepository.findById(projectId)
			.filter(Project::isDeleted)
			.orElseThrow(() -> new ResourceNotFoundException("Project not found"));
	}

	private static ProjectResponse toResponse(Project project) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getDescription(),
				project.getOwner().getId());
	}

	private static String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
