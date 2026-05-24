# IssueFlow – Ticket Management Backend Platform Run Guide

## Prerequisites

- Java 21
- Docker and Docker Compose
- Maven wrapper included in this repository

## Start PostgreSQL

```bash
docker compose up -d
```

The local database uses the development-only credentials from `compose.yml` and `src/main/resources/application.yaml`.

## Build

```bash
./mvnw clean verify
```

On Windows:

```bat
mvnw.cmd clean verify
```

## Run Tests

```bash
./mvnw test
```

For the same verification used before handoff:

```bash
./mvnw clean verify
```

## Run the Application

```bash
./mvnw spring-boot:run
```

The application runs on `http://localhost:8080`.

## Local API Flow

Create a user. `password` is accepted by the implementation so login can validate credentials.

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "jdoe",
    "email": "jdoe@example.com",
    "fullName": "John Doe",
    "role": "DEVELOPER",
    "password": "secret"
  }'
```

Login and copy the returned `accessToken`.

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "username": "jdoe", "password": "secret" }'
```

Call authenticated endpoints with `Authorization: Bearer <token>`.

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer <token>"
```

Create a project using the created user's `id` as `ownerId`.

```bash
curl -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sample Project",
    "description": "A sample project",
    "ownerId": 1
  }'
```

Fetch active projects.

```bash
curl http://localhost:8080/projects \
  -H "Authorization: Bearer <token>"
```

Create a ticket for the project.

```bash
curl -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Fix login bug",
    "description": "Login fails for valid users",
    "status": "TODO",
    "priority": "HIGH",
    "type": "BUG",
    "projectId": 1,
    "assigneeId": 1,
    "dueDate": "2026-04-01T00:00:00Z"
  }'
```

Fetch active tickets for a project.

```bash
curl "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer <token>"
```

After creating two tickets in the same project, add, list, and remove a dependency.

```bash
curl -X POST http://localhost:8080/tickets/1/dependencies \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "blockedBy": 2 }'

curl http://localhost:8080/tickets/1/dependencies \
  -H "Authorization: Bearer <token>"

curl -X DELETE http://localhost:8080/tickets/1/dependencies/2 \
  -H "Authorization: Bearer <token>"
```

Add and list comments for a ticket. Mentioned users are matched by `@username`.

```bash
curl -X POST http://localhost:8080/tickets/1/comments \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "authorId": 1, "content": "Please review this @jdoe" }'

curl http://localhost:8080/tickets/1/comments \
  -H "Authorization: Bearer <token>"
```

Fetch comments where a user was mentioned.

```bash
curl "http://localhost:8080/users/1/mentions?page=1&pageSize=20" \
  -H "Authorization: Bearer <token>"
```

Fetch audit logs for recorded actions.

```bash
curl "http://localhost:8080/audit-logs?entityType=PROJECT" \
  -H "Authorization: Bearer <token>"
```

The local JWT signing secret is development-only and must be externalized before deployment.
