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
- What the GPU path cannot reproduce exactly stays on Minecraft's path, decided
  per submit, never for the whole frame.
- Every claim of a gain comes from the benchmark under Validation, Minecraft's
  path and the GPU path in the same session.

## Steps

1. Model vertices from the GPU. Every model cube is uploaded once. Each frame
   the CPU poses a model's parts as Minecraft does and writes one record per
   part and one per cube; a compute pass writes the vertices into a buffer the
   mod owns, and Minecraft draws them with its own pipelines.
2. Model draws from mesh shaders. The mesh stage reads cubes and part poses
   directly, so no vertex buffer is written, and the task stage culls models
   outside the view.
3. Terrain visibility on the GPU: frustum and occlusion per section from the
   page table, Minecraft's section walk kept only for scheduling compiles, and
   translucent terrain on the mesh path, sorted on the GPU.
4. Items, block models, particles and text the same way: their quads resident,
   one record per instance.
5. Occlusion culling for everything the GPU draws, against a depth pyramid, in
   two phases so that nothing newly visible is missing for a frame.

## Validation

The terrain plan's benchmark (ground, overlook and horizon, render distance 16
and 32), plus an entity scene: a few hundred animals in view, ticks frozen.
Parity screenshots of the same scenes, entities included. A render-thread
profile before and after each step.

## Decisions

- Step 1 leaves drawing to Minecraft. The GPU writes the bytes Minecraft would
  have written into a buffer Minecraft's pipelines draw from, so every entity
  render type, shader, transparency mode and outline works unchanged, and the
  largest CPU cost goes first. Step 2 drops the vertex buffer once the mesh
  stage reproduces the shading.
- The mod compiles its own shaders and builds its own compute pipelines.
  Minecraft's `GlslCompiler` compiles only vertex and fragment shaders, numbers
  bindings itself for its descriptor model and targets Vulkan 1.2, and its
  pipelines have no compute or mesh stages. Everything else comes from
  Minecraft: the allocator, debug names, the shaderc library and the frame's
  command buffer. Step 2 should include Minecraft's shader includes at runtime
  for lighting and fog rather than rewrite them.

## Follow-Up

Nothing yet.
