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

2026-10-16, model cubes after a resource reload. A reload bakes every model
again, and Minecraft's elder guardian curse particle bakes one each time it
appears, so the cube store kept the faces of cubes Minecraft had dropped beside
those of the new ones. The store now holds its cubes weakly and counts the
faces of those collected; once they are most of its faces, the GPU path starts
a new store, and the cubes still drawn upload their faces to it as they are
drawn. In the entity scene the store held 93 cubes in 53,568 bytes; the first
reload doubled it, with the old cubes' 558 faces collected, and the second
renewed it to 93 cubes in 53,568 bytes. Every model was drawn on the vertex
pass and on the mesh stage throughout, in frames identical to Minecraft's
path.

2026-10-28, the render thread's share of the GPU path. On 26.4-snapshot-6,
with present pacing a frame behind (it had waited for every frame to reach the
screen since Minecraft began presenting through the Vulkan swapchain), the
entity scene's frame is the render thread's own work again: 0.94 ms of a
1.18 ms frame on the GPU path. A profile with `-XX:+DebugNonSafepoints` put
about a quarter of that under the GPU path's hook on `ModelPart.compile`,
which wrote 15 words per posed part and 4 per cube through `MemorySegment`
accessors, looked at every cube of every part, and turned six face normals by
the part's pose. Three changes, each checked against Minecraft's vertices byte
for byte:

- The records are written into Java `int` arrays and copied to the GPU in
  one copy each. Medians of four runs each way in two sessions, Minecraft's
  path alongside: 0.937 to 0.918 ms of render thread a frame on the GPU path,
  Minecraft's path 2.05 and 2.04 ms.
- A part's cubes are laid out once per cube store, on the part itself
  (`ModelPartCubes` through `ModelPartMixin`): each cube's first face and face
  count, and every direction the part's faces point. A posed part then costs
  one part record and a loop over the layout's arrays; a part with a cube left
  to Minecraft still goes cube by cube.
- Three of the six normals are turned by the pose; each opposite one is the
  negation, exactly, as a unit vector along an axis has one non-zero
  component.

The last two, against the cube-by-cube path in one session, five rounds each
in turn: 0.944 to 0.920 ms of render thread a frame (medians), 846 to 863 FPS,
less in every pair. Together the three take about 0.045 ms, a twentieth, off
the render thread in this scene. Frames on Minecraft's path, the vertex pass
and the mesh stage are identical. What is left of the frame is mostly
Minecraft's: posing parts, setting up animations and extracting entities.

2026-11-02, barriers by resource on by default, after a run with the Vulkan
validation layer (SDK 1.4.363) on 26.4-snapshot-6. Core and synchronization
validation, with barriers by resource on: Minecraft's terrain, strict mesh
replacement in the three scenes, the listed path, render distance 32, block
edits, streaming, a window resize, a dimension change, the entity scene on the
vertex pass and the mesh stage, resource reloads and shutdown. No error; one
warning, on Minecraft's own `oit_water_mask`, which writes a colour its subpass
has no attachment for. Setting it up took more care than running it:

- A first run was as clean with the held barriers dropped outright, because
  synchronization validation leaves out what shaders read and write unless
  `syncval_shader_accesses_heuristic` is on. With it on, dropping them reports
  a draw reading `Camera projection matrix UBO level` after `vkCmdCopyBuffer`,
  and dropping the barrier before a conflicting transfer reports two clears of
  `Main / Depth`. With the barriers held the same session reports neither.
- The layer stops reporting a message after a limit per id for the session, so
  those drops used up the messages the real run was looking for. The run above
  is a new session of the committed code, with a limit of 200.

The run covers what the flag changes: Minecraft's transfers and the reads that
follow them. The setting warns that it can ignore accesses and does not support
`VK_EXT_descriptor_buffer`; mesh terrain reaches its buffers through the
descriptor heap and device addresses, so ordering inside the terrain passes is
not taken as checked.

On snapshot-6 it saves less than on snapshot-4. Five runs of each in turn at
1920x1080, four while sections reload, medians, GPU time from Minecraft's timer
query:

