# Prompts

## Step 1 — Clean Maven Project Skeleton

Model used: GPT-5.5 Thinking

Prompt summary: Clarify the instruction files first so future generated code follows the flat package-by-layer convention `com.att.tdp.issueflow.<layer>`. Convert the single Spring Boot Maven application into a clean Maven multi-module skeleton for IssueFlow – Ticket Management Backend Platform. Keep Java 21 and Spring Boot 3.4.2, create the service modules, add minimal Spring Boot application classes and local configuration, update Docker Compose for PostgreSQL, preserve the README assignment API contract, and avoid implementing business logic, entities, repositories, controllers, services, auth, Swagger/OpenAPI, migrations, or bootstrap data. No business logic was implemented in Step 1.

Package convention correction: The skeleton instructions were corrected from layer-then-domain packages to flat package-by-layer packages, such as `com.att.tdp.issueflow.service.TicketService`.

Single-app correction: The generated Maven multi-module split was removed and the project was corrected back to one Spring Boot application with a flat package-by-layer skeleton under `src/main/java/com/att/tdp/issueflow`.

## Step 3 — Password Handling Decision

The provided README table for POST /users does not include a password field, but the authentication contract requires POST /auth/login to accept username and password. Because there is no separate register or set-password endpoint, the implementation accepts password in CreateUserRequest for POST /users, hashes it with BCrypt, stores only User.passwordHash, and never exposes password or passwordHash in responses. JWTs are issued only after successful username/password validation.

## Step 4 — Project API and Project Soft Delete

Model used: GPT-5.5 Thinking

Summary:
Implemented Project CRUD according to README.md and the TDP requirements PDF, including soft delete, ADMIN-only deleted-project listing, ADMIN-only restore, validation, and tests.
