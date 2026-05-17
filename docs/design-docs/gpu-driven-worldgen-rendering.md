# GPU-Driven Worldgen And Rendering Feasibility

## Summary

Fully GPU-driven rendering is feasible and should be a central goal of the
Vulkan architecture. Minecraft is heavily CPU-limited, so the renderer should
shift every render-derived task to the GPU unless doing so would break gameplay
authority, vanilla parity, datapack behavior, or broad mod compatibility.
Fully GPU-authoritative vanilla world generation is not feasible as the default
behavior of a Fabric client mod without crossing those boundaries.

The practical target is a unified Vulkan architecture with three layers:

1. CPU-authoritative Minecraft world state remains the source of truth.
2. A GPU-resident world database mirrors, compresses, and derives render data
   from chunks, sections, entities, lighting, biome data, and material data.
3. GPU compute, mesh/task shaders, indirect draw, descriptor buffers, and
   optional device-generated commands drive visibility, LOD, mesh generation,
   material binding, and draw submission.

GPU-assisted world generation is feasible for render-only distant terrain,
height/noise previews, LOD fill, occlusion data, and custom opt-in worlds. It
should not be the default source of gameplay chunk state.

## Future RT/PT Readiness

The GPU-driven renderer should make later ray tracing and path tracing work
easier, but only if the shared GPU data model is designed as a renderer-wide
source of truth rather than a terrain-only draw cache.

Future RT/PT paths need the same inputs as rasterization:

- stable world, section, entity, and block entity identities;
- compact geometry pages or meshlets with bounds and revision numbers;
- material IDs that preserve Minecraft render layers, alpha modes, emission,
  tinting, lightmaps, animation, and resource-pack overrides;
- texture handles that resolve back to Minecraft atlases and model textures;
- dirty-event updates when chunks, blocks, block entities, entities, resource
  packs, models, or video settings change.

This does not mean the first renderer must build acceleration structures. It
does mean the architecture should avoid baking assumptions that make RT/PT a
separate renderer with separate asset conversion, separate material IDs, or
separate world-state invalidation.

The likely path is:

1. Use the GPU world database and material tables for rasterization first.
2. Add optional geometry export buffers for acceleration-structure builds after
   meshlet and LOD data are stable.
3. Build BLAS-like geometry inputs from the same chunk/entity/model pages used
   by raster passes.
4. Build TLAS-like instance inputs from the same visibility/entity registry used
   by raster passes.
5. Add hybrid RT features first: shadows, reflections, ambient occlusion, or GI
   probes.
6. Treat full path tracing as a separate experimental mode with explicit
   compatibility and performance limits.

RT/PT should consume the renderer's canonical GPU asset representation, not
replace it.

## Radiance Design Reference

Radiance is a useful design reference because it is already trying to solve the
Minecraft Vulkan + hardware RT problem as a real mod. The public repository
split is important:

- The Java mod repository handles Fabric integration, options, pipeline-module
  configuration, texture/resource hooks, and JNI-facing glue.
- The native MCVR repository owns the C++ Vulkan renderer, shader tree, ray
  tracing, denoising, upscaling, and post-processing implementation.
- The Java module system describes passes with named inputs, outputs, image
  formats, and attributes. Current modules include ray tracing, NRD denoising,
  temporal accumulation, tone mapping, DLSS, FSR, XeSS, and post-render steps.
- The ray-tracing module emits radiance, albedo/metallic, specular albedo,
  normal/roughness, motion vector, depth, hit-depth, direct/indirect light,
  emission, fog, and refraction buffers.
- The texture compatibility layer tracks auxiliary PBR textures such as
  specular, normal, and flag maps, loads resource-pack sidecar textures, uploads
  matched auxiliary atlases, and records emission tiles.

Design ideas to keep:

- Treat RT/PT as a module family with explicit image contracts instead of a
  one-off shader path.
- Keep denoising, temporal accumulation, tone mapping, and upscaling as
  replaceable post-render modules.
- Track PBR and emission information at texture upload/resource-reload time so
  ray tracing does not need to rediscover material facts every frame.
- Use explicit module attributes for quality, jitter, radiance cache, denoiser,
  and debug behavior.

Design ideas to avoid or isolate:

- Do not make native C++ the only architectural route unless Java/LWJGL cannot
  expose a required Vulkan feature. This project currently owns a Java Fabric
  renderer path and should keep JNI/native code as an explicit tradeoff.
- Do not require DLSS or vendor runtime libraries for startup. Optional vendor
  modules must fail closed and fall back cleanly.
