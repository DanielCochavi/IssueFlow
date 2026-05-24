# Prompts

## Step 1 — Clean Maven Project Skeleton

Model used: GPT-5.5 Thinking

Prompt summary: Clarify the instruction files first so future generated code follows the flat package-by-layer convention `com.att.tdp.issueflow.<layer>`. Convert the single Spring Boot Maven application into a clean Maven multi-module skeleton for IssueFlow – Ticket Management Backend Platform. Keep Java 21 and Spring Boot 3.4.2, create the service modules, add minimal Spring Boot application classes and local configuration, update Docker Compose for PostgreSQL, preserve the README assignment API contract, and avoid implementing business logic, entities, repositories, controllers, services, auth, Swagger/OpenAPI, migrations, or bootstrap data. No business logic was implemented in Step 1.

Package convention correction: The skeleton instructions were corrected from layer-then-domain packages to flat package-by-layer packages, such as `com.att.tdp.issueflow.service.TicketService`.

Single-app correction: The generated Maven multi-module split was removed and the project was corrected back to one Spring Boot application with a flat package-by-layer skeleton under `src/main/java/com/att/tdp/issueflow`.
