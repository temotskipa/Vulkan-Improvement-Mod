# GPU Resident Terrain Architecture

**Status:** Completed 2026-10-14. Read the Completion section for what was
built and what was left out on purpose.

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
  plan is opened. (It was: the plan was carried out on `26.4-snapshot-3`.)

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

## Completion

Closed on 2026-10-14, implemented on `26.4-snapshot-3`. By then the maintainer
had decided that the mod extends Minecraft's renderer and takes the frame's
visible sections from Minecraft; the plan was carried out within that.

What was built, against the exit criteria:

- **Persistent pages fed by dirty events.** A captured section layer gets a
  page (`ResidentTerrainPages`, `TerrainRangeAllocator`): a slot in the page
  table and a range of the vertex payload, the index payload and the meshlet
  headers. Capturing a section layer again replaces its page; releasing it
  gives the page back; nothing else is uploaded. Before, any change rewrote
  all terrain after waiting for the GPU.
- **Synchronization.** Pages reach device-local buffers through a staging
  buffer and copies recorded in the command stream. Nothing waits on a fence,
  and a freed range can be reused at once, because the copies run after every
  draw recorded before them. Uploads are budgeted per frame.
- **Visibility compaction and command generation from GPU-resident inputs.**
  A frame uploads, per layer, the page slot and fade-in of each section layer
  Minecraft collected: eight bytes each. A compute pass reads that list and
  the page table, culls against the view frustum, appends the surviving pages
  to the layer's draw list and counts up the mesh-task command. The task stage
  draws a page per task and emits a mesh task per meshlet. The per-meshlet CPU
  work queue is no longer on this path; it remains behind
  `vim.enableGpuGeneratedMeshTaskCommands=false` and as the fallback for a
  frame whose command could not be prepared.
- **Diagnostics.** `gpuWorldDatabase.residentTerrain` reports live pages, used
  and capacity bytes and allocation refusals from the allocators;
  `sectionStore.residentPages` adds pages handed out and given back and the
  fragmentation of each buffer; `visibilityCommandStats` is fed from counters
  the GPU keeps and is read without making a frame wait for it.
- **Overflow.** The draw list of a layer has room for every page that was
  listed, so the pass cannot overflow it. A layer larger than the mesh-task
  limit of one draw is split. When a ring has no room or a page does not fit,
  the layer is left to Minecraft for the frame and the fallback is counted.
- **Strict replacement** stays a validation mode with vanilla as the default.
  Coverage is decided on the CPU before the frame is drawn: a layer replaces
  Minecraft's draws only when every section layer listed for it has a page.
- **Invalidation.** Block updates and neighbour changes arrive as a new
  capture of the section layer; unloads as a release; render-distance changes,
  resource reloads, level changes and device loss clear the pages; growth of a
  buffer re-uploads them.

Left out on purpose, each a possible follow-up:

- **GPU traversal and occlusion.** There is no hierarchical depth test on the
  GPU. Which sections a frame may draw is Minecraft's decision; the GPU pass
  only removes what is outside the view from that list.
- **Level-of-detail and predictive pages.** Only canonical mirrors of
  Minecraft's section meshes are resident.
- **Eviction by budget.** A page leaves when Minecraft releases its section,
  not because a budget is exceeded. A buffer that is too small grows.
- **Growth without the CPU copies.** Growing a buffer re-uploads from copies
  kept on the CPU. Copying on the GPU instead would let those copies go.

Runtime evidence is the 2026-10-14 row of the GPU validation matrix in
`docs/RELIABILITY.md`.