- Do not let path-tracing material conventions replace Minecraft compatibility.
  PBR sidecars are additive; vanilla and modded assets still need defined
  fallback material records.

## Minecraft Asset Compatibility

The renderer must remain compatible with Minecraft's texture, model, resource
pack, and modded rendering expectations. GPU-driven rendering cannot require
content authors to ship a new asset format.

Required compatibility rules:

- Preserve Minecraft's block model, item model, entity model, block entity
  model, particle sprite, atlas, animation, tint, lightmap, overlay, and render
  layer semantics.
- Treat resource-pack reloads as full material/model/texture revision changes
  that invalidate affected GPU pages and descriptors.
- Keep model baking and mod/datapack/model callback behavior CPU-side until a
  safe representation exists; GPU work should consume baked or translated data.
- Maintain a fallback path for assets whose geometry, shader behavior, or
  dynamic render state cannot yet be represented in the GPU-driven path.
- Preserve vanilla cutout, translucent, emissive, biome-tinted, animated,
  connected, and custom model behavior before enabling strict replacement for a
  domain.
- Use the Minecraft texture atlas and model registry as the source of truth for
  material IDs and descriptor-table entries.

The compatibility layer should produce renderer-neutral material records. Raster
passes, mesh shaders, indirect draws, RT/PT acceleration builds, and debug tools
should all consume those same records.

## RT-Derived Gameplay Lighting

RT can eventually improve gameplay simulation, but this is a different contract
from visual ray tracing. Minecraft gameplay lighting is authoritative server
state. For example, monster spawning currently checks sky light, block light,
and raw local brightness through server-side spawn rules before allowing normal
monster spawns.

That means a renderer must not silently replace vanilla light values used by
gameplay. A more realistic gameplay-lighting path should be an explicit
simulation feature with its own compatibility rules.

Feasible uses:

- Client-side diagnostics that compare vanilla light to RT-derived illuminance.
- Single-player or integrated-server experimental mode that writes a bounded,
  quantized lighting field back into gameplay rules.
- Server-side opt-in API for mods that want physically inspired light queries.
- More realistic mob spawning, plant growth, freezing/melting, redstone-like
  sensors, or block behavior only when a world/server explicitly enables it.

Required boundaries:

- Multiplayer servers remain authoritative. A client renderer cannot decide mob
  spawning, crop growth, or other gameplay outcomes.
- Gameplay light must be deterministic or at least server-reproducible enough
  for saves, debugging, and anti-cheat expectations.
- RT/PT sampling noise and temporal accumulation cannot directly drive gameplay
  rules. Any gameplay-facing result needs stable quantization, hysteresis, and
  bounded update cadence.
- Vanilla light and RT-derived light should coexist as separate fields until a
  world type or server rule intentionally remaps gameplay logic.
- Mods and datapacks must be able to detect whether realistic-light simulation
  is active and query the intended light field explicitly.

The recommended architecture is to make RT-derived gameplay lighting a later
server/simulation layer that consumes the same GPU or CPU-visible light probes,
not a hidden side effect of the visual renderer.

## CPU Offload Principle

The CPU should become an authority and mutation producer, not the owner of
per-frame renderer work. In the mainline architecture, CPU work should be
limited to receiving Minecraft state, validating compatibility boundaries,
emitting compact dirty updates, and submitting a small number of high-level
Vulkan passes.

GPU work should own:

- chunk-section transcoding into compact render pages;
- terrain meshlet generation from mirrored authoritative chunk data;
- LOD page generation and distant render-only terrain fill;
- frustum, occlusion, HIZ, and portal/visibility-style section culling;
- entity, block entity, item, and particle visibility compaction where model
  state can be represented safely;
- material and descriptor lookup through GPU-visible tables;
- indirect draw, mesh-task dispatch, and optional device-generated command
  records;
- debug counters and statistics that do not require synchronous readback.

CPU work should remain responsible for:

- authoritative gameplay chunk state, server-visible worldgen, saving, and
  multiplayer trust boundaries;
- mod/datapack callbacks that depend on Java object graphs or registry state;
- resource-pack ingestion, model baking, compatibility classification, and
  fallback decisions;
- enqueueing dirty ranges when chunks, entities, resources, options, or worlds
  change.

The design should avoid CPU iteration over all visible chunks, entities, block
entities, particles, or LOD nodes each frame. Those loops are exactly the work
that should become GPU queues, GPU scans, or persistent GPU state updated by
dirty events.

## Terms

