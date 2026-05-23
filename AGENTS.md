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

## Working Guidelines

- Prefer small, focused changes that match the existing Spring Boot and Maven structure.
- Do not change application source code unless the task requires it or documentation paths would otherwise become inaccurate.
- Use the Maven wrapper for build and test commands.
- When changing behavior, add or update focused tests where practical.
- Keep old project names out of new docs and instructions. Use `IssueFlow` or `IssueFlow – Ticket Management Backend Platform`.

