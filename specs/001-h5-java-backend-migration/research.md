# Research Notes

Feature: 001-h5-java-backend-migration
Updated: 2026-06-06

## Spec Kit

Official GitHub Spec Kit describes a spec-driven workflow where each phase produces Markdown artifacts that feed the next phase. The core process is `Spec -> Plan -> Tasks -> Implement`; production workflows can include `constitution`, `clarify`, `checklist`, and `analyze` quality gates.

Useful references:

- https://github.github.com/spec-kit/
- https://github.github.com/spec-kit/quickstart.html
- https://github.com/github/spec-kit

Local decision:

- Adopt the artifact structure now without requiring CLI installation.
- Keep durable context in `.specify/memory`.
- Keep feature work in top-level `specs/NNN-feature-name/`.

## Frontend Stack

React official docs report the latest major docs for React 19, with current docs hosted at `react.dev`.

Vite official docs support TypeScript and React templates and remain appropriate for H5 app scaffolding.

Useful references:

- https://react.dev/versions
- https://vite.dev/guide/

Local decision:

- Use TypeScript + React + Vite for H5 menus and app shell.
- Add Phaser only when battle presentation needs Canvas/game-loop behavior.

## Backend Stack

Spring Boot official system requirements show the current Spring Boot 4 line requires Java 17+ and is compatible with modern Java versions. For a greenfield backend, Java LTS with Spring Boot modular monolith is a good default.

Useful reference:

- https://docs.spring.io/spring-boot/system-requirements.html

Local decision:

- Target Spring Boot modular monolith.
- Choose exact Java/Spring Boot versions during scaffold after validating dependency compatibility.
- Record the decision as ADR before implementation.

## MySQL

MySQL official release documentation describes Innovation and LTS release tracks. MySQL 8.4 is an LTS line and is widely supported by managed database providers.

Useful references:

- https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html
- https://dev.mysql.com/doc/relnotes/mysql/8.4/en/

Local decision:

- Prefer MySQL 8.4 LTS for the first backend unless deployment platform constraints make another LTS safer.

## Redis

Redis official docs list Redis 8.x GA versions and recommend using the latest available version for features, performance, and security updates. Redis 8.x includes modern data capabilities while still fitting standard cache, lock, stream, and ranking use cases.

Useful references:

- https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/
- https://redis.io/docs/latest/operate/oss_and_stack/install/

Local decision:

- Use Redis 8.x GA.
- Pin exact minor/patch in deployment files when the runtime is scaffolded.

