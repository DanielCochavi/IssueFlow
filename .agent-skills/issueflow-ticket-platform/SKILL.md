---
name: issueflow-ticket-platform
description: Use for IssueFlow backend work, including Spring Boot API implementation, service/domain design, tests, documentation, and assignment-specific changes.
project: IssueFlow
project_title: IssueFlow – Ticket Management Backend Platform
applies_to:
  - Spring Boot backend implementation
  - REST API changes
  - JPA persistence and validation
  - tests and test configuration
  - README, run.md, prompts.md, and assignment documentation
---

# IssueFlow – Ticket Management Backend Platform Skill

## When To Use

Use this skill when implementing, testing, reviewing, or documenting IssueFlow backend behavior. Do not load it for unrelated repository cleanup or generic text-only tasks that do not need backend context.

## Project Shape

IssueFlow is a Java 21 Spring Boot 3.4.2 single Maven Spring Boot application. PostgreSQL is available through Docker Compose for local development, but Step 1 intentionally does not implement persistence, APIs, or business logic.

Primary package:

```text
com.att.tdp.issueflow
```

Application entry point:

```text
src/main/java/com/att/tdp/issueflow/IssueFlowApplication.java
```

Configuration files:

```text
src/main/resources/application.yaml
src/test/resources/application.yaml
compose.yml
pom.xml
```

## Source Of Truth

Use `README.md` as the assignment source of truth for required APIs and behavior. Keep the visible product name as `IssueFlow` and the full title as `IssueFlow – Ticket Management Backend Platform`.

The required functional areas include:

- Users
- Authentication
- Projects
- Tickets
- Comments and mentions
- Audit logs
- Ticket dependencies
- Attachments
- CSV ticket import/export
- Soft delete and restore
- Workload reporting
- Auto-escalation
- Auto-assignment

## Package Convention

Future generated code must follow flat package-by-layer automatically. Classes go directly inside the layer package.

Correct:

```text
com.att.tdp.issueflow.controller.TicketController
com.att.tdp.issueflow.service.TicketService
com.att.tdp.issueflow.repository.TicketRepository
com.att.tdp.issueflow.entity.Ticket
com.att.tdp.issueflow.enums.TicketStatus
com.att.tdp.issueflow.dto.TicketResponse
```

Wrong:

```text
com.att.tdp.issueflow.controller.ticket.TicketController
com.att.tdp.issueflow.service.ticket.TicketService
com.att.tdp.issueflow.repository.ticket.TicketRepository
com.att.tdp.issueflow.dto.ticket.TicketResponse
com.att.tdp.issueflow.ticket.service.TicketService
com.att.tdp.issueflow.ticket.dto.TicketResponse
```

The Spring Boot application entry point is `com.att.tdp.issueflow.IssueFlowApplication`.

## Implementation Guidance

- Keep REST controllers thin and move business rules into services.
- Keep REST controllers in `com.att.tdp.issueflow.controller`.
- Keep business logic in `com.att.tdp.issueflow.service`.
- Keep repositories in `com.att.tdp.issueflow.repository`.
- Keep JPA entities in `com.att.tdp.issueflow.entity`.
- Keep enums in `com.att.tdp.issueflow.enums`.
- Keep request and response DTOs in `com.att.tdp.issueflow.dto`.
- Keep mappers in `com.att.tdp.issueflow.mapper`.
- Keep configuration in `com.att.tdp.issueflow.config`.
- Keep exceptions and handlers in `com.att.tdp.issueflow.exception`.
- Use DTOs for request and response payloads rather than exposing JPA entities directly.
- Do not expose JPA entities directly through controllers.
- Do not create domain subpackages under these layer packages.
- Do not place implementation classes directly under the root package or under domain-first packages.
- Validate request bodies with Jakarta Bean Validation annotations.
- Model missing resources and invalid state transitions with consistent error responses.
- Prefer `PATCH` for partial updates where the README specifies it.
- Preserve soft-delete semantics for tickets and projects; do not expose hard deletion unless the assignment changes.
- Keep audit logging tied to state-changing actions.
- Keep CSV import/export behavior deterministic and testable.

## Testing Guidance

Use the Maven wrapper:

```bash
./mvnw clean verify
```

Add focused tests for new behavior. Prefer service-layer tests for business rules and Spring MVC tests or integration tests for endpoint contracts when API behavior changes.

Test configuration lives under:

```text
src/test/resources/application.yaml
```

## Documentation Guidance

When updating documentation, keep these files aligned:

```text
README.md
run.md
prompts.md
AGENTS.md
.agent-skills/issueflow-ticket-platform/SKILL.md
```

Use the current IssueFlow naming and skill path consistently in all docs and agent instructions.
