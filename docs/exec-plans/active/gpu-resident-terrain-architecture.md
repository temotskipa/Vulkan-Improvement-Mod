# GPU Resident Terrain Architecture

**Status:** Active.

## Goal

Replace the current per-draw mesh-terrain diagnostic path with a
GPU-resident terrain architecture that keeps authoritative Minecraft chunk
state on the CPU, keeps vanilla terrain visible until coverage is proven, and
moves visibility, compaction, and command production toward persistent GPU
state.

## Architecture

The renderer should use persistent section pages derived from CPU-authoritative
chunks, then update those pages through dirty-event updates. Camera movement
should primarily update uniforms and trigger GPU traversal; the target
architecture is not one CPU work-queue upload per vanilla terrain draw.

The first contract-level code for this direction is:

- `GpuWorldResidencyBudget`, which exposes the explicit residency budget for
  canonical section mirrors, render-only LOD pages, predictive distant pages,
  visibility compaction work, and indirect command generation work.
- `GpuWorldResidencyStats`, which reports live page counts, used resident
  bytes, eviction totals, and over-budget refusals by page kind.
- `GpuWorldVisibilityCommandStats`, which reports visibility compaction input,
  culled, and visible section counts plus emitted, overflowed, and fallback
  indirect command counts without granting vanilla-cancellation authority.
- `GpuWorldPipelineStage`, which documents the pipeline stages that move from
  authoritative ingest to persistent residency, visibility compaction, and
  indirect command generation.
- `GpuWorldTerrainLayerDirectory`, which exposes resident terrain layer records
  keyed by section node and layer ordinal instead of transient vanilla draw
  objects.
- `GpuWorldDatabase.asMap()`, which reports the residency budget, residency
  usage stats, pipeline stages, resident terrain layer directory, and existing
  page-kind, authority, material-table, and dirty update diagnostics.

## External Reference Boundary

External source links and named reference projects stay in ignored local notes under `docs/local/`. This public plan records only the inferred architecture: GPU-resident terrain section management, memory budgeting, visibility, and GPU-driven command production.

Do not copy or translate external source code, shader code, class names,
internal layouts, constants, or control flow. Use only independently described
architecture concepts and re-express them in this project's Vulkan contracts.

## Optional Worldgen Provider Boundary

Optional accelerated world-generation providers remain integration evidence,
not renderer dependencies. Do not add an optional provider to `fabric.mod.json`
as a hard dependency before a dedicated integration plan exists.

Runtime distribution discovery, provider detection, source links, and
provider-specific compatibility notes stay out of public plans until a
dedicated integration plan decides what belongs in committed docs.

## Constraints

- CPU-authoritative Minecraft chunk state remains the source of truth.
- Canonical GPU-world pages mirror CPU chunk state and are never labeled
  gameplay-authoritative.
- Render-only LOD and predictive distant pages stay separate from canonical
  chunk mirrors.
- `vim.strictMeshTerrainReplacement=false` remains the default.
- strict cancellation remains validation-only until runtime evidence proves
  complete visible solid/cutout coverage with no task truncation, no command
  overflow, no prepared-dispatch leaks, no texture regression, and no state
  leak into vanilla rendering.
- vanilla terrain remains visible while the GPU path is incomplete.
- translucent terrain, underwater overlays, and environment screen effects stay vanilla until their compatibility contracts exist.
- OpenGL-active sessions must not initialize VIM renderer services.
- Dependency pins remain stable Minecraft 26.2 unless a separate version-port
  plan is opened.

## Resident Page Ownership

GPU-world residency needs one renderer-owned page manager for terrain pages.
The page manager owns the page ID lifecycle, allocation, retire, and free
sequence. `GpuWorldSectionUpdate` revisions identify the CPU-authoritative
source revision that each canonical page mirrors, while render-only LOD pages,
predictive pages, visibility buffers, and command buffers keep separate
canonical-vs-LOD authority labels.

Resident page diagnostics must distinguish allocated bytes, used bytes,
fragmentation, live page count, evictions, over-budget refusals, and pending
retirements. Page memory can be reused only after resource retirement proves no
in-flight GPU work still references the old page.

## Queue Overflow Policy

Every GPU append target and generated-command stream needs a documented soft
limit, hard limit, frame behavior, fallback path, and diagnostics. This includes
visible-section outputs, traversal request queues, opaque/cutout command
buffers, future translucent buckets, and fallback command paths.

Overflow behavior must prefer vanilla terrain visibility over strict
replacement. If a queue saturates, the frame may skip the GPU replacement path,
clamp diagnostic output, or schedule a rebuild, but it must report the queue,
limit, dropped or fallback count, and fallback reason.

## Synchronization Model

