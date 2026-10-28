# Mesh Terrain Performance

**Status:** Active. Started 2026-10-14 on `master`.

## Goal

Strict mesh replacement is the reason the mesh path exists, and it has to pay
for itself: with it on, Minecraft must run faster than with vanilla terrain on
the same machine, camera and settings. First reach parity, then beat vanilla,
where mesh shaders can do work that vanilla's vertex pipeline cannot: culling
below a section on the GPU and drawing a layer with one command.

## Baseline

2026-10-14, RTX 4070 Ti (616.92), Ryzen 7 7800X3D, Windows 11, 1920x1080,
`26.4-snapshot-3`, render distance 16, frame limit off, vsync off, ticks
frozen, `mcdev_world`. FPS is Minecraft's own counter over ten seconds; GPU
time is derived from Minecraft's GPU utilization timer, which the CPU profile
below shows understates mesh-path GPU time.

| Scene    | Vanilla FPS | Strict mesh FPS |
|----------|-------------|-----------------|
| ground   | 860         | 313             |
| overlook | 953         | 293             |
| horizon  | 833         | 408             |

A CPU profile of the render thread (JFR, 15 seconds each) shows vanilla
CPU-bound at about 1.2 ms a frame, and the mesh path waiting on the GPU for
about a third of its frame (swapchain acquire and submit). The mod's own CPU
work is about a tenth of the mesh-path frame.

## Constraints

- The set of sections drawn stays Minecraft's; the mesh path may only draw
  less than that set by culling what cannot be seen.
- The Vanilla Parity Check in `docs/RELIABILITY.md` must still pass.
- Nothing on the CPU waits for the GPU in a frame.
- Every claim of a gain comes from the benchmark under Validation, vanilla and
  strict mesh in the same session.

## Steps

1. Measure inside the mod: frame intervals with percentiles, and GPU
   timestamps of the prepare work and of each terrain layer group, for vanilla
   and the mesh path alike (`vim.terrainGpuTimer`).
2. Remove the costs the mesh path has and vanilla does not, one at a time,
   each measured.
3. Cull below a section on the GPU: back-facing and out-of-view meshlets are
   not drawn.
4. Cut the mod's per-frame CPU work, which matters once the GPU is no longer
   the limit.
5. Decide from the measurements whether occlusion culling on the GPU is the
   next step.

## Validation

Benchmark, in one client session per run: 1920x1080, frame limit off, vsync
off, `inactivityFpsLimit` not `afk` (the AFK limit caps an unattended client at
30 FPS after a minute), ticks frozen, three scenes (ground, overlook, horizon),
render distance 16 and 32, vanilla then strict mesh, each scene measured after
Minecraft has no section left to compile. Record mean frame time, 1 % low
FPS, and the GPU timestamps of the terrain groups.

## Progress

2026-10-14, step 1 and the first of step 2, same machine and settings as the
baseline, GPU times from `vim.terrainGpuTimer`:

| Scene    | Vanilla FPS | Opaque GPU | Mesh FPS before | Opaque GPU before | Mesh FPS now | Opaque GPU now |
|----------|-------------|------------|-----------------|-------------------|--------------|----------------|
| ground   | 855         | 0.28 ms    | 310             | 2.31 ms           | 730          | 0.44 ms        |
| overlook | 762         | 0.33 ms    | 274             | 2.64 ms           | 676          | 0.49 ms        |
| horizon  | 757         | 0.24 ms    | 405             | 1.61 ms           | 702          | 0.29 ms        |

What the mesh path cost was found by switching parts of it off one at a time
in one session. Neither shading nor the visibility pass (0.03 ms) was it: the
mesh stage added one to two counters per workgroup with an atomic, on a buffer
in host memory, so every meshlet waited on the bus. With the counters gone and
every buffer the GPU uses each frame in device memory, the opaque layers cost
0.4 ms instead of 2.4 ms. Without the mesh stage's fragment work they would
cost 0.26 ms; vanilla's cost 0.28 ms.

