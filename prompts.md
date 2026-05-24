# Prompts

## Step 1 — Clean Maven Project Skeleton

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Prepared IssueFlow as a Java 21 / Spring Boot 3.4.2 Maven backend.
- Preserved `README.md` as the API contract for the assignment.
- Established the project structure needed to implement the IssueFlow requirements incrementally.

### Engineering Intent

- Keep the project as a clean single Spring Boot application.
- Use the flat package-by-layer convention under `com.att.tdp.issueflow`.
- Avoid premature business logic in the initial setup.
- Avoid unnecessary Maven modules or generated structure that would not help the assignment.

### Prompt Summary

Codex was instructed to prepare the Spring Boot backend skeleton, align the package convention, keep the README API contract intact, and avoid implementing application behavior during the setup step.

### Key Design Decisions

- Use one Spring Boot application instead of an unnecessary multi-module split.
- Use flat package-by-layer packages such as `controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config`, and `security`.
- Use PostgreSQL through `compose.yml` for local development.

### Scope Control

- No business APIs, controllers, services, entities, repositories, authentication, Swagger/OpenAPI, migrations, or bootstrap data were implemented in this setup step.

### Validation and Testing

- Maven verification was used as the baseline validation command for the project structure.

### Ownership Note

- The generated structure was reviewed against the assignment shape and the agreed package conventions.

## Step 2 — Auth and User Service Implementation

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the User Management requirement from the README API contract.
- Implemented the Authentication requirement with `POST /auth/login`, `POST /auth/logout`, and `GET /auth/me`.
- Added JWT protection for authenticated endpoints while keeping the required registration/login endpoints public.
- Implemented user CRUD behavior for the current assignment scope.

### Engineering Intent

- Use DTO request and response boundaries instead of exposing JPA entities.
- Store credentials as `passwordHash` and never expose it in API responses.
- Hash passwords with BCrypt before persistence.
- Authenticate requests through a JWT authentication filter.
- Invalidate logged-out access tokens with a simple server-side deny-list.
- Keep validation and error handling consistent through centralized exceptions.
- Cover security behavior with MockMvc integration tests.

### Prompt Summary

Codex was instructed to implement Auth/User behavior using Spring Security, JWTs, the existing persistence layer, and the flat package-by-layer convention already established in the repository.

### Key Design Decisions

- Use BCrypt for password hashing.
- Use JWTs for stateless authentication.
- Use an in-memory token deny-list for logout in the current assignment stage.
- Keep `POST /users` and `POST /auth/login` public.
- Require JWT authentication for other endpoints.

### Scope Control

- Projects, tickets, comments, audit logs, dependencies, attachments, import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.

### Validation and Testing

- Focused auth/security integration tests verify public login/registration, protected endpoints, login failure, `/auth/me`, and logout token invalidation.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed to ensure password and `passwordHash` are not returned and the security behavior matches the assignment.

## Step 3 — Password Handling Decision

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- The README table for `POST /users` does not show a password field.
- The authentication contract requires `POST /auth/login` with username and password.
- There is no separate set-password or register-password endpoint in the README contract.

### Engineering Intent

- Allow `POST /users` to accept `password` as a practical implementation decision.
- Hash the submitted password with BCrypt.
- Store only `User.passwordHash`.
- Never expose `password` or `passwordHash` in responses.

### Key Design Decisions

- Accepting password in `CreateUserRequest` is necessary for login to work out of the box.
- This keeps the API simple while satisfying the assignment's authentication intent.

### Scope Control

- Refresh tokens, password reset, email verification, and external identity providers were not introduced.

### Validation and Testing

- Login tests verify password validation and JWT generation after successful credentials.

### Ownership Note

- This decision was documented because it extends the README user creation table in the smallest necessary way to satisfy authentication.

## Step 4 — Project API and Project Soft Delete

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented Project Management requirements:
  - create project with `name`, `description`, and `ownerId`
  - fetch all active projects
  - fetch project by id
  - update project name and/or description
  - delete project
- Implemented Project Soft Delete requirements:
  - projects are soft-deleted only
  - deleted projects are hidden from standard API responses
  - `GET /projects/deleted` is ADMIN only
  - `POST /projects/{projectId}/restore` is ADMIN only
