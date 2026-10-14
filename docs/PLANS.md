# Plans

Use execution plans for changes that cross more than one boundary, alter Vulkan
runtime contracts, or need multi-step validation.

## Lightweight Work

For small local changes, use a short in-thread checklist and update the relevant
docs if behavior changes.

## Execution Plans

Create a file in `docs/exec-plans/active/` when work includes any of these:

- Vulkan feature, extension, or API requirements.
- Meshlet storage layout or GPU buffer ownership.
- Mixin target changes across multiple Minecraft classes.
- Build, packaging, or source-set changes.
- Runtime validation that must be repeated later.

When a plan is finished, move what stays true into `ARCHITECTURE.md`,
`docs/RELIABILITY.md` or `docs/exec-plans/tech-debt-tracker.md`, list it in
`docs/exec-plans/active/README.md` no longer, and delete it. Git keeps its
history.

## Plan Template

```markdown
# Short Plan Title

## Goal

## Constraints

## Steps

## Validation

## Decisions

## Follow-Up
```
