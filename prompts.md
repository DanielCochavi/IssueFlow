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