Terrain residency uploads, command generation, diagnostic readback, and page
retirement must be frame-delayed and fence-backed. Upload streams and GPU
barriers move dirty section data into resident pages; readback streams expose
diagnostics after the relevant frame has retired.

Runtime decisions must not depend on immediate GPU readback. Diagnostic
readback must not be frame-critical, and resource frees or page decommit must
wait until the retirement service proves the page is no longer referenced by
submitted GPU work.

## Invalidation Matrix

Dirty-event ingestion must cover block updates, neighbor exposure changes,
section unloads, render-distance changes, resource reloads,
material-table revision changes, world clear/device loss, and LOD parent/child
propagation.

LOD parent/child propagation must be explicit rather than relying on a full
cache reset.

Each invalidation source must state which page classes are stale, whether the
page can be patched or must be rebuilt, how the `GpuWorldSectionUpdate`
revision changes, and which fallback reason is reported while the resident page
is missing or stale.

## Material And Texture Compatibility

The block atlas/lightmap source of truth remains Minecraft's resource state.
GPU material records must preserve render layer, atlas identity, lightmap use,
per-face tint or color metadata, alpha/cutout policy, sampler and mip state,
and fallback classification.

Resource reloads and resource-pack revision invalidation must retire or rebuild
affected material and page records before strict cancellation can use them.
Texture diagnostics must include sampler filtering, mip limits, anisotropy,
atlas revision, and fallback material counts.

## Traversal Diagnostics

GPU traversal and compaction must report HIZ built/invalid dimensions,
traversal input count, output count, request queue saturation, visibility frame
IDs, and fallback reason when traversal data is incomplete. Visibility counters
are validation evidence only until they are tied to resident page revisions and
material compatibility.

Generated-command readiness is separate from mesh dispatch success. Strict
vanilla cancellation requires complete visible coverage, no queue overflow, no
stale page revisions, and material compatibility, not only successful indirect
dispatch.

Strict terrain replacement allows no stale page revisions.

## Steps

1. Keep the current mesh terrain path as overlay/fallback diagnostics.
2. Extend GPU-world diagnostics with an explicit residency budget, residency
   usage stats, and pipeline stage map.
3. Route new terrain work toward persistent section pages instead of transient
   per-draw queues.
4. Add GPU visibility compaction counters before allowing GPU compaction to
   affect vanilla cancellation.
5. Add indirect command generation from compacted GPU visibility output.
6. Gate strict vanilla terrain cancellation on complete coverage diagnostics,
   not on successful mesh dispatch alone.
7. Keep optional accelerated-worldgen providers as dependency detection and
   diagnostics only until a dedicated integration plan defines loader metadata,
   warnings, and world-backup behavior.

## Validation

- `.\gradlew.bat checkGpuResidentTerrainArchitectureInvariants --console=plain`
- `.\gradlew.bat checkGpuWorldDatabaseInvariants --console=plain`
- `.\gradlew.bat checkDependencyBaselineInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- `.\gradlew.bat build --console=plain`

Runtime validation is required before promoting any replacement path. It must
use the record-first procedure from the mesh bugfix plan: schedule delayed
camera jitter, then call `mc_record_video` with a long recording interval.

## Decisions

- The current visible mesh path is diagnostic scaffolding, not the final strict
  replacement architecture.
- Persistent GPU-world state, visibility compaction, indirect command
  generation, and explicit residency budget diagnostics are the next mainline
  architecture direction.
- Residency usage stats are a diagnostic contract now; byte-accurate resident
  allocation values are filled in when real GPU-world page allocation owns the
  backing memory.
- Strict terrain cancellation cannot be enabled by default until the GPU path
  proves coverage and vanilla parity under record-first jitter captures.
- Optional accelerated-worldgen providers are an authoritative-worldgen
  integration track, not the renderer's default source of world authority.

## Exit Criteria

This plan is complete when:

- GPU-world diagnostics report residency budget usage, page counts, evictions,
  over-budget refusals, visibility compaction counters, and indirect command
  generation counters.
- Terrain updates feed persistent section pages through dirty-event updates.
- Visibility compaction and indirect command generation use GPU-resident inputs
  instead of per-draw CPU work-queue uploads.
- Strict replacement is either proven by runtime captures and promoted through
  a separate default-change plan, or it remains validation-only with vanilla
  fallback intact.
- Accelerated-worldgen provider integration remains optional until a dedicated
  integration plan is implemented.

## Follow-Up

- Connect residency byte counters to real GPU allocation sizes once allocation
  owns real GPU-world pages.
- Add GPU compaction counters for input sections, culled sections, visible
  sections, emitted commands, overflowed commands, and fallback commands.
- Use the dedicated
  [accelerated-worldgen provider integration](accelerated-worldgen-provider-integration.md)
  plan before changing loader metadata or runtime dependency behavior.
