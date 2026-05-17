# Core Beliefs

## Repository Knowledge Is The System Of Record

Decisions that matter later belong in the repository, not only in chat history.
Prefer short indexed documents over a single long instruction file.

## Boundaries Matter More Than Style

The important boundary is that mixins adapt external Minecraft and Vulkan
internals while reusable renderer behavior stays in `client.vulkan`.

## Checks Should Carry Rules

When a convention can be checked, encode it in Gradle or a script. Prose is for
judgment, context, and tradeoffs.

## Runtime Evidence Beats Assumptions

For renderer changes, compile success is not enough. Capture logs, capability
snapshots, and world-rendering observations whenever behavior changes.