Vanilla is CPU-bound here at about 1.2 ms a frame, with its terrain about a
quarter of that on the GPU. The mesh path is now behind it by about 0.15 ms a
frame, mostly the mod's own CPU work per frame.

Later the same day, steps 2 and 4, measured the A/B way (vanilla and strict
mesh in turn, three times each, medians; GPU timer off):

| Scene    | Vanilla FPS | Mesh FPS | Mesh / vanilla | Render-thread ms, vanilla / mesh |
|----------|-------------|----------|----------------|----------------------------------|
| ground   | 886         | 831      | 0.94           | 0.78 / 0.77                      |
| overlook | 701         | 682      | 0.97           | 0.85 / 0.83                      |
| horizon  | 714         | 731      | 1.02           | 0.70 / 0.67                      |

What changed, each measured on its own:

- The mod's work per frame went from about a quarter of the render thread to
  about a tenth. A frame's section layers are kept in arrays that hold each
  section layer's page binding and fade-in, instead of hash maps keyed by
  records and boxed numbers; the backend check is cached; a compiled mesh
  carries the mod's record of it; the visibility pass's rings wait for the
  frame that began their lap, which has long finished, instead of the frame
  before (the command ring wrapped every 32 frames and stalled the CPU until
  the GPU caught up); descriptors and the material table are written only
  when what they describe changes.
- Strict replacement takes the section layers it draws out of Minecraft's
  collection, so Minecraft no longer looks them up and builds and uploads
  draws that are then cancelled (that was about a fifth of vanilla's render
  thread). Minecraft draws what was not taken.
- The lightmap is sampled per vertex in the mesh stage, as Minecraft does,
  instead of per fragment: one texture fetch and two varyings less.

With these the render thread does no more in the mesh path than in vanilla;
where the mesh path is still slower the GPU sets its pace, and its opaque
layers still cost more than vanilla's.

Then step 3. A section layer's quads are grouped by facing at capture and
the task stage drops meshlets that face away or are out of view. Medians,
same session; opaque GPU time from `vim.terrainGpuTimer`:

| Scene    | Vanilla opaque | Mesh opaque | Mesh opaque, no culling |
|----------|----------------|-------------|-------------------------|
| ground   | 0.28 ms        | 0.30 ms     | 0.52 ms                 |
| overlook | 0.33 ms        | 0.33 ms     | 0.64 ms                 |
| horizon  | 0.24 ms        | 0.25 ms     | 0.32 ms                 |

Without the timer: ground 832 -> 855 FPS, overlook 761 -> 758, horizon
785 -> 769. The mesh path is level with vanilla here: its render thread does
a little less, and its opaque layers cost the GPU what vanilla's do.

At render distance 32 (which needs `Options.broadcastOptions()` after the
option is set, or the integrated server keeps sending the old distance) the
mesh path pulls ahead where the CPU sets the pace and stays slightly behind
where the GPU does. Medians in one session, whole-frame GPU time from
Minecraft's own GPU utilization timer:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 427         | 453      | +6 %   | 2.11 / 1.98 ms                | 1.36 / 1.38 ms                |
| overlook | 370         | 393      | +6 %   | 2.47 / 2.28 ms                | 1.69 / 1.72 ms                |
| horizon  | 448         | 436      | -3 %   | 1.62 / 1.46 ms                | 1.87 / 1.98 ms                |

The opaque layers cost the GPU the same in both modes at the horizon (0.81
ms); the extra tenth of a millisecond there lies outside them and is not
explained yet. Handing the listed pages straight to the task stage, in
Minecraft's order, instead of compacting them in a compute pass first made no
measurable difference to it, and is simpler, so it is the default.

