# Documentation Index

Repository-local documentation is the system of record for agents and humans.
Start with the smallest relevant document and follow links only as needed.

## Core Maps

- [../AGENTS.md](../AGENTS.md): agent entrypoint and working rules.
- [../ARCHITECTURE.md](../ARCHITECTURE.md): package map, runtime flow, and
  dependency boundaries.
- [PLANS.md](PLANS.md): execution-plan policy.
- [RELIABILITY.md](RELIABILITY.md): build, runtime, and validation guidance.
- [SECURITY.md](SECURITY.md): security model and assumptions.

## Structured Knowledge

- [design-docs/index.md](design-docs/index.md): design notes and principles.
- [exec-plans/active/README.md](exec-plans/active/README.md): plans in progress.
- [exec-plans/tech-debt-tracker.md](exec-plans/tech-debt-tracker.md): tracked
  cleanup work.
- [product-specs/index.md](product-specs/index.md): user-facing behavior and
  scope.
- [references/minecraft-26-4-mixin-targets.md](references/minecraft-26-4-mixin-targets.md):
  the Mojang classes and members the client mixins touch, as verified with mcdev.
- [references/minecraft-26-4-renderer-domain-owners.md](references/minecraft-26-4-renderer-domain-owners.md):
  the Mojang class that owns the vanilla path of each renderer domain.

## Maintenance Rules

- Keep `AGENTS.md` as a table of contents, not a manual.
- Add or update docs when changing architecture, system properties, validation
  commands, or runtime contracts.
- Prefer checks in Gradle or scripts when a rule can be enforced mechanically.
