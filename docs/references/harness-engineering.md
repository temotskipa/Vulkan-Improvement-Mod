# Harness Engineering Reference

Source: <https://openai.com/index/harness-engineering/>

The relevant operating model for this repository is:

- Keep `AGENTS.md` short and use it as a table of contents.
- Put durable repository knowledge in versioned docs.
- Make the codebase legible to agents with explicit maps, boundaries, and
  validation commands.
- Prefer mechanical checks for rules that can drift.
- Track plans and technical debt as repository artifacts.
- Clean up continuously in small increments instead of waiting for large manual
  rewrites.

This repository applies those ideas through `AGENTS.md`, `ARCHITECTURE.md`, the
structured `docs/` directory, and the Gradle `checkRepoDocs` task.