Then the tasks. One task drew a whole page: it handed the mesh stage up to
1,024 meshlets through a payload of four kilobytes, and a large page kept one
workgroup busy while the others had nothing left. A page is now drawn by one
task per 32 of its meshlets (`MESHLETS_PER_TASK`); the list holds one record
per task, naming the page and which 32, and a task's payload is 32 meshlet
indices. The mesh path's GPU time a frame fell below vanilla's in every
scene, the horizon included. Medians in one session at render distance 32:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 454         | 482      | +6 %   | 2.03 / 1.90 ms                | 1.23 / 1.05 ms                |
| overlook | 393         | 419      | +7 %   | 2.36 / 2.21 ms                | 1.53 / 1.32 ms                |
| horizon  | 441         | 466      | +6 %   | 1.64 / 1.51 ms                | 1.66 / 1.50 ms                |

A render-thread profile (JFR) at render distance 32 then showed Minecraft
looking up a buffer slice for every layer of every visible section, one or
two hash probes each, also for layers the section has nothing in, whose
slice it then ignores. In a takeover frame those lookups are skipped. The
same kind of session:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 482         | 549      | +14 %  | 1.89 / 1.65 ms                | 1.23 / 1.06 ms                |
| overlook | 428         | 484      | +13 %  | 2.16 / 1.89 ms                | 1.53 / 1.30 ms                |
| horizon  | 452         | 463      | +2 %   | 1.65 / 1.40 ms                | 1.69 / 1.39 ms                |

Minecraft's frame time (`Minecraft.getFrameTimeNs()`, the render-thread
column) runs from extraction to the blit to the swapchain; acquiring the
swapchain image, the submit, which waits for the frame before last, and the
present are outside it, and its GPU timer covers the same span. At the
horizon a frame spends about 0.75 ms outside that span in both modes, which
is why less work on either side barely shows there.

The same profile showed the mod's hooks on that lookup costing the render
thread with the mesh path off too, so the vanilla column above ran slower
than Minecraft without the mod. The lookup went through a MixinExtras
`@WrapOperation`, whose operation object cost about a tenth of the render
thread, and an `@Inject` into the lookup itself, with a callback object per
call, about a twentieth. One `@Redirect` now does both jobs, calls
Minecraft's method directly, and returns at once while no frame is collected.
Mapped buffers are flushed only where their memory is not coherent, and then
only the range written. With the mod's cost gone from the vanilla column the
mesh path's lead is smaller, and nearer the truth:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 508         | 532      | +5 %   | 1.80 / 1.71 ms                | 1.22 / 1.05 ms                |
| overlook | 441         | 468      | +6 %   | 2.09 / 1.96 ms                | 1.53 / 1.31 ms                |
| horizon  | 431         | 462      | +7 %   | 1.53 / 1.40 ms                | 1.29 / 1.36 ms                |

At the horizon the render thread spends a tenth of its time in
`D3D12GpuSurface.acquireNextTexture`, waiting on the swapchain's frame
latency object: there the presentation of frames sets the pace, in both
modes.

A frame's list of a layer's pages is now built from reused arrays: a taken
section layer keeps its page's slot and meshlet count, read while the page
is at hand, and the task records are written from them directly. At render
distance 16, where the mesh path trailed vanilla before these steps:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 1058        | 1153     | +9 %   | 0.73 / 0.68 ms                | 0.59 / 0.55 ms                |
| overlook | 815         | 902      | +11 %  | 0.82 / 0.78 ms                | 0.65 / 0.59 ms                |
| horizon  | 810         | 844      | +4 %   | 0.63 / 0.60 ms                | 0.56 / 0.52 ms                |

With tasks small, meshlets of 64 vertices (sixteen quads) cost the GPU less
than meshlets of 128: in one session at render distance 32 the mesh path's
GPU time a frame was 0.81 of vanilla's on the ground and 0.83 from above,
against 0.86 with 128 in the sessions before; frame rates were 552 against
519 (ground), 493 against 456 (overlook) and 462 against 429 (horizon). A
task record counts a page's tasks in six bits now, so a page may still have
the meshlets of a section layer full of faces.