- Matched the README Project API endpoint and response contract.

### Engineering Intent

- Keep `ProjectController` thin.
- Keep validation and business rules in `ProjectService`.
- Use `ProjectResponse` so API responses do not expose JPA internals.
- Use `CreateProjectRequest` and `UpdateProjectRequest` as clear API boundaries.
- Validate `ownerId` through `UserRepository` before persistence.
- Implement soft delete with `deleted` and `deletedAt` instead of physical deletion.
- Enforce ADMIN-only behavior through Spring Security method authorization.
- Verify real HTTP behavior with MockMvc tests.

### Prompt Summary

Codex was instructed to implement only Project CRUD and project soft-delete/restore behavior using the existing `Project` entity and `ProjectRepository`.

### Key Design Decisions

- Reuse the existing Project persistence model.
- Reject invalid `ownerId` values.
- Reject blank project names and empty update payloads.
- Hide deleted projects from normal reads.
- Keep deleted-project listing and restore as separate ADMIN-only operations.

### Scope Control

- Tickets, comments, audit logs, dependencies, attachments, import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.

### Validation and Testing

- Project integration tests cover create project, get projects, get by id, invalid owner, patch validation, soft delete, hidden deleted projects, ADMIN-only deleted listing, ADMIN-only restore, and restored project visibility.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, security expectations, validation behavior, and package conventions.

## Step 5 — Audit Log Foundation

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the persistent append-only audit log foundation required by the assignment.
- Added audit recording for existing state-changing User and Project operations.
- Implemented the README `GET /audit-logs` endpoint with optional `entityType`, `entityId`, `action`, and `actor` filters.

### Engineering Intent

- Centralize audit writes and reads in `AuditLogService`.
- Store `performedBy` as a scalar user id so audit history remains stable if a user is later deleted.
- Use a reusable user/system actor model for current and future manual or automated actions.
- Integrate audit logging with existing User and Project state changes before adding ticket/comment/automation features.
- Cover both audit write behavior and read/filter behavior with integration tests.

### Prompt Summary

Codex was instructed to implement the audit foundation before future ticket, comment, dependency, attachment, import/export, and automation features so audit behavior does not need to be retrofitted later.

### Key Design Decisions

- Use an append-only audit entity exposed only through a read endpoint.
- Use enum-based action, entity type, and actor fields.
- Store `performedBy` as `Long` instead of a `User` foreign key.
- Provide a filterable `GET /audit-logs` endpoint sorted newest first.
- Save audit records in the same transaction as the state-changing operation where practical.

### Scope Control

- Tickets, comments, dependencies, attachments, import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.

### Validation and Testing

- Audit integration tests cover user create/update audit writes, project create/update/delete/restore audit writes, authentication requirements, newest-first ordering, filters, and response field safety.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, and audit traceability expectations.

## Step 6 — Ticket API Core and Ticket Soft Delete

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the core Ticket Management requirement from the assignment.
- Matched the README Ticket API contract for creating, reading, updating, and deleting tickets.
- Implemented ticket soft-delete behavior, including deleted-ticket listing and restore endpoints.
- Recorded audit logs for state-changing ticket operations.

### Engineering Intent

- Use DTO request and response boundaries for the Ticket API.
- Keep ticket validation and lifecycle rules in `TicketService`.
- Enforce forward-only ticket status transitions.
- Preserve optimistic-locking readiness through the existing `@Version` field on `RecordBase`.
- Implement ticket deletes as soft deletes with `deleted` and `deletedAt`.
- Integrate audit logging for ticket create, update, delete, and restore.
- Verify behavior through focused MockMvc integration tests.

### Prompt Summary

Codex was instructed to implement only core Ticket CRUD and ticket soft-delete/restore behavior while deferring dependencies, comments, attachments, import/export, workload, auto-assignment, and auto-escalation.

### Key Design Decisions

- Reuse the existing `Ticket` entity, `TicketRepository`, and ticket enums.
- Validate that the referenced project exists and is active before ticket creation or project-scoped reads.
- Validate an optional assignee when `assigneeId` is provided.
- Allow tickets to remain unassigned when `assigneeId` is absent.
- Require one-step forward status movement: `TODO` to `IN_PROGRESS` to `IN_REVIEW` to `DONE`.
- Treat `DONE` tickets as immutable.
- Clear the `overdue` flag when priority is manually changed for future escalation alignment.
- Soft-delete tickets instead of physically deleting rows.
- Audit every state-changing ticket operation.

### Scope Control

- Ticket dependencies, dependency-based DONE blocking, comments, mentions, attachments, CSV import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.
- The DONE transition does not yet check blockers because ticket dependencies are intentionally deferred.

### Validation and Testing

- `TicketControllerIntegrationTest` covers ticket creation and reads, project and assignee validation, partial updates, patch validation, lifecycle transition rules, DONE immutability, manual priority changes, soft delete and restore, ADMIN-only deleted/restore endpoints, audit writes, and JWT protection.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, audit expectations, validation behavior, and security behavior.

## Step 7 — Ticket Dependencies and DONE Blocker Rule

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the Ticket Dependencies requirement from the assignment.
- Matched the README dependency endpoints:
  - `POST /tickets/{ticketId}/dependencies`
  - `GET /tickets/{ticketId}/dependencies`
  - `DELETE /tickets/{ticketId}/dependencies/{blockerId}`
- Enforced the blocker rule that prevents moving a ticket to `DONE` while unresolved blockers exist.
- Enforced the same-project constraint for dependent tickets.
- Recorded audit logs for state-changing dependency actions.

### Engineering Intent

- Keep dependency business rules in `TicketDependencyService`.
- Use DTO request and response boundaries instead of exposing JPA entities.
- Validate that dependencies stay within one project.
- Reject duplicate dependencies and self-dependencies.
- Integrate unresolved-blocker checks into the existing `TicketService` lifecycle validation.
- Audit dependency add/remove operations.
- Verify HTTP behavior and lifecycle integration with focused MockMvc tests.

### Prompt Summary

Codex was instructed to implement dependency endpoints and enforce the DONE blocker rule while deferring comments, attachments, import/export, workload, auto-assignment, and auto-escalation.

### Key Design Decisions

- Reuse the existing `TicketDependency` entity and repository.
- Reject self-dependency.
- Reject duplicate dependency.
- Reject cross-project dependency.
- Reject direct circular dependencies.
- Block `DONE` only when blockers are unresolved.
- Allow `DONE` when blockers are already `DONE` or soft-deleted.
- Audit add/remove dependency actions as `TICKET_DEPENDENCY` events.

### Scope Control

- Comments, mentions, attachments, CSV import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.
- Full graph cycle detection was not introduced; the implementation covers direct circular dependencies required for this step.

### Validation and Testing

- `TicketDependencyIntegrationTest` covers dependency add/list/remove, JWT protection, missing/deleted ticket validation, same-project validation, self/duplicate/direct-cycle validation, DONE blocker behavior, and dependency audit writes.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, lifecycle rules, audit expectations, and security behavior.

## Step 8 — Comments API and Mentions

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the Comment Management requirement from the assignment.
- Matched the README Comments API contract for listing, adding, updating, and deleting comments.
- Implemented the `@mention` mechanism required by the assignment.
- Included mention metadata in comment responses through `mentionedUsers`.
- Re-evaluated mention associations when comment content is updated.
- Recorded audit logs for state-changing comment actions.
- Preserved concurrent edit readiness through the existing optimistic-locking foundation.

### Engineering Intent

- Keep comment business rules in `CommentService`.
- Use DTO request and response boundaries instead of exposing JPA entities.
- Parse mentions with case-insensitive username matching.
- Soft-delete comments and remove mention associations for deleted comments.
- Integrate audit logging for comment add, update, and delete.
- Keep optimistic-lock conflict handling generic for tickets, comments, and future entities.
- Verify behavior with focused MockMvc integration tests.

### Prompt Summary

Codex was instructed to implement comments and mentions together because the README comment response includes `mentionedUsers` and the TDP requirements require mention metadata in each comment response.

### Key Design Decisions

