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

## Decisions

- Solid terrain's mesh pipeline is specialized without the alpha test, as
  vanilla's solid shader has none. It did not change the measured time on this
  machine, which tests depth early either way, and costs nothing.
- The render-distance 32 runs of the baseline did not load more sections than
  render distance 16 and are left out until the benchmark waits for the
  server to send the chunks.

## Follow-Up
