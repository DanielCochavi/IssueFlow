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

## README.md Change Policy

- Do not edit `README.md` during normal implementation steps.
- Codex may read `README.md`, but must not edit it unless a future prompt explicitly says the task is the final README documentation pass.
- Do not update `README.md` with step-by-step progress such as "Step 2", "Step 3", "business logic not implemented yet", or similar temporary status notes.
- Do not add manual test flows during feature implementation steps.
- Do not add or update the architecture diagram during feature implementation steps.
- Treat `README.md` as the stable assignment/API contract during implementation.
- If `README.md` appears outdated during an implementation step, report it in the final summary instead of editing it.
- Update `README.md` only in the final documentation step, or when setup/run commands or the public API contract truly changed.
- Final README documentation work should include final setup/build/run/test instructions, one or two manual test flows, the final architecture diagram, and final notes aligned with implemented behavior.

## Documentation Policy

- Treat `prompts.md` as the professional engineering diary and AI usage record.
- Treat `run.md` as the local setup/build/run/test runbook, not as an implementation diary.
- Every implementation step should document assignment alignment, engineering intent, design decisions, scope control, tests, and ownership.

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
com.att.tdp.issueflow.entity.Ticket
com.att.tdp.issueflow.enums.TicketStatus
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
- JPA entities belong in `com.att.tdp.issueflow.entity`.
- Enums belong in `com.att.tdp.issueflow.enums`.
- Request and response DTOs belong in `com.att.tdp.issueflow.dto`.
- Mappers belong in `com.att.tdp.issueflow.mapper`.
- Configuration classes belong in `com.att.tdp.issueflow.config`.
- Exceptions and exception handlers belong in `com.att.tdp.issueflow.exception`.
- Security and JWT classes belong in `com.att.tdp.issueflow.security`.
- Do not create domain subpackages under these layer packages.

The Spring Boot application entry point is `com.att.tdp.issueflow.IssueFlowApplication`.

## Code Comment Policy

- Use comments sparingly; prefer clear naming and small methods.
- Add a 1-2 sentence comment only when it clarifies a non-obvious assignment rule, security decision, concurrency behavior, soft-delete/audit behavior, lifecycle rule, CSV parsing rule, auto-escalation rule, or auto-assignment rule.
- Do not comment obvious CRUD code, simple DTOs, mapper assignments, repository method names, or self-explanatory controller methods.

## Working Guidelines

- Prefer small, focused changes that match the existing Spring Boot and Maven structure.
- Do not change application source code unless the task requires it or documentation paths would otherwise become inaccurate.
- Use the Maven wrapper for build and test commands.
- When changing behavior, add or update focused tests where practical.
- Keep old project names out of new docs and instructions. Use `IssueFlow` or `IssueFlow – Ticket Management Backend Platform`.