- Reuse the existing `Comment` and `Mention` entities and repositories.
- Ignore unknown `@username` values instead of failing the whole comment operation.
- Deduplicate repeated mentions in the same comment.
- Re-evaluate mention associations on comment update.
- Remove mention associations when a comment is deleted.
- Avoid storing comment content or mention text in audit logs.

### Scope Control

- Attachments, CSV import/export, workload, auto-assignment, and auto-escalation were not implemented in this step.
- Mention delivery/notification side effects were limited to persisted association metadata for this backend assignment step.

### Validation and Testing

- `CommentIntegrationTest` covers comment CRUD, JWT protection, validation, soft delete visibility, mention parsing, case-insensitive matching, duplicate mention handling, unknown mention handling, mention pagination, mention update re-evaluation, mention cleanup on delete, and comment audit writes.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, mention rules, audit expectations, validation behavior, and security behavior.

## Step 9 — Attachment Management

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the Attachment Management requirement from the assignment.
- Matched the README attachment endpoint contract for uploading and deleting ticket attachments.
- Enforced the assignment upload limits: maximum file size of 10 MB and allowed content types of `image/png`, `image/jpeg`, `application/pdf`, and `text/plain`.
- Recorded audit logs for state-changing attachment operations.

### Engineering Intent

- Keep attachment validation and persistence rules in `AttachmentService`.
- Use `AttachmentResponse` as the API boundary so raw file bytes are never exposed.
- Validate the target ticket is active before accepting or deleting an attachment.
- Store attachment metadata and file bytes through the existing `Attachment` persistence model.
- Integrate audit logging for attachment upload and delete.
- Verify HTTP behavior and validation with focused MockMvc integration tests.

### Prompt Summary

Codex was instructed to implement only attachment upload and delete behavior for tickets while preserving the existing package structure, audit infrastructure, and README contract.

### Key Design Decisions

- Reuse the existing `Attachment` entity and `AttachmentRepository`.
- Keep uploads limited to a small fixed content-type allow-list.
- Reject missing, empty, oversized, and unsupported files before persistence.
- Preserve the original filename when provided and fall back to `attachment` when it is missing.
- Store file bytes in the database and return only metadata in API responses.
- Audit attachment upload and delete without storing file content or filenames in audit logs.

### Scope Control

- Attachment download/list endpoints, CSV import/export, workload, auto-assignment, auto-escalation, Swagger/OpenAPI, bootstrap data, smoke tests, and architecture documentation were not implemented in this step.

### Validation and Testing

- `AttachmentControllerIntegrationTest` covers upload and delete JWT protection, successful upload response shape, missing/deleted ticket validation, missing/empty/oversized/unsupported file validation, delete ownership validation, and attachment audit writes.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, attachment validation rules, audit expectations, and security behavior.

## Step 10 — Ticket CSV Export and Import

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the README Ticket CSV export endpoint: `GET /tickets/export?projectId={id}`.
- Implemented the README Ticket CSV import endpoint: `POST /tickets/import` with multipart `file` and `projectId` form fields.
- Export returns non-deleted tickets for an active project with the required fields: `id`, `title`, `description`, `status`, `priority`, `type`, and `assigneeId`.
- Import validates the target project, parses the required headers, ignores CSV ids, validates row values, continues past row-level failures, and returns the required created/failed/errors summary.
- Successful imported rows write ticket audit records with `IMPORT_TICKETS`.

### Engineering Intent

- Keep CSV-specific parsing, escaping, validation, and import summary behavior in `TicketCsvService`.
- Use Apache Commons CSV for both export and import so commas and quotes are handled consistently.
- Keep the existing Ticket API and lifecycle behavior intact while adding focused import/export endpoints.
- Treat export as read-only and import as state-changing.
- Keep row-level import failures isolated so valid rows are still created.
- Verify behavior with focused MockMvc integration tests.

### Prompt Summary

Codex was instructed to implement only Ticket CSV export/import using the existing ticket, project, user, repository, security, and audit infrastructure while deferring workload, automation, final README documentation, architecture diagrams, and helper scripts.

### Key Design Decisions