- GPU-driven rendering: the CPU submits a small number of high-level frame
  commands while GPU stages perform visibility, culling, work compaction,
  mesh/task dispatch, indirect draw, and material lookup.
- GPU-assisted world generation: GPU compute produces render-only or derived
  world data such as distant terrain, height fields, LOD meshes, ambient probes,
  or transient mesh data from authoritative CPU chunks.
- GPU-authoritative world generation: the GPU produces canonical chunk block
  states, structures, features, fluids, heightmaps, lighting inputs, save data,
  and gameplay-visible state.

## Current Minecraft Boundary

The Minecraft generation pipeline remains server-side and CPU-owned in the
target snapshot inspected through mcdev:

- `ChunkStatusTasks.generateNoise` builds a `WorldGenRegion` and calls
  `ChunkGenerator.fillFromNoise(...)`.
- `NoiseBasedChunkGenerator.fillFromNoise(...)` runs asynchronous background
  work and locks `LevelChunkSection` objects.
- `NoiseBasedChunkGenerator.doFill(...)` iterates cells and writes `BlockState`
  values into `ChunkAccess` sections, updates heightmaps, and marks fluid
  post-processing.
- Client render-region code reads block states, fluid states, light engines,
  tint data, and block entities from CPU-side chunk copies.

That shape means a client renderer can mirror and accelerate render data, but it
cannot silently replace canonical chunk generation for gameplay or multiplayer.
Any default GPU path must be a derived cache with explicit invalidation and
fallback.

## Existing Mod Precedents

The useful lesson from existing projects is that the problem has already been
solved in pieces, but not as one unified Vulkan architecture.

- Nvidium demonstrates the closest existing shape to GPU-driven terrain. Its
  world renderer owns upload and download streams, a section manager, a render
  pipeline, an asynchronous occlusion tracker, GPU terrain memory budgeting, and
  Sodium chunk-build ingestion. Its terrain shaders use mesh/task shader style
  work distribution, subgroup operations, bindless texture access, screen-space
  triangle rejection, and per-section visibility decisions. The limits are just
  as important: the implementation is OpenGL/NVIDIA-extension based, terrain
  focused, and not a Vulkan renderer-wide domain model.
- VulkanMod demonstrates that a broad Minecraft Vulkan renderer can cover more
  than terrain. Its renderer packages are split across chunk, engine, model,
  shader, sky, texture, vertex, and profiling areas. Its world renderer owns
  section grids, section graphs, chunk build dispatch, render region caching,
  indirect buffers, terrain-layer rendering, translucent sorting, and block
  entity render-state extraction. This is useful architecture evidence for
  renderer-wide ownership, but it still depends heavily on CPU chunk building
  and vanilla/Minecraft render-state flow for non-terrain domains.
- Voxy demonstrates the right direction for render-only world databases and
  distant-world rendering. Its world engine tracks LOD-section identities,
  section storage, dirty/save callbacks, and active-section references. Its
  render-generation service is still CPU/service-thread based, but it can emit
  meshlets and decouples render data from ordinary chunk sections. Its shaders
  show GPU hierarchical traversal, HIZ/frustum culling, render/request queues,
  visibility buffers, and compute-generated indirect draw commands.
- C2ME demonstrates the compatibility boundary for generation. It improves
  chunk generation, I/O, and loading through CPU parallelism while treating
  vanilla behavior, datapacks, and mod compatibility as first-order constraints.
  That supports the decision to keep authoritative generation CPU-owned for the
  default path.
- Sodium demonstrates the importance of renderer correctness, broad hardware
  compatibility, and vanilla parity when replacing client rendering.
- Iris demonstrates how compatibility goals can dominate renderer design,
  especially once shader packs and modded render paths enter the picture.

The unified Vulkan architecture should combine these lessons: VulkanMod's broad
renderer domain coverage, Nvidium's GPU-resident terrain dispatch model, Voxy's
LOD world database and GPU command generation, and C2ME's strict boundary around
authoritative worldgen behavior.

## Feasibility Matrix