| Scene                                | FPS on | FPS off | GPU on   | GPU off  |
|--------------------------------------|--------|---------|----------|----------|
| Overlook, render distance 16         | 1041   | 1052    | 0.858 ms | 0.867 ms |
| Entity scene, render distance 12     | 446    | 453     | 0.678 ms | 0.699 ms |
| Overlook while every section reloads | 1014   | 999     | 0.884 ms | 0.894 ms |

All three are inside the run-to-run spread: it spends no time and saves about
57 % of the barriers after transfers, which now come to little on this GPU.
Frames are identical, a mean difference of 0.00 of 255 where two frames with it
off differ by 0.07.

2026-11-02, the mesh stage where the GPU sets the pace. The entity scene with
strict mesh terrain at render distance 32, five runs of each in turn, medians:

| Window    | Path        | FPS | Frame    | Render thread | GPU time |
|-----------|-------------|-----|----------|---------------|----------|
| 1920x1080 | Vertex pass | 622 | 1.607 ms | 1.426 ms      | 1.266 ms |
| 1920x1080 | Mesh stage  | 622 | 1.608 ms | 1.435 ms      | 1.197 ms |
| 3840x2160 | Vertex pass | 319 | 3.132 ms | 1.534 ms      | 2.823 ms |
| 3840x2160 | Mesh stage  | 318 | 3.146 ms | 1.571 ms      | 2.769 ms |

The mesh stage takes less of the GPU in every pair, 0.069 ms at 1920x1080 and
0.054 ms at 3840x2160, and the frame rate does not move at either: the render
thread sets the pace at the first and the GPU at the second, where the saving
is 2 % of its time. It stays off by default until it buys something, such as
the task stage under Follow-Up.

2026-11-02, GPU model vertices on by default. A fresh render-thread profile,
the path off and on in one session, 1920x1080, ticks frozen, medians of five
runs:

| Scene                      | FPS off | FPS on | Render thread off | Render thread on | `Cube.compile`, off / on |
|----------------------------|---------|--------|-------------------|------------------|--------------------------|
| Ground, no entities        | 1729    | 1721   | 0.267 ms          | 0.269 ms         | none                     |
| Ground, 24 mobs            | 1633    | 1654   | 0.373 ms          | 0.354 ms         | 15 % / 0.2 %             |
| Entity scene, 400 mobs     | 436     | 912    | 2.115 ms          | 0.920 ms         | 58 % / 0 %               |

The opt-in flag had only ever been switched on at runtime, and a default met
three things that never had:

- `configure` built the vertex pass inside `VulkanDevice`'s constructor, before
  `RenderSystem` has a device, which would have stopped every client at startup.
  It is built on the first frame the path takes.
- `OtherModMixins` never saw another mod. It read `Mixins.getConfigs()`, which
  holds only configurations Mixin has yet to prepare, so it was empty by the
  first frame. `Mixins.getMixinsForClass` is no help: it is empty for a target
  class. The check now reads the classes as loaded, where Mixin marks what it
  merged with `MixinMerged`. A method another mod merged counts unless it is an
  accessor or invoker: Fabric API puts an invoker on `ModelPart`, so counting
  every target would have turned the path off for nearly every client.
- A vertex pass that cannot be built crashed the game. It now leaves every
  model on Minecraft's path, with the reason in the log.

Each was exercised for real: a throwaway mixin with an `@Inject` on `ModelPart`
turns the path off and names the method and mixin, a forced build failure is
logged once and the client plays on, and an OpenGL session never takes the path.

Parity covers 202 subjects in the sky: the entity types that stay where they
are put, 43 variants (armour with trims, glint and dye, elytra, shields and
tridents in hand, babies, saddles, charged creepers, slimes, glowing and
invisible mobs, dropped items, displays, item frames) and 32 block entities.
47 views, as under Vanilla Parity Check: both sides of each page, midnight,
render distance 32, improved transparency off, strict mesh terrain, the player
in third person and first, and the inventory. 37 have no stable difference.
The other 10 have from 1 to 572 pixels, at most 3 of 255. The large clusters are
on the translucent beams in the scene; of the small ones, not all were traced,
and one, 3 pixels of 1 of 255, is on a mob. The cause is most likely the order
of summing: the path accepts translucent models unsorted under improved
transparency, so the accumulation adds the same layers in another order. That
fits the views with it off, where Minecraft's sorted models stay on its path
and a view differs by 1 pixel at most, but it was reasoned from the code, not
measured directly.

