# Documentation Index

Repository-local documentation is the system of record for agents and humans.
Start with the smallest relevant document and follow links only as needed.

## Core Maps

- [../AGENTS.md](../AGENTS.md): agent entrypoint and working rules.
- [../ARCHITECTURE.md](../ARCHITECTURE.md): package map, runtime flow, and
  dependency boundaries.
- [PLANS.md](PLANS.md): execution-plan policy.
- [QUALITY_SCORE.md](QUALITY_SCORE.md): current maintainability scorecard.
- [RELIABILITY.md](RELIABILITY.md): build, runtime, and validation guidance.
- [SECURITY.md](SECURITY.md): security model and assumptions.

## Structured Knowledge

- [design-docs/index.md](design-docs/index.md): design notes and principles.
- [exec-plans/index.md](exec-plans/index.md): active and completed plans.
- [exec-plans/tech-debt-tracker.md](exec-plans/tech-debt-tracker.md): tracked
  cleanup work.
- [generated/README.md](generated/README.md): generated documentation policy.
- [product-specs/index.md](product-specs/index.md): user-facing behavior and
  scope.
- [references/harness-engineering.md](references/harness-engineering.md):
  source notes from the OpenAI harness-engineering article.

## Maintenance Rules

- Keep `AGENTS.md` as a table of contents, not a manual.
- Add or update docs when changing architecture, system properties, validation
  commands, or runtime contracts.
- Prefer checks in Gradle or scripts when a rule can be enforced mechanically.