| Capability                                          | Feasibility | Default Path             | Notes                                                                                                  |
|-----------------------------------------------------|-------------|--------------------------|--------------------------------------------------------------------------------------------------------|
| GPU-driven frustum/occlusion culling                | High        | Yes                      | Use section, meshlet, entity, and particle work lists.                                                 |
| GPU-driven terrain draw dispatch                    | High        | Yes                      | Mesh/task shader and indirect dispatch are a natural fit.                                              |
| GPU mesh generation from CPU chunk sections         | High        | Yes, after parity checks | CPU chunks remain authoritative; GPU owns render mesh data.                                            |
| GPU material and descriptor binding                 | High        | Yes                      | Descriptor buffers fit a renderer-wide bindless-style material system.                                 |
| Minecraft texture/model compatibility               | High        | Yes                      | Use Minecraft baked assets as the source of truth and translate them into shared GPU material records. |
| GPU LOD terrain from CPU-ingested chunks            | High        | Yes                      | Use clipmaps or paged LOD regions.                                                                     |
| GPU-assisted render-only distant terrain generation | Medium-high | Optional                 | Useful for visual fill beyond generated chunks; must be labeled approximate.                           |
| GPU lighting for render probes/AO                   | Medium      | Optional                 | Good for visuals; not a replacement for gameplay light engine.                                         |
| GPU section data transcoding                        | High        | Yes                      | CPU uploads compact dirty input; GPU builds render pages and counters.                                 |
| GPU authoritative vanilla noise terrain             | Low         | No                       | Exact `ChunkAccess` mutation, heightmaps, fluids, structures, and mods make this hard.                 |
| GPU authoritative structures/features               | Very low    | No                       | Datapacks, registries, random streams, and mod hooks are CPU/object-heavy.                             |
| GPU authoritative custom world type                 | Medium      | Experimental only        | Feasible if the world type is explicitly designed around GPU kernels.                                  |
| GPU-driven entities and particles                   | Medium-high | Incremental              | Requires material, animation, sorting, and modded model boundaries.                                    |
| GPU-driven shader-pack compatibility                | Medium-low  | Later                    | Existing shader-pack formats assume a different pipeline.                                              |
| RT-derived gameplay lighting                        | Medium      | Experimental only        | Needs server authority, stable quantization, and explicit world/server opt-in.                         |

## Proposed Unified Vulkan Architecture

### Renderer Core

- Device profile and feature gates for Vulkan 1.4 plus extension-backed paths.
- Frame graph or pass scheduler with explicit pass dependencies.
- Shared buffer allocator, descriptor-buffer allocator, optional descriptor-heap
  path, and resource retirement queue.
- Shared shader module, pipeline, and pipeline-layout cache.
- Shared synchronization model for upload, compute, graphics, readback, and
  shutdown.
- Per-domain diagnostics with counters, memory, selected paths, and fallback
  reasons.

### GPU World Database

- Section pages keyed by dimension, chunk position, section Y, revision, and
  data role.
- Palette/material tables that map Minecraft `BlockState`, fluid state, biome,
  light, tint, and texture atlas data into compact GPU IDs.
- Model/material records keyed by Minecraft baked model, item model, entity
  model, block entity renderer, particle sprite, texture atlas revision, and
  resource-pack revision.
- Per-section occupancy, opacity, bounds, and dirty flags.
- Optional sparse clipmaps or region pages for distant LOD.
- Append/consume queues for updates from chunk rebuilds, block changes, resource
  reloads, and world unloads.
- Separate canonical section mirrors from render-only predictive pages so
  approximate GPU-generated distant terrain can never be mistaken for gameplay
  chunk state.

### GPU Work Pipeline

1. CPU ingests authoritative chunks and mutations into staging buffers.
2. GPU compute converts staging data into render-friendly pages.
3. GPU compute builds or updates terrain meshlets, LOD blocks, section bounds,
   visibility data, material references, and debug counters.
4. GPU culling compacts visible work into draw or mesh-task records.
5. Mesh/task shaders render terrain and other meshlet-compatible domains.
6. Indirect or device-generated commands submit domain draws when supported.
7. Small readbacks are allowed only for diagnostics, profiling, and cache
   health, not for gameplay-critical loops.

The work pipeline should be domain-agnostic. Terrain is the first producer of
meshlets and section pages, but entities, block entities, items, particles, sky,
clouds, weather, and later effects should enter the same visibility, material,
descriptor, and diagnostics systems as soon as their correctness boundaries are
mapped.

CPU submission should scale with changed data and pass count, not with world
size. A camera move should primarily update uniforms and trigger GPU traversal,
not rebuild CPU draw lists for all visible world content.

RT/PT acceleration data should be treated as another derived product of this
pipeline. If it is added later, it should be invalidated by the same dirty
events and built from the same geometry and material pages as raster rendering.

### Worldgen Integration Levels

1. Mirror-only: authoritative CPU chunks are mirrored to GPU render pages.
2. Derived render data: GPU builds meshlets, LOD meshes, visibility, and visual
   lighting from mirrored chunks.
3. Predictive/visual worldgen: GPU generates distant approximate terrain or
   height fields beyond loaded chunks, clearly separated from gameplay state.
4. Custom GPU world type: GPU kernels produce canonical chunks only for a
   deliberately opt-in world mode with strict limitations.

The first two levels should be the mainline architecture. The third is useful
for modern render distance expectations. The fourth is research, not a default
mod feature.

## Required Vulkan Capabilities

Hard requirements for the unified renderer should include:

- Vulkan 1.4 device support.
- Buffer device address.
- Descriptor buffer path.
- Mesh shader path for terrain and other meshlet-compatible domains.
- Storage buffers, atomics, robust access, and synchronization features needed
  for GPU compaction and work lists.
- Dynamic rendering and explicit synchronization for pass scheduling.

Optional acceleration paths:

- Device-generated commands.
- Descriptor heap.
- Async compute queue.
- Fragment shading rate.
- Multi-draw or draw-indirect-count style paths where mesh shaders are not the
  selected path.
- Graphics pipeline library or shader object for pipeline warmup.

## Risks

- Server authority: multiplayer worlds cannot trust client-generated chunks.
- Vanilla parity: exact worldgen requires CPU object graphs, registries,
  datapacks, structures, feature placement, fluid post-processing, heightmaps,
  and light integration.
- Mod compatibility: custom generators, block states, render layers, models,
  shader packs, and resource packs can all violate assumptions.
- Readback latency: GPU-generated canonical chunks would need expensive readback
  before the server can simulate or save them.
- Memory pressure: GPU world databases, LOD clipmaps, descriptor buffers, and
  meshlet payloads can grow quickly at modern render distances.
- Driver coverage: mesh shaders, descriptor heap, and device-generated commands
  are not universal even when Vulkan 1.4 is available.
- Debuggability: unified GPU pipelines need strong counters, captures, and
  fallback modes to be supportable.

## Recommended Roadmap

1. Stabilize the current terrain renderer and build a renderer-wide Vulkan core.
2. Build the GPU world database from authoritative CPU chunks.
3. Move terrain meshing, visibility, culling, and draw dispatch onto GPU work
   queues.
4. Add GPU hierarchical traversal, HIZ/frustum culling, request queues, and
   compute-generated indirect or mesh-task work records.
5. Add LOD pages and render-only GPU-assisted distant terrain.
6. Add non-terrain domains: entities, block entities, items, particles, sky,
   clouds, weather, and post-processing handoff points.
7. Add optional device-generated commands and descriptor heap paths after the
   descriptor-buffer path is stable.
8. Investigate custom GPU-authoritative world types only after render-only GPU
   world data is stable and well instrumented.

## Decision

Pursue a GPU-driven renderer and GPU-assisted visual world pipeline. Do not make
GPU-authoritative vanilla world generation a default goal. Treat it as an
experimental custom-world research track with explicit compatibility limits.

## Sources

- Nvidium: `https://github.com/MCRcortex/nvidium`
- Nvidium renderer and shaders:
  `https://github.com/MCRcortex/nvidium/tree/dev/src/main`
- VulkanMod: `https://github.com/xCollateral/VulkanMod`
- VulkanMod renderer packages:
  `https://github.com/xCollateral/VulkanMod/tree/dev/src/main/java/net/vulkanmod/render`
- Sodium: `https://github.com/CaffeineMC/sodium`
- C2ME: `https://github.com/RelativityMC/C2ME-fabric`
- Voxy: `https://github.com/MCRcortex/voxy`
- Voxy LOD renderer and shaders:
  `https://github.com/MCRcortex/voxy/tree/dev/src/main`
- Iris: `https://github.com/IrisShaders/Iris`
- Radiance Java mod: `https://github.com/Minecraft-Radiance/Radiance`
- Radiance native renderer: `https://github.com/Minecraft-Radiance/MCVR`
- Radiance project site: `https://www.minecraft-radiance.com/`
- Vulkan mesh shader: `https://docs.vulkan.org/refpages/latest/refpages/source/VK_EXT_mesh_shader.html`
- Vulkan descriptor buffer guide:
  `https://docs.vulkan.org/guide/latest/descriptor_buffer.html`
- Vulkan device-generated commands:
  `https://docs.vulkan.org/spec/latest/chapters/device_generated_commands/generatedcommands.html`
- Vulkan platform and Vulkan 1.4 overview: `https://www.vulkan.org/`
