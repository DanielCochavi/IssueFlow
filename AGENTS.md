# IssueFlow Agent Instructions

Project name: IssueFlow

Full project title: IssueFlow – Ticket Management Backend Platform

## Skill Discovery

Before implementation, testing, review, or documentation tasks, check `.agent-skills/` for relevant skills.

Read only each skill file's YAML frontmatter first. Use the frontmatter to decide whether the full skill applies to the task.

The IssueFlow project skill lives at:

```text
.agent-skills/issueflow-ticket-platform/SKILL.md
```

Load the full IssueFlow skill only when working on the Spring Boot backend, API implementation, tests, documentation, or assignment-specific changes for IssueFlow.

Avoid loading the full IssueFlow skill for unrelated tasks such as generic file cleanup, editor settings, dependency-free text edits, or other work that does not need IssueFlow backend context.

## Project Context

IssueFlow is a Spring Boot backend platform for ticket management. It covers users, projects, tickets, comments, audit logs, dependencies, attachments, import/export, soft delete, mentions, workload, auto-escalation, and auto-assignment.

Use `README.md` as the source of truth for assignment requirements and API behavior. Keep documentation paths and references aligned with the current IssueFlow naming.

## Package Convention

IssueFlow uses flat package-by-layer.

Correct pattern:

```text
com.att.tdp.issueflow.<layer>.<ClassName>
```

Correct examples:

```text
com.att.tdp.issueflow.controller.TicketController
com.att.tdp.issueflow.service.TicketService
com.att.tdp.issueflow.repository.TicketRepository
com.att.tdp.issueflow.model.TicketEntity
com.att.tdp.issueflow.dto.TicketResponse
```

Wrong patterns:

```text
com.att.tdp.issueflow.<layer>.<domain>.<ClassName>
com.att.tdp.issueflow.<domain>.<layer>.<ClassName>
```

Wrong examples:

```text
com.att.tdp.issueflow.service.ticket.TicketService
com.att.tdp.issueflow.repository.ticket.TicketRepository
com.att.tdp.issueflow.dto.user.UserResponse
com.att.tdp.issueflow.ticket.service.TicketService
```

- REST controllers belong in `com.att.tdp.issueflow.controller`.
- Business services belong in `com.att.tdp.issueflow.service`.
- Spring Data repositories belong in `com.att.tdp.issueflow.repository`.
- JPA entities and domain models belong in `com.att.tdp.issueflow.model`.
- Request and response DTOs belong in `com.att.tdp.issueflow.dto`.
- Mappers belong in `com.att.tdp.issueflow.mapper`.
- Configuration classes belong in `com.att.tdp.issueflow.config`.
- Exceptions and exception handlers belong in `com.att.tdp.issueflow.exception`.
- Security and JWT classes belong in `com.att.tdp.issueflow.security`.
- Do not create domain subpackages under these layer packages.

The Spring Boot application entry point is `com.att.tdp.issueflow.IssueFlowApplication`.

## Working Guidelines

- Prefer small, focused changes that match the existing Spring Boot and Maven structure.
- Do not change application source code unless the task requires it or documentation paths would otherwise become inaccurate.
- Use the Maven wrapper for build and test commands.
- When changing behavior, add or update focused tests where practical.
- Keep old project names out of new docs and instructions. Use `IssueFlow` or `IssueFlow – Ticket Management Backend Platform`.