- Reuse existing `Ticket`, `TicketRepository`, `ProjectRepository`, `UserRepository`, and audit infrastructure.
- Add `TicketImportSummaryResponse` for the README import response shape.
- Add export/import mappings to the existing `TicketController` instead of introducing a separate controller or domain package.
- Validate active project before export or import.
- Ignore incoming CSV ids and let the database generate ticket ids.
- Allow blank `assigneeId` to import unassigned tickets.
- Require provided `assigneeId` values to reference an existing user.
- Audit only successfully imported rows and avoid storing CSV content in audit logs.

### Scope Control

- Workload API, auto-assignment, auto-escalation, final README documentation, architecture diagram updates, attachment download/list endpoints, Swagger/OpenAPI, bootstrap data, and smoke-test scripts were not implemented in this step.

### Validation and Testing

- `TicketCsvIntegrationTest` covers JWT protection, CSV export headers, deleted-ticket exclusion, comma/quote escaping, missing/deleted project validation, valid imports, ignored CSV ids, blank assignees, row-level failures, malformed/missing-header/missing-file validation, and import audit writes.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, CSV escaping expectations, validation behavior, audit expectations, and security behavior.

## Step 11 — Workload API and Auto-assignment

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented the README Workload API endpoint: `GET /projects/{projectId}/workload`.
- Implemented automatic ticket assignment when a ticket is created without an explicit `assigneeId`.
- Workload counts non-deleted, non-`DONE` tickets assigned to each developer within the requested project.
- Auto-assignment selects the least-loaded `DEVELOPER`, with oldest registration first and id as a deterministic fallback.
- Successful auto-assignment writes `AUTO_ASSIGN` audit records for `TICKET` with actor `SYSTEM`.

### Engineering Intent

- Keep workload calculation and auto-assignment candidate selection in `WorkloadService`.
- Keep `TicketService.createTicket` responsible for deciding whether auto-assignment should run.
- Preserve explicit assignee behavior for ticket creation and PATCH updates.
- Keep CSV import behavior stable by letting `TicketCsvService` create tickets directly from CSV data without triggering auto-assignment.
- Verify endpoint behavior, sorting, counting, assignment, and audit writes with focused integration tests.

### Prompt Summary

Codex was instructed to implement only workload reporting and ticket create-time auto-assignment while preserving existing ticket lifecycle, dependency blocker, soft-delete, CSV import/export, comments, mentions, attachments, auth, and audit behavior.

### Key Design Decisions

- No project-membership or project-user link model exists in the current persistence model, so all users with role `DEVELOPER` are treated as the workload population and auto-assignment candidate pool.
- ADMIN users are excluded from workload responses and auto-assignment candidates.
- Missing or deleted projects return the existing `Project not found` response.
- If no developer candidates exist, ticket creation succeeds with no assignee and no `AUTO_ASSIGN` audit record.
- Explicit `assigneeId` values on create and update override assignment behavior and do not write `AUTO_ASSIGN` audit records.
- CSV imports with blank `assigneeId` remain unassigned to preserve the Step 10 import contract.

### Scope Control

- Auto-escalation scheduler, final README documentation, architecture diagram updates, Swagger/OpenAPI, bootstrap data, smoke-test scripts, project membership modeling, notification delivery, and assignment emails were not implemented in this step.

### Validation and Testing

- `WorkloadAndAutoAssignmentIntegrationTest` covers JWT protection, missing/deleted project validation, developer-only workload responses, same-project open-ticket counts, soft-delete and `DONE` exclusions, workload sorting, auto-assignment tie-breaking and least-loaded selection, no-candidate behavior, explicit assignment preservation, PATCH assignment override behavior, system audit records, and CSV import non-auto-assignment.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, workload ordering rules, auto-assignment rules, audit expectations, and security behavior.

## Step 12 — Auto-escalation Scheduler

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Implemented automatic ticket escalation for unresolved overdue tickets with `dueDate` before the current time.
- Preserved existing ticket creation and update behavior for optional `dueDate`.
- Escalation applies only to non-deleted tickets whose status is not `DONE`.
- Priority moves one level per cycle: `LOW` to `MEDIUM`, `MEDIUM` to `HIGH`, and `HIGH` to `CRITICAL`.
- Tickets at `CRITICAL` are marked overdue when needed, and no further escalation happens after that.
- Successful automatic changes write `AUTO_ESCALATE` audit records for `TICKET` with actor `SYSTEM`.