A run with the Vulkan validation layer, the path on from the first frame through
the 400 mobs, a resource reload, a window resize, a dimension change, leaving
the world and joining it again, reports no error and leaks nothing, with
`framesLeftOut` at 0 throughout.

2026-11-02, step 5, what each part costs. Render-thread time above an empty
frame's 0.27 ms, with the GPU model path on, and where a profile puts the frame:

| Scene                       | Added   | Where it goes                                                      |
|-----------------------------|---------|--------------------------------------------------------------------|
| 576 signs, four lines each  | 2.86 ms | `TextFeatureRenderer.buildGroup` 70 %: glyph vertices 38 %, laying out the same strings again 28 % |
| 900 dropped items           | 1.95 ms | writing quads 45 %, `getModelBoundingBox` 13 %, extraction 16 %    |
| 14,243 particles            | 0.83 ms | writing vertices 38 %, extraction 38 %, a map lookup per particle in that 11 % |
| 400 block displays          | 0.13 ms | not worth a path of its own yet                                    |

So the four are three different problems. Items repeat what models were: a
`BakedQuad` is a face with four corners that never changes, and `ItemQuads`
hands the same list every frame. Particles change every frame but already sit
in Minecraft's flat arrays. Text spends as long on laying out strings that have
not changed as on writing their vertices.

2026-11-02, items on the GPU (`vim.gpuItemVertices`, on by default). A baked
model's quad list goes into the cube store once, its layout kept on the list's
first quad, and an item records a part and jobs for each run of quads that share
a render type, tint and light emission. 900 dropped items, five runs of each in
turn: 421 to 731 FPS, 2.165 to 1.168 ms of render thread, no run overlapping.
What stays on Minecraft's path, whole, so that its quads keep their order:
special foil, outlines, render types in another format, and translucent items
Minecraft sorts on upload when improved transparency is off.

Parity with PNGs taken off, on, off, on, off: all 1,671 items in the game and 38
variants (enchanted, dyed, potions, translucent and emissive blocks, banners,
shields, maps, stacks of 64), 12 views each with improved transparency, without
it and at midnight, 36 views with no differing pixel. The 202 subjects of the
model check, items held, framed and displayed among them, 16 views: 13 with no
stable difference, 3 with 1 to 1,171 pixels of 1 of 255, on the translucent
beams as before. `checkModelVertexInvariants` compares the pass with
`VertexConsumer.putBakedQuad` byte for byte on every build.

The validation layer reports no error from the first frame through items and
mobs in one draw, reloads, the mesh stage beside the vertex pass, and leaving
and joining the world again. A reload bakes new lists: the store's old quads are
counted as collected as a cube's are, and the next reload renews it to the same
size. Fabric API changes none of the classes the path stands in for.

2026-11-02, particles on the GPU (`vim.gpuParticleVertices`, on by default).
Minecraft extracts its particles into flat arrays every frame, twelve floats and
two ints each, then allocates a quaternion and four vectors per particle to write
their vertices. The path copies the arrays to the GPU whole, and a compute pass
of its own writes the vertices in Minecraft's particle format, where Minecraft
draws them with its own pipelines, so the render thread does nothing per particle
past extraction. Extraction looked its layer up in a map for every particle; the
last layer's storage is now kept. 10,558 particles in view, five runs of each in
turn with that fix in both: 900 to 1,293 FPS, 0.889 to 0.535 ms of render thread,
no run overlapping.

Parity with PNGs taken off, on, off, on, off: 44 kinds across the opaque and
translucent sheets and the block and item atlases, tinted, emissive and scaling,
in 9 views with improved transparency, without it and at midnight. None has a
stable difference; a few hundred pixels differ between shots of either path at
midnight, where the particles themselves change. `checkParticleVertexInvariants`
reads Minecraft's own arrays and compares the shader's arithmetic with
`buildLayer` byte for byte on every build. One thing that check cannot see: GPU
division is not correctly rounded, so a quaternion's normalizing factor may differ
in its last bit on some GPU, which would move a corner by less than a pixel.

