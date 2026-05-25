package com.att.tdp.issueflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.att.tdp.issueflow.entity.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findByDeletedFalse();

	List<Project> findByDeletedTrue();

	boolean existsByOwnerId(Long ownerId);
}
