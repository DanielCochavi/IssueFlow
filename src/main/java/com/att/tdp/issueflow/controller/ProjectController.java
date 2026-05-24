package com.att.tdp.issueflow.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.WorkloadResponse;
import com.att.tdp.issueflow.service.ProjectService;
import com.att.tdp.issueflow.service.WorkloadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/projects")
public class ProjectController {

	private final ProjectService projectService;

	private final WorkloadService workloadService;

	public ProjectController(ProjectService projectService, WorkloadService workloadService) {
		this.projectService = projectService;
		this.workloadService = workloadService;
	}

	@GetMapping
	public List<ProjectResponse> getProjects() {
		return projectService.getProjects();
	}

	@GetMapping("/deleted")
	@PreAuthorize("hasRole('ADMIN')")
	public List<ProjectResponse> getDeletedProjects() {
		return projectService.getDeletedProjects();
	}

	@GetMapping("/{projectId}")
	public ProjectResponse getProject(@PathVariable Long projectId) {
		return projectService.getProject(projectId);
	}

	@GetMapping("/{projectId}/workload")
	public List<WorkloadResponse> getProjectWorkload(@PathVariable Long projectId) {
		return workloadService.getProjectWorkload(projectId);
	}

	@PostMapping
	public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
		return projectService.createProject(request);
	}

	@PatchMapping("/{projectId}")
	public ResponseEntity<Void> updateProject(
			@PathVariable Long projectId,
			@RequestBody UpdateProjectRequest request) {
		projectService.updateProject(projectId, request);
		return ResponseEntity.ok().build();
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<Void> deleteProject(@PathVariable Long projectId) {
		projectService.deleteProject(projectId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/{projectId}/restore")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> restoreProject(@PathVariable Long projectId) {
		projectService.restoreProject(projectId);
		return ResponseEntity.ok().build();
	}
}
