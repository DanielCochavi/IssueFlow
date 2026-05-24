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

Fetch audit logs for recorded user and project actions.

```bash
curl "http://localhost:8080/audit-logs?entityType=PROJECT" \
  -H "Authorization: Bearer <token>"
```

The local JWT signing secret is development-only and must be externalized before deployment.