The validation layer reports no error with particles, items and mobs together
from the first frame through a resize, a reload, and leaving and joining the
world, with the vertex passes' SPIR-V from the Vulkan SDK.

2026-11-02, text on the GPU (`vim.gpuTextVertices`, on by default). Minecraft lays
every text out again every frame, glyph by glyph. The path keeps the quads
Minecraft's own glyphs write under no pose, per string object, in a store of its
own, and a compute pass writes them through each text's pose in Minecraft's text
format, into Minecraft's draws. 576 signs with improved transparency: 341 to 812
FPS, 2.718 to 1.035 ms of render thread.

A string counts from the second frame it is drawn in. Counting from its second
draw filled the store: a name tag makes its string anew every frame and draws it
twice within one, so four name tags laid out thousands of strings a second and
started the store over several times a second. Text that stays on Minecraft's
path: see-through text, whose format has no light; outlined text; obfuscated
text, whose glyphs are drawn at random; and text made anew every frame.

Signs and text displays draw with a render type Minecraft sorts on upload unless
improved transparency is on, so with it off, as under the Fast and Fancy presets,
they stay on Minecraft's path. A sweep with it off is clean with `gpuTexts` not
moving at all, which shows the path steps aside and nothing more.

Parity with PNGs taken off, on, off, on, off: 19 styles of text in bold, italic,
underlined, struck, colours, Japanese, Arabic, four fonts and shadow colours, in 9
display styles, beside styled and glowing signs and name tags. No stable
difference in 9 views at noon or 9 at midnight, or after a resource reload or the
Unicode font option, each of which starts the store over. The one difference the
sweep ever showed was an obfuscated text's random glyphs, gone with that text
taken out. `checkTextVertexInvariants` compares the shader's arithmetic with
Minecraft's glyphs byte for byte on every build.

The validation layer reports no error with signs, text displays and name tags from
the first frame through a reload, with the pass's SPIR-V from the Vulkan SDK.

Step 5 ends here. Block models stay on Minecraft's path: 400 of them cost 0.13 ms.

2026-11-04, step 6 for terrain (`vim.terrainOcclusionCulling`, on by default;
Video Settings: Terrain Occlusion Culling). GPU-driven terrain is culled in two
phases when improved transparency is on. The first phase, in Minecraft's opaque
pass, draws only the pages that were not hidden in the frame before; each page
keeps that in its page-table record, which the GPU alone writes. Minecraft then
closes the pass (`LevelRenderer.executeOit`, at its head): a depth pyramid is
built from the depth it wrote, every page in view is tested against it, and the
pages seen now that the first phase left out are drawn in a render pass of the
mod's on the same target, before the transparency passes read the depth. A frame
whose second phase never ran turns culling off until the renderer is configured
again; none did.

The pyramid is read straight from Minecraft's depth image, pushed as a
descriptor: one dispatch writes six levels per 64 by 64 tile through shared
memory, and one more finishes them. The first version copied the depth into a
buffer and wrote one level per dispatch, each behind a barrier: 0.19 ms of GPU
time for the second phase, more than the terrain it hid. Now the pyramid costs
0.027 ms and the test and the draw 0.009 ms at 1920x1080.

The same session, ground, overlook and horizon, strict mesh terrain, improved
transparency, culling off and on in turn three times each, medians; terrain GPU
time from `vim.terrainGpuTimer`, second phase included:

| Scene, render distance | Pages hidden | FPS off | FPS on | Terrain GPU off / on |
|------------------------|--------------|---------|--------|----------------------|
| ground, 16             | 47 %         | 2051    | 2121   | 0.216 / 0.200 ms     |
| overlook, 16           | 5 %          | 1587    | 1472   | 0.274 / 0.314 ms     |
| horizon, 16            | 6 %          | 1799    | 1651   | 0.173 / 0.215 ms     |
| ground, 32             | 72 %         | 1072    | 1323   | 0.555 / 0.266 ms     |
| overlook, 32           | 6 %          | 823     | 814    | 0.703 / 0.728 ms     |
| horizon, 32            | 10 %         | 727     | 724    | 0.474 / 0.483 ms     |

