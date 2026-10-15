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

## Follow-Up

Nothing yet.
