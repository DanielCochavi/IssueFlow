# Run Guide

IssueFlow is currently at **Step 1 — Clean Maven project skeleton**. The project builds as a single Spring Boot application, but API endpoints and business logic are not implemented yet.

## Prerequisites

- Java 21
- Docker and Docker Compose

## Start PostgreSQL

```bash
docker compose up -d
```

## Build and Test

```bash
./mvnw clean verify
```

On Windows:

```bat
mvnw.cmd clean verify
```

## Run the Application

```bash
./mvnw spring-boot:run
```

No endpoints are implemented yet because this repository is at Step 1 only.