Where hills hide terrain the GPU draws half of it; where little is hidden the
second phase costs 0.04 ms, which shows only above 1,000 FPS. Forest hides
little: a leaf's holes let the sky's depth through, and the pyramid keeps the
farthest depth.

Parity: frames of a still scene with culling off and on are identical, and so is
the first frame at the overlook after settling on the ground, where 256 pages the
ground hid were drawn by the second phase; the few pixels that change by one
level between any two shots there change the same way with culling off. The
validation layer, with synchronization validation on, reports nothing from the
mod over the same runs; it does not see what shaders reach through device
addresses, which is how the pyramid and the pages are read.

2026-11-08, step 6 for entities and block entities
(`vim.entityOcclusionCulling`, off by default; Video Settings: Entity Occlusion
Culling). Minecraft decides on the CPU which entities and block entities a
frame draws, while it extracts the frame and before anything is drawn, so they
cannot wait for a second phase as terrain does: a box the GPU finds hidden can
only leave out work in a later frame. As Minecraft extracts a frame, the box
each entity renderer tests against the view is noted, grown by four ticks of
the entity's motion and by its shadow, and so is the block of each block
entity of a section in view, grown by one. Where Minecraft closes its opaque
pass the pyramid is built, whether or not the frame's terrain is culled, and
a fifth step of the GPU-driven pass tests the boxes and writes one word each
into host-visible memory. The CPU reads a frame's words once its submit has
finished and leaves out what they found hidden; what it leaves out is still
noted and tested, so it comes back once a test sees it. At about 1,500 FPS the
results are two or three frames old when used. A box reaching past the edge
of the screen is never found hidden, results are not used once older than
200 ms or once the camera has moved more than a block. A glowing entity and
one whose name tag shows are always drawn, as their outline and name show
through what hides them, and so are a fishing hook, whose line reaches back to
the rod, and the camera's entity.

400 cows and 121 chests floating behind a stone wall in the sky, Minecraft's
terrain, 1920x1080, ticks frozen, off and on in turn three times each, means:

| View                    | Left out | FPS off | FPS on | Render thread off / on |
|-------------------------|----------|---------|--------|------------------------|
| behind the wall         | 521      | 1,118   | 1,900  | 0.78 / 0.36 ms         |
| every cow in plain view | 0        | 1,091   | 1,070  | 0.80 / 0.82 ms         |

Parity: from above the wall, where 200 cows and every chest stay hidden, five
PNGs off, on, off, on, off are identical. The validation layer's first run
caught a write-after-read hazard on Minecraft's depth: in a frame whose
terrain is not culled nothing ordered the pyramid's reads before Minecraft's
next pass wrote the depth, which the barriers into terrain's second-phase
draws had always done. With a barrier for those frames, Minecraft's terrain,
strict mesh terrain culled in two phases, a resize, a resource reload and
leaving and joining the world logged only Minecraft's `oit_water_mask`
warning.

2026-11-08, mapping kept, measured and left out. Minecraft's `GpuBuffer.map`
maps the allocation and unmaps it again when the view closes, and its mappable
buffers are made without a persistent mapping, so each map of a block no other
allocation holds mapped costs a `vkMapMemory` and a `vkUnmapMemory`. A script
on the render thread put that at about 0.7 µs a map. A mixin that kept each
buffer mapped from its first map until it was destroyed was measured at the
ground at render distance 16, 1920x1080, Minecraft's terrain, in 24 runs of
four seconds in turn, ABBA among them: 1,124 against 1,127 FPS and 0.346
against 0.347 ms of render thread, inside the spread. Minecraft 26.4 batches
its uniform writes, so a frame maps few buffers, and the mixin was not kept.

2026-11-08, occlusion tests only where they pay (`vim.terrainOcclusionProbing`,
on by default). Where little terrain is hidden the second phase costs more than
it saves. The CPU already reads the GPU's counts of tested and hidden pages; it
now follows the share found hidden from them, a few frames late, and while it
is under 12 % tests one GPU-driven frame in 16, the others drawing every page in
view in one phase, until a test finds 20 % or more hidden. Strict mesh terrain,
improved transparency, 1920x1080, ticks frozen, three rounds of each mode in
turn in one session:

