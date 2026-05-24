# IssueFlow – Ticket Management Backend Platform Run Guide

This file is the local setup, build, run, and test runbook for the IssueFlow backend.

## Prerequisites

- Java 21 is required.
- Docker Desktop must be installed and running.
- Maven does not need to be installed separately because this repository includes the Maven wrapper (`./mvnw`).

## Start PostgreSQL

Confirm Docker is installed and the Docker daemon is running:

```bash
docker --version
docker compose version
docker info
```

On macOS, start Docker Desktop before running Compose.

```bash
docker compose up -d
```

The local PostgreSQL service is defined in `compose.yml` and listens on `localhost:5432`.

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

Use the full verification command before handoff:

```bash
./mvnw clean verify
```

## Run the Application

```bash
./mvnw spring-boot:run
```

The API runs at:

```text
http://localhost:8080
```

Useful local URLs:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Protected endpoints still require `Authorization: Bearer <token>`.

## Configuration Notes

- Production profile management is not part of this assignment repository.
- Local database settings live in `src/main/resources/application.yaml`.
- Test database settings live in `src/test/resources/application.yaml` and use H2.
- The local JWT signing secret and PostgreSQL password are development-only placeholders and must be changed or externalized before deployment.
- The escalation scheduler is enabled by default for the application and disabled in tests.

## Local API Usage

Create a user. The implementation accepts `password` on `POST /users` so login can validate credentials.

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe","role":"DEVELOPER","password":"secret"}'
```

Login and copy the returned `accessToken`.

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"jdoe","password":"secret"}'

TOKEN="<accessToken from login>"
```

Call authenticated endpoints with the JWT.

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

Create a project and ticket.

```bash
curl -X POST http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Sample Project","description":"A sample project","ownerId":1}'

curl -X POST http://localhost:8080/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Fix login bug","description":"Login fails for valid users","status":"TODO","priority":"HIGH","type":"BUG","projectId":1,"dueDate":"2026-04-01T00:00:00Z"}'
```

Fetch active projects and tickets.

```bash
curl http://localhost:8080/projects \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:8080/tickets?projectId=1" \
  -H "Authorization: Bearer $TOKEN"
```

Add a comment with a mention and fetch mentions for the user.

```bash
curl -X POST http://localhost:8080/tickets/1/comments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"authorId":1,"content":"Please review this @jdoe"}'

curl "http://localhost:8080/users/1/mentions?page=1&pageSize=20" \
  -H "Authorization: Bearer $TOKEN"
```

Export tickets to CSV.

```bash
curl "http://localhost:8080/tickets/export?projectId=1" \
  -H "Authorization: Bearer $TOKEN" \
  -o tickets-project-1.csv
```

Import tickets from CSV.

```bash
curl -X POST http://localhost:8080/tickets/import \
  -H "Authorization: Bearer $TOKEN" \
  -F "projectId=1" \
  -F "file=@./tickets-project-1.csv;type=text/csv"
```

Upload and delete an attachment.

```bash
curl -X POST http://localhost:8080/tickets/1/attachments \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@./screenshot.png;type=image/png"

curl -X DELETE http://localhost:8080/tickets/1/attachments/1 \
  -H "Authorization: Bearer $TOKEN"
```

Fetch audit logs.

```bash
curl "http://localhost:8080/audit-logs?entityType=TICKET" \
  -H "Authorization: Bearer $TOKEN"
```

## Stop Local Services

```bash
docker compose down
```

To remove the local PostgreSQL volume as well:

```bash
docker compose down -v
```
