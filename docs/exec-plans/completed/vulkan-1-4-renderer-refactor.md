# Vulkan 1.4 Renderer Refactor — Completed

## Outcome

Phase 1 foundation and the terrain-path hardening increment are complete on **Minecraft 26.2-pre-3** / **Fabric API 0.150.2+26.2** / **Fabric Loader 0.19.2**.

Delivered:

- Renderer lifecycle, domain registry, runtime profile, and terrain GPU layout contracts.
- Work-queue + GPU-generated indirect mesh-task command path for visible and full-layer modes.
- Terrain read fences wired from successful mesh draws; `vim.waitIdleBeforeTerrainUpload` remains validation-only.
- CPU visible-meshlet ring gated behind `vim.allowCpuVisibleMeshletFallback` (default `false`).
- Prepared-dispatch queue leak diagnostics at terrain group end.
- Non-terrain domains observed as vanilla; terrain reports GPU work queues via `RendererDomainObserver`.
- Bug-report dump via Debug key `K` (`RendererDiagnostics.logBugReport()`).
- Mechanical `check` gates and GPU validation matrix template in `docs/RELIABILITY.md`.

## Follow-Up (outside this plan)

- Full runtime smoke matrix with Vulkan validation layers on NVIDIA/AMD/Intel.
- Non-terrain domain incremental replacement.
- Build-time SPIR-V pipeline for terrain shaders.