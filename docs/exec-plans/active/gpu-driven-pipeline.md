# GPU-Driven Pipeline

**Status:** Active. Started 2026-10-15 on `master`.

## Goal

Everything a frame draws lives on the GPU and is generated, culled and drawn
there, as in GPU-driven engines: the CPU runs the game and writes a few compact
records per object each frame, and none of its work grows with the number of
vertices. Terrain is GPU-driven already (see
[Mesh Terrain Performance](mesh-terrain-performance.md)). This plan brings
entities, block entities, items, particles and text into the same pipeline, and
then moves visibility and occlusion for all of it to the GPU.

All of it extends Minecraft's renderer, following the Design Principles in
[ARCHITECTURE.md](../../../ARCHITECTURE.md): the work lives in Minecraft's
buffers, command buffers, render types and submits, so other mods that use
those keep working.

## Baseline

A render-thread profile (JFR, fifteen seconds) of a GPU-driven terrain frame at
the overlook, render distance 32, 2026-10-15, same machine and settings as the
terrain plan. Shares are inclusive.

| Work                                                                   | Render thread |
|------------------------------------------------------------------------|---------------|
| Preparing entity and block-entity features (`prepareFrame`)            | 62 %          |
| of which building model vertices on the CPU (`ModelPart.Cube.compile`) | 44 %          |
| of which posing model parts (`ModelPart.translateAndRotate`)           | 6 %           |
| Collecting entities and block entities (`LevelExtractor.extract`)      | 14 %          |
| Collecting the frame's terrain (`extractSectionDrawGroups`)            | 11 %          |

Terrain is no longer what the render thread waits on. Writing entity vertices
one at a time is: 24 vertices of 36 bytes for every cube of every part of every
model, every frame, although a cube never changes after its model is baked.

## Constraints

- The Vanilla Parity Check in `docs/RELIABILITY.md` must still pass, with
  entities in the scenes.
- Nothing on the CPU waits for the GPU in a frame.
- Nothing is uploaded and no compute work is recorded while Minecraft's render
  pass is open.
- What the GPU path cannot reproduce exactly, or another mod has changed, stays
  on Minecraft's path, decided per submit, never for the whole frame.
- Every claim of a gain comes from the benchmark under Validation, Minecraft's
  path and the GPU path in the same session.

## Steps

1. Minecraft's backend extended for GPU-driven work. Buffers made by
   `GpuDevice.createBuffer` that shaders read and write by device address,
   through a buffer usage the mod adds, and compute dispatch on Minecraft's
   command encoder: checked by its frontend like Minecraft's own commands,
   recorded by its Vulkan backend into the frame's command buffer, and followed
   by the barrier Minecraft puts after its own transfers.
2. Model vertices from the GPU. Every model cube is uploaded once. Each frame
   Minecraft still poses a model's parts; the mod writes one record per part
   and one per cube, a compute pass writes the vertices into Minecraft buffers,
   and Minecraft draws them with its own render types, as draws of its own
   staged vertex buffer.
3. Model draws from mesh shaders. The mesh stage reads cubes and part poses
   directly, so no vertex buffer is written, and the task stage culls models
   outside the view.
4. Terrain visibility on the GPU: frustum and occlusion per section from the
   page table, Minecraft's section walk kept only for scheduling compiles, and
   translucent terrain on the mesh path, sorted on the GPU.
5. Items, block models, particles and text the same way: their quads resident,
   one record per instance.
6. Occlusion culling for everything the GPU draws, against a depth pyramid, in
   two phases so that nothing newly visible is missing for a frame.

Alongside the steps, Minecraft's backend improved in place, each change
measured with the benchmark on its own:

- Barriers by resource. Minecraft follows every transfer with a barrier on all
  commands before and after it; recording what each buffer and texture was
  last used for lets a command wait only for that.
- Framebuffers kept across frames. Minecraft creates one for every render pass
  and destroys it once the frame is done.
- Mapping kept. `GpuBuffer.map` maps and unmaps the allocation on every call.
- Terrain on Minecraft's objects. Terrain allocates through `DeviceBuffer` and
  records its compute passes itself; it moves onto Minecraft's buffers and the
  compute dispatch of step 1.

## Validation

The terrain plan's benchmark (ground, overlook and horizon, render distance 16
and 32), plus an entity scene: a few hundred animals in view, ticks frozen.
Parity screenshots of the same scenes, entities included. A render-thread
profile before and after each step.

## Progress

2026-10-15, steps 1 and 2. The entity scene: 400 animals and golems in view
above the overlook, 4,380 cubes in 3,790 posed parts a frame, render distance
12, ticks frozen; Minecraft's path and the GPU path (`vim.gpuModelVertices`) in
turn in one session, three times each, medians:

| Path      | FPS | Frame    | Render thread | 99th percentile frame |
|-----------|-----|----------|---------------|-----------------------|
| Minecraft | 404 | 2.47 ms  | 2.28 ms       | 4.02 ms               |
| GPU       | 778 | 1.29 ms  | 1.09 ms       | 2.10 ms               |

The render thread does half the work per frame: Minecraft still poses every
part, and the mod writes a 96-byte record per posed part and a 16-byte record
per cube where Minecraft wrote 24 vertices of 36 bytes. The compute pass that
writes the vertices is not timed yet; the frame rate nearly doubled, so the
frame does not wait on it here.

Parity: a dozen mobs, one in armour, and an armor stand side by side at close
range, ticks frozen, vignette and chat off. Frames with the path on and off are
identical wherever the models are; only water, which animates on its own,
differs. `checkModelVertexInvariants` compares the pass's arithmetic with
Minecraft's `Cube.compile` byte for byte on every build.

In these scenes every model submit took the GPU path. What stays on
Minecraft's: decals and remapped textures, render types in another vertex
format, translucent quads Minecraft sorts as it uploads them, subclasses of
`ModelPart.Cube`, and every model while another mod's mixins target a class
the path replaces.

2026-10-16, framebuffers kept across frames (`vim.keepFramebuffers`, on by
default). A render pass over the same views, size and Vulkan render pass as
one in an earlier frame gets that one's framebuffer; a framebuffer is
destroyed through Minecraft's destruction queue once one of its views closes,
and Minecraft destroys views only after they close. In the entity scene a
frame opens 13 render passes and, with the framebuffers kept, creates none
after the first. A window resize replaced the 12 whose views Minecraft
recreated. Frames are identical with and without, before and after a resize.
Six runs of each in turn, 8 s each: 0.02 ms a frame shorter, about 1 %, inside
the run-to-run spread. What it removes is 13 creations and 13 destructions of
Vulkan objects a frame, not a measurable share of this scene's frame time.

2026-10-16, barriers by resource (`vim.barriersByResource`, off by default).
The barrier Minecraft records after each clear, upload and copy is held back
until a command needs it: a transfer that conflicts with a held one, by
buffer range or whole texture, gets a barrier before it; anything else
recorded into the frame's command buffer, and the end of that command buffer,
gets the held one first. A frame records about 7 transfers, and 4 of their 7
barriers go. GPU time comes from Minecraft's own timer query. In the entity
scene it is 0.676 ms a frame either way. In the overlook without mobs while
every section reloads it falls from 0.58 to 0.53 ms and the frame rate rises
from 961 to 987 FPS (medians of four runs each, better in every pair). Frames
are identical. It stays off until a run with synchronization validation; the
validation layer is not installed on the test machine.

2026-10-16, step 3 (`vim.gpuModelMeshShaders`, off by default). While
Minecraft compiles a pipeline that draws the entity format in quads, its
vertex shader is compiled a second time, by Minecraft's compiler with the
pipeline's defines and includes, wrapped in a mesh stage: one workgroup per
cube, each vertex's inputs read from the cube's faces and its part's pose,
colour, overlay and light, then the vertex shader's own `main`, then its
outputs written for the vertex. Minecraft's reflection binds the mesh stage's
uniforms as it binds the pipeline's, the pipeline's descriptors and push
constants are opened to the mesh stage, and the mesh pipeline is built with
the pipeline's layout, fragment module and state for its render pass. A GPU
model draw whose pipelines all have one is drawn by it where Minecraft records
the draw, from the records alone; the others keep the vertex pass.
`checkModelMeshShader` compiles the mesh stage of Minecraft's entity shader
under the defines its pipelines use on every build.

In the entity scene, after a resource reload with both flags on, every one of
the 18 GPU model draws of a frame went through the mesh stage, and frames on
Minecraft's path, the vertex pass and the mesh stage are identical below the
debug overlay. Four runs of each in turn, medians:

| Path        | FPS | Frame    | Render thread | GPU time |
|-------------|-----|----------|---------------|----------|
| Minecraft   | 437 | 2.29 ms  | 2.10 ms       | 0.70 ms  |
| Vertex pass | 650 | 1.54 ms  | 1.35 ms       | 0.54 ms  |
| Mesh stage  | 647 | 1.54 ms  | 1.35 ms       | 0.52 ms  |

The mesh stage takes 0.02 ms less of the GPU than the vertex pass, less in
every pair, and the frame does not change: the render thread sets this
scene's pace. GPU time comes from Minecraft's timer query. These are the first
entity-scene numbers taken with every model confirmed drawn; the step 2
figures above (778 against 404 FPS) were not checked for that, and three of
the day's first five clients drew no GPU model at all (below). Step 3 has no
task stage yet, so models outside the view are not culled on the GPU.

2026-10-16, GPU model draws left out, fixed. Minecraft's
`StagedVertexBuffer.upload()` returns early when its draws hold no vertices of
its own, and the hook that writes the GPU model draws ran only at its last
return. With every model in view on the GPU path and nothing else in view
drawn from Minecraft's vertices, the draws were never written and Minecraft
skipped them all. Whether a natural mob holding an item, a dropped item or a
particle happened to be in view decided whether the entity scene passed. The
hook now runs at every return; the entity scene with no other entity and no
particle, which left every draw out each time before, draws every model on
the vertex pass and on the mesh stage, in frames identical to Minecraft's
path. A run whose draws are left out draws no models and is faster for it, so
step 2's 778 FPS, above the 650 FPS measured with every model drawn, may come
from such frames.

## Decisions

- The mod extends Minecraft's renderer instead of running its own beside it
  (the Design Principles in `ARCHITECTURE.md`). The GPU path's buffers,
  commands and draws are Minecraft's, so mods that hook those see the usual
  objects, and Minecraft's code keeps running wherever the GPU path does not
  need to replace it.
- Step 2 leaves drawing to Minecraft. The GPU writes the bytes Minecraft would
  have written into a buffer Minecraft's pipelines draw from, so every entity
  render type, shader, transparency mode and outline works unchanged, and the
  largest CPU cost goes first. Step 3 drops the vertex buffer once the mesh
  stage reproduces the shading.
- The mod compiles its own shaders and builds its own compute pipelines.
  Minecraft's `GlslCompiler` compiles only vertex and fragment shaders, numbers
  bindings itself for its descriptor model and targets Vulkan 1.2, and its
  pipelines have no compute or mesh stages. Everything else comes from
  Minecraft: the allocator, debug names, the shaderc library and the frame's
  command buffer. Step 3 should include Minecraft's shader includes at runtime
  for lighting and fog rather than rewrite them.
- Step 3 takes Minecraft's own vertex shader instead. The mesh stage wraps the
  shader a pipeline already compiles, its inputs fed from the records and its
  outputs written per vertex, and Minecraft's compiler compiles it with the
  pipeline's defines and includes. Every entity shader, and one a resource
  pack replaces, keeps working; one the wrapper cannot take (another input, a
  vertex-only built-in) keeps the vertex pass. Mesh pipelines are built only
  when both flags are on as Minecraft builds its pipelines, so Minecraft's
  pipelines are untouched otherwise.
- A hook that must run when a Minecraft method ends injects at every return
  (`@At("RETURN")`), not at the last (`@At("TAIL")`), unless the method's
  bytecode has one return: the decompiled source can hide early returns, as
  it did in `StagedVertexBuffer.upload()`.

## Follow-Up

- Cubes stay resident across resource reloads: a reload bakes new models, and
  the cube store adds their cubes beside the old ones.
- A task stage for step 3, to cull models outside the view on the GPU.