Where the GPU time goes, with `vim.terrainGpuTimer` and Minecraft's GPU
timer both on, in one session at render distance 32:

| Scene    | Vanilla FPS | Mesh FPS | Change | Opaque terrain GPU, vanilla / mesh | Whole frame GPU, vanilla / mesh |
|----------|-------------|----------|--------|------------------------------------|---------------------------------|
| ground   | 512         | 547      | +7 %   | 0.75 / 0.52 ms                     | 1.25 / 1.00 ms                  |
| overlook | 455         | 486      | +7 %   | 0.94 / 0.68 ms                     | 1.54 / 1.25 ms                  |
| horizon  | 435         | 482      | +11 %  | 0.62 / 0.44 ms                     | 1.49 / 1.38 ms                  |

The mesh path's opaque layers cost the GPU about 30 % less than vanilla's.
The timer's translucent group reads zero: Minecraft 26.4 draws translucent
terrain outside the group the mixin marks, so its share is not measured yet.

In strict replacement a frame is now GPU-driven (`vim.gpuDrivenTerrain`).
The CPU marks the sections Minecraft found in view in a grid of bits, about
13 kilobytes at render distance 32, and the GPU reads its own page table to
write the tasks of every page of those sections and each layer's draw command.
Minecraft then walks only the visible sections that draw something the GPU
does not. In a profile at the overlook at render distance 32, Minecraft's
collection of the frame's sections fell from 24 % to 11 % of the render
thread, the grid included.

One session, both GPU timers off, three rounds of four seconds per mode, with
the listed path of the steps before (`vim.gpuDrivenTerrain=false`) alongside:

| RD | Scene    | Vanilla FPS | Listed FPS | GPU-driven FPS | GPU-driven vs vanilla | Render thread, vanilla / listed / GPU-driven |
|----|----------|-------------|------------|----------------|-----------------------|----------------------------------------------|
| 32 | ground   | 533         | 555        | 642            | +20 %                 | 1.70 / 1.63 / 1.39 ms                        |
| 32 | overlook | 493         | 515        | 546            | +11 %                 | 1.86 / 1.77 / 1.66 ms                        |
| 32 | horizon  | 411         | 421        | 419            | +2 %                  | 1.24 / 1.26 / 1.22 ms                        |
| 16 | ground   | 881         | 1009       | 908            | +3 %                  | 0.73 / 0.71 / 0.64 ms                        |
| 16 | overlook | 724         | 752        | 744            | +3 %                  | 0.74 / 0.72 / 0.66 ms                        |
| 16 | horizon  | 753         | 761        | 756            | 0 %                   | 0.61 / 0.61 / 0.60 ms                        |

The GPU-driven frame takes the least render-thread time in every scene. Its
opaque terrain costs the GPU what the listed path's does, and its pass costs
about 0.011 ms of GPU time a frame. At render distance 16 on the ground it is
still 10 % slower than the listed path: about 0.18 ms more of each frame goes
outside the render thread's work, to acquiring, submitting and presenting.
Neither dropping the sort (one step and one barrier instead of three) nor
recording the pass into Minecraft's own command buffer changed that much.
With both GPU timers on, the comparison came out differently in some scenes
(the overlook at render distance 32: 510 GPU-driven against 532 listed), so
frame rates are compared with the timers off.

2026-10-28, on 26.4-snapshot-6, where Minecraft presents through the Vulkan
swapchain, with present pacing waiting only when vsync is on (it had waited
for every frame to reach the screen, which tied frames to the display's
refresh, see `docs/RELIABILITY.md`). 1920x1080, render distance 32, three
rounds of each mode in one session, medians; whole-frame GPU time from
Minecraft's GPU utilization timer:

| Scene    | Vanilla FPS | Mesh FPS | Change | Render thread, vanilla / mesh | GPU per frame, vanilla / mesh |
|----------|-------------|----------|--------|-------------------------------|-------------------------------|
| ground   | 599         | 717      | +20 %  | 1.52 / 1.25 ms                | 1.21 / 0.98 ms                |
| overlook | 505         | 598      | +18 %  | 1.82 / 1.47 ms                | 1.61 / 1.39 ms                |
| horizon  | 484         | 541      | +12 %  | 1.16 / 0.89 ms                | 1.81 / 1.62 ms                |

At the horizon the frame now follows the GPU in both modes; nothing waits on
the swapchain there any more.

At render distance 16 on the ground the GPU-driven frame still trails the
listed path (1,160 to 1,290 FPS against 1,260 to 1,410 in three sessions), and
the difference is GPU time: 0.06 to 0.16 ms more a frame, where the GPU sets
the pace. At render distance 32, where the render thread does, it is
0.009 ms. The GPU-driven pass takes 0.012 ms and its opaque draws 0.003 ms
more than the listed ones (`vim.terrainGpuTimer`), and an empty compute
dispatch with the same barrier, recorded at the same point of a listed frame,
cost nothing, so neither the pass's work nor the switch to compute is it.

## Decisions

- Solid terrain's mesh pipeline is specialized without the alpha test, as
  vanilla's solid shader has none. It did not change the measured time on this
  machine, which tests depth early either way, and costs nothing.
- Strict replacement no longer cancels Minecraft's draws for a layer when the
  mesh path covers every section layer of it. It takes what it can draw as
  Minecraft collects the frame, so one section without a page no longer sends
  the whole layer back to Minecraft, and Minecraft does no work for what was
  taken.
- A page is drawn by one task per 32 of its meshlets, not one task per page.
  Small payloads and many workgroups are what mesh shaders are fast with.
- The draw's textures stay in a descriptor buffer. Pushing them with each
  draw instead, to rule out a stall where the GPU switches between Minecraft's
  descriptor sets and the mod's descriptor buffer, made no difference in any
  scene (horizon: 451 against 452 FPS, 1.13 against 1.11 ms of GPU a frame).
- Meshlets hold 64 vertices. With 128, the mesh stage's outputs are twice as
  large and its GPU time on the same terrain about a twentieth higher.
- In strict replacement frames are GPU-driven: the CPU says which sections are
  in view and nothing about pages, and Minecraft walks only the sections that
  draw something the GPU does not. A frame falls back to listing pages when a
  layer has more tasks than one draw may launch, the box around the sections
  in view is too large, or the visible list has no room.
- A mesh's layers get pages only once Minecraft shows the mesh. The GPU-driven
  draw takes every page at a section in view, so a section may have the pages
  of one mesh only; until it is shown, the mesh it replaces is the one drawn.
- The GPU-driven pass sorts a layer's tasks by the distance of their section
  from the camera's. Without the sort they follow the page table's slots,
  which follow the order sections were loaded in only until the camera moves;
  in the scenes measured both cost the same.
- The mod's page uploads and its GPU-driven pass are recorded into the command
  buffer Minecraft records the frame into. Executing a command buffer of the
  mod's instead ends Minecraft's open ones, the object-init one behind a
  barrier on all work before it.
- The render-distance 32 runs of the baseline did not load more sections than
  render distance 16 and are left out until the benchmark waits for the
  server to send the chunks.

## Follow-Up

- At render distance 16 on the ground the GPU-driven frame takes 0.06 to
  0.16 ms more GPU time than the listed path, outside its pass and its draws
  (Progress, 2026-10-28). A GPU trace of both modes would show where. Nsight
  Systems' Vulkan trace (`--trace=vulkan,vulkan-annotations`, with Minecraft
  started with `--renderDebugLabels` so its passes are labelled) needs its
  Vulkan layer registered in the system registry, which takes administrator
  rights.
- Occlusion culling on the GPU (step 5).
