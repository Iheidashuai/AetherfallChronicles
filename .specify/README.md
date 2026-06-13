# Spec Kit Adoption Notes

This repository uses a Spec Kit compatible documentation flow for long-running work.

Official Spec Kit centers development around persistent Markdown artifacts:

1. Constitution: stable project principles in `.specify/memory/constitution.md`
2. Spec: what to build and why
3. Plan: technical approach and architecture
4. Tasks: ordered implementation checklist
5. Implement: code changes driven by the above artifacts

## Local Structure

- `.specify/memory/constitution.md`: non-negotiable project rules for agents and maintainers.
- `.specify/memory/project-context.md`: durable context discovered from the current iOS project.
- `specs/001-h5-java-backend-migration/spec.md`: migration feature specification.
- `specs/001-h5-java-backend-migration/plan.md`: H5 + Java backend technical plan.
- `specs/001-h5-java-backend-migration/tasks.md`: implementation task breakdown.
- `specs/001-h5-java-backend-migration/research.md`: version and source notes.

## Recommended Workflow

For meaningful future features, use this sequence:

```text
constitution -> specify -> clarify -> checklist -> plan -> tasks -> analyze -> implement
```

If the official `specify` CLI is installed later, keep the existing artifacts and let the CLI manage new feature folders. Do not delete the current game design documents under `docs/specs`; treat them as source material for gameplay requirements.

## References

- GitHub Spec Kit docs: https://github.github.com/spec-kit/
- GitHub Spec Kit repository: https://github.com/github/spec-kit