### Engineering Intent

- Keep escalation rules in `EscalationService` with a public `runEscalationCycle()` method for deterministic tests.
- Enable scheduling through Spring scheduling with configurable delay properties.
- Use an injectable `Clock` so tests can control the current time without relying on wall-clock timing.
- Keep scheduler timing disabled in tests and trigger escalation directly.
- Preserve the existing manual priority update behavior that clears `overdue` when priority changes.

### Prompt Summary

Codex was instructed to implement only the auto-escalation scheduler and service behavior while preserving existing ticket lifecycle, dependency blocker, soft-delete, CSV import/export, workload, auto-assignment, comments, mentions, attachments, auth, and audit behavior.

### Key Design Decisions

- Reuse `TicketRepository.findByDueDateBeforeAndStatusNotAndDeletedFalse(...)` for escalation candidates.
- Use explicit priority transition logic instead of implicit enum ordering.
- Apply at most one priority increase per escalation cycle.
- Treat `CRITICAL` with `overdue=true` as an idempotent no-op and avoid duplicate audit records.
- Do not add a manual escalation endpoint or a separate escalation history table.
- Record only action, actor, entity type, and entity id in audit logs.

### Scope Control

- Final README documentation, architecture diagram updates, Swagger/OpenAPI, bootstrap data, smoke-test scripts, notification delivery, assignment emails, a new escalation history table, and manual trigger endpoints were not implemented in this step.

### Validation and Testing

- `AutoEscalationIntegrationTest` covers ignored tickets, one-step escalation, `CRITICAL` overdue handling, idempotent no-op behavior, status preservation, multiple cycles, manual priority reset behavior, and system audit records.
- The implementation was verified with `./mvnw clean verify`.

### Ownership Note

- The implementation was reviewed against the TDP PDF requirements provided in the prompt, the README API contract, existing package conventions, scheduler behavior, idempotency expectations, audit expectations, and ticket update behavior.

## Step 13 — Final Documentation and Verification

Model used: GPT-5.5 Thinking.

### Assignment Alignment

- Completed the final README documentation pass required for submission.
- Added the approved architecture diagram asset under `docs/architecture/IssueFlowPlatform.png`.
- Embedded the diagram in `README.md` under `Project System Design Diagram`.
- Updated `run.md` as the local setup, build, run, test, and stop-services runbook.
- Preserved the README API tables as the visible implementation contract while aligning surrounding notes with completed behavior.

### Engineering Intent

- Make final documentation describe the implemented backend rather than an in-progress step.
- Document the single Spring Boot application as logical service/module boundaries, not physical microservices.
- Keep local setup commands concise and reproducible.
- Keep secrets documented as development-only placeholders.
- Avoid documenting unsupported endpoints or future work as complete.

### Prompt Summary

Codex was instructed to finalize repository documentation, add the approved `IssueFlowPlatform.png` system design diagram, update README and runbook content, preserve package and assignment conventions, avoid new implementation features, and verify the repository with Maven.

### Key Design Decisions

- Use the provided diagram exactly as supplied instead of generating Mermaid or a replacement image.
- Keep `AGENTS.md` and the IssueFlow skill unchanged because their README, documentation, package, and comment policies were already aligned.
- Keep README manual test flows short and focused on implemented auth, project, ticket, comment mention, and CSV export behavior.
- Keep `run.md` practical and avoid duplicating the full README API contract.

### Scope Control

- No application source code, endpoint behavior, package structure, Swagger/OpenAPI setup, bootstrap data, seed data, smoke-test scripts, helper scripts, or extra Maven modules were added in this final documentation step.

### Validation and Testing

- Final verification used `./mvnw clean verify`.
- Documentation was reviewed to ensure README and runbook content do not contradict the implemented API behavior.

### Ownership Note

- The final documentation was reviewed against the TDP assignment guardrails provided in the prompt, the README API contract, existing package conventions, generated prompt history, local runbook expectations, and repository submission requirements.