| Scene, render distance | Culling off | Every frame | Probing     |
|------------------------|-------------|-------------|-------------|
| overlook, 16           | 932-943     | 858-882     | 931-946     |
| horizon, 16            | 935-967     | 867-877     | 940-946     |
| ground, 16             | 1,172-1,187 | 1,237-1,273 | 1,249-1,254 |
| ground, 32             | 872-874     | 1,115-1,122 | 1,115-1,127 |
| overlook, 32           | 618-621     | 616-619     | 618-622     |
| horizon, 32            | 469-473     | 462-471     | 465-478     |

Probing keeps the gain where hills hide terrain and gives back the loss where
they do not. A still frame at the overlook shows no stable difference between
probing and every frame, and neither does the ground a few frames after a snap
from the overlook: the first tested frame there drew about 3,500 pages that the
overlook's marks had left out, in its second phase, as it should.

Culled frames of a still scene are not quite identical among themselves: up to
about 200 pixels change by one level between shots, in the gaps of dense leaves,
where frames without culling change in one. Pages move between the phases from
frame to frame there, which changes the order in which coplanar faces of
neighbouring sections are drawn; that is the likely cause, not measured
directly.

2026-11-08, shared item bounding boxes on by default
(`vim.shareItemBoundingBoxes`). Minecraft works out every item's model
bounding box again in every frame from every corner of its model; the box
depends only on the model's corners, its item and local transforms and the
hand, so items with the same model share one. With 900 floating items, four
rounds in turn: 768 to 782 FPS without, 948 to 953 with, 1.14 against 0.90 ms
of render thread. It had stayed off since 2026-11-04 because frames with it on
varied by one level in a few hundred pixels. Five PNGs in turn, twice, show no
item pixel differing; what varies is the ground seen through leaves far below
the items, by one level, and it varies as much with the flag off.

2026-11-08, terrain on Minecraft's buffers. `DeviceBuffer` was a buffer of the
mod's made through Minecraft's allocator; it is now a Minecraft `GpuBuffer`,
made by `GpuDevice.createBuffer` with Minecraft's usages and the mod's, or by
Minecraft's Vulkan device while Minecraft is still making the device and its
frontend has none yet, which is when terrain is configured. Its placement
becomes Minecraft's mapping usages, its mapping a view of `GpuBuffer.map` kept
open until the buffer is given back, and its destruction Minecraft's queue.
Three usages join `ShaderStorage`'s in `bufferUsageToVk`: a descriptor heap and
sampler and resource descriptor buffers (`DescriptorStorage`). Minecraft's
mappable memory is host-coherent, so terrain no longer flushes. The validation
layer reports nothing from the mod through strict mesh terrain, occlusion
culling, entity occlusion culling, render distance 32, a resize, a reload,
rejoining and shutdown, nor in a session started with every terrain capacity
at its minimum, which grew the buffers twelve times; strict mesh terrain
against Minecraft's at the ground differs by a mean under 0.001 of 765.

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
- Occlusion culling needs a point between opaque terrain and everything that
  reads its depth. Minecraft has one only with improved transparency, where it
  closes the opaque pass before the transparency passes; without it terrain and
  translucency share one pass, and the mod does not build for that mode.
- Entities and block entities are culled from results a few frames old, which
  breaks step 6's rule that nothing newly visible is missing for a frame, so
  it is off by default and chosen in Video Settings. Minecraft extracts them on
  the CPU before the frame is drawn, and testing them in the same frame would
  have the CPU wait for the GPU, which the constraints rule out.
- The depth pyramid reads Minecraft's depth image through a pushed descriptor,
  so `VK_KHR_push_descriptor` joins the mod's required extensions. Vulkan 1.4
  makes push descriptors a feature of `VkPhysicalDeviceVulkan14Features`, which
  may not be chained beside the maintenance 5 and 6 and local-read structs the
  mod's feature set already asks for; the extension needs no feature struct.
- A hook that must run when a Minecraft method ends injects at every return
  (`@At("RETURN")`), not at the last (`@At("TAIL")`), unless the method's
  bytecode has one return: the decompiled source can hide early returns, as
  it did in `StagedVertexBuffer.upload()`.

## Follow-Up

- A task stage for step 3, to cull models outside the view on the GPU.
