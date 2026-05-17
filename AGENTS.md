# Agent Guide

This file is the entrypoint for agents working in this repository. Keep it short:
open the linked documents for detail instead of expanding this file into a
manual.

## Project Shape

- This is a Fabric client mod for Minecraft focused on Vulkan terrain rendering
  improvements.
- Gradle uses Fabric Loom with split `main` and `client` source sets.
- Runtime Vulkan work lives under
  `src/client/java/com/temotskipa/vulkanimprovement/client/vulkan`.
- Mixin adapters live under
  `src/client/java/com/temotskipa/vulkanimprovement/mixin/client`.
- Mod metadata and mixin configs live in `src/main/resources` and
  `src/client/resources`.

## Where To Look

- [ARCHITECTURE.md](ARCHITECTURE.md) maps packages, runtime flow, and boundaries.
- [docs/index.md](docs/index.md) is the documentation table of contents.
- [docs/PLANS.md](docs/PLANS.md) explains when to create execution plans.
- [docs/QUALITY_SCORE.md](docs/QUALITY_SCORE.md) tracks maintainability gaps.
- [docs/RELIABILITY.md](docs/RELIABILITY.md) covers validation and runtime checks.
- [docs/SECURITY.md](docs/SECURITY.md) records security assumptions.
- [docs/exec-plans/tech-debt-tracker.md](docs/exec-plans/tech-debt-tracker.md)
  tracks cleanup work that should not be lost.

## Build And Check

- Use `.\gradlew.bat checkRepoDocs` for the repository-knowledge checks.
- Use `.\gradlew.bat build` for the full Gradle build.
- Use `.\gradlew.bat runClient` when a change needs Minecraft runtime
  validation.
- The project targets Java 25. Do not lower the Java target unless the mod's
  supported Minecraft/Fabric versions change.

## Working Rules

- Preserve the current boundary: mixins adapt Minecraft/Vulkan internals, while
  reusable rendering behavior belongs in `client.vulkan`.
- Keep `src/main` free of client renderer behavior unless Fabric metadata or a
  shared initializer truly requires it.
- When adding, renaming, or deleting mixins, update
  `src/client/resources/vulkanimprovement.client.mixins.json` in the same change.
- When changing Vulkan requirements, keep
  `VulkanFeatureRequirements`, `VulkanImprovementCapabilities`, and
  [ARCHITECTURE.md](ARCHITECTURE.md) aligned.
- Treat system properties in `TerrainRendererDebugConfig` as an operational API:
  document new flags in [docs/RELIABILITY.md](docs/RELIABILITY.md).
- Do not commit generated runtime state from `run/`, Gradle caches, or IDE files.

## Local Tooling Notes

- Prefer `rg` when available. On this machine it may be absent; use PowerShell
  `Select-String` or `Get-ChildItem -Recurse` as the fallback.

## Keeping The Repo Legible

- Update docs in the same change as architecture, build, or runtime-contract
  changes.
- Prefer small, mechanical checks over prose-only rules when a rule can drift.
- Capture durable findings in `docs/` rather than relying on chat history.
