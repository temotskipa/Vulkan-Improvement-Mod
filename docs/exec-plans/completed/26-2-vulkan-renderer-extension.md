# Minecraft 26.2 Vulkan Renderer Extension

**Status:** Completed 2026-10-13, when the
[26.4 snapshot port](../active/minecraft-26-4-snapshot-port.md) replaced 26.2 as the
target. Read the Completion section first: three of the decisions below were
overtaken by the port.

## Goal

Extend the stable Minecraft 26.2 Vulkan renderer path from a terrain-specific
mesh replacement into a renderer-owned GPU-driven foundation that matches the
project goals in `docs/design-docs/gpu-driven-worldgen-rendering.md`.

The first implementation target is not a broad rewrite. It is a controlled
26.2 extension that hardens capability negotiation, isolates Mojang-version
touch points, extracts shared renderer core services from the terrain path, and
creates the first GPU-world-database contracts that later terrain, non-terrain,
LOD, and RT/PT work can share.

## Current Baseline

- The project now targets stable Minecraft `26.2`, Fabric API
  `0.154.0+26.2`, Fabric Loader `0.19.3`, and Loom `1.17.13`.
- The Gradle wrapper is `9.6.1`; the 2026-07-03 dependency check found Gradle,
  Fabric Loader, and Loom release pins current, kept Minecraft on the stable
  26.2 target, and bumped Fabric API from `0.153.0+26.2` to `0.154.0+26.2`.
- The 2026-07-04 dependency recheck found no pin changes: Gradle remains
  `9.6.1`, Fabric Loader remains `0.19.3`, Loom remains `1.17.13`, and the
  latest Fabric API build for Minecraft `26.2` remains `0.154.0+26.2`.
- The completed dependency update is recorded in
  [../completed/minecraft-26-2-dependency-update.md](minecraft-26-2-dependency-update.md).
- The previous terrain renderer foundation is recorded in
  [../completed/vulkan-1-4-renderer-refactor.md](vulkan-1-4-renderer-refactor.md).
- Open follow-up work is tracked in
  [../tech-debt-tracker.md](../tech-debt-tracker.md).

## Progress

- 2026-06-28: `VulkanFeatureRequirements` now exposes explicit local groups
  for base Vulkan API, VIM hard requirements, required-if-enabled descriptor
  heap, optional acceleration, and RT/PT readiness while preserving the 26.2
  adapter methods consumed by `VulkanBackendMixin`.
- 2026-06-28: `VulkanRuntimeProfile` now reports selected descriptor,
  mesh-task dispatch, multi-draw fallback, device-generated command, and RT/PT
  readiness states with disabled reasons for unavailable paths.
- 2026-06-28: DebugBridge contact-sheet recordings under
  `run/debugbridge-recordings` showed broken mesh-terrain visuals: HUD, hand,
  and sky mostly rendered, while terrain showed dark holes and corrupt
  ordering. The first fix removed constant clip-space depth from the terrain
  mesh shader and added a layout-manifest check to reject that regression.
- 2026-06-28: `SectionMeshletStore` now validates captured block-vertex
  payloads and normalized custom indices before accepting a layer capture. Bad
  payloads remove stale meshlet mappings, increment rejected-capture
  diagnostics, and let the visible draw-list path fall back to vanilla.
- 2026-06-29: The first `GpuWorldDatabase` contracts now exist for
  renderer-owned section identity, revision, material-table layout, page-kind
  authority, and dirty update diagnostics. Terrain captures register canonical
  CPU-derived mirror pages, releases mark removal updates, world clears reset
  the derived GPU world state, and `RendererDiagnostics` exposes the database
  in bug-report JSON.
- 2026-06-29: Vanilla 26.2 graphics API defaults are preserved. The previous
  first-run `DEFAULT -> VULKAN` preference rewrite was removed, and
  backend-agnostic chunk/capture hooks now bail out unless the active
  `GpuDevice` is Vulkan. OpenGL-active sessions do not enter VIM terrain
  capture or renderer services.
- 2026-06-29: All `TerrainRendererDebugConfig` flags are mutable through the
  shared setter path used by Video Settings and `mc_execute` Groovy debugging.
  The Video Settings section exposes the debug flags when Vulkan is active or
  explicitly selected.
- 2026-06-29: The Video Settings visibility gate now prioritizes the active
  Vulkan backend before rejecting a pending OpenGL restart preference, so VIM
  options remain accessible while the current session is still running Vulkan.
  `checkVulkanVideoOptionsInvariants` covers this ordering.
- 2026-06-29: Runtime smoke on RTX 4070 Ti / NVIDIA 610.62 showed the visible
  mesh work-queue replacement path still device-loses after ~30 seconds even
  with GPU-generated mesh-task command preparation disabled. The crash report
  is `run/crash-reports/crash-2026-06-29_08.25.58-client.txt`.
- 2026-06-29: `vim.replaceVanillaTerrain` now defaults to `false`. A follow-up
  mcdev smoke survived past the previous crash window with zero mesh-task
  dispatches and normal vanilla terrain visible; evidence is
  `run/debugbridge-recordings/req_31/recording.jpg`.
- 2026-07-01: The default runtime mode is vanilla terrain with VIM terrain
  capture/bootstrap disabled. `vim.enableTerrainCaptureBootstrap=false` keeps
  section capture, draw-slice recording, terrain metadata upload, and terrain
  cache reset hooks idle unless diagnostic capture/bootstrap or visible mesh
  replacement is explicitly enabled.
- 2026-06-29: Record-first camera-jitter validation reproduced the mesh
  replacement visual failure before the latest fix: the contact sheet at
  `run/debugbridge-recordings/req_17/recording.jpg` showed large dark/green
  slab-like terrain corruption while HUD, hand, and sky continued rendering.
- 2026-06-29: The mesh replacement path now restores vanilla's native Vulkan
  graphics pipeline after each cancelled vanilla draw and marks vanilla
  descriptors dirty before fallback can resume. `MeshShaderTerrainProgram` also
  waits until descriptor-buffer binding succeeds before binding the VIM mesh
  pipeline.
- 2026-06-29: The terrain mesh shader now uses Minecraft's
  `Camera#getViewRotationProjectionMatrix` instead of rebuilding projection from
  camera FOV and fixed near/far values. Follow-up record-first jitter captures
  no longer showed the slab corruption: `run/debugbridge-recordings/req_34/recording.jpg`
  survived 37,257 mesh draw calls / 46.0M mesh tasks, and the daylight downward
  sample at `run/debugbridge-recordings/req_43/recording.jpg` showed coherent
  terrain. Mesh replacement remains validation-gated until a longer soak pass
  covers device-loss risk.
- 2026-06-29: Follow-up inspection of `req_43` found close terrain textures
  looked blurred in the contact sheet. The run options record
  `textureFiltering:2` (`ANISOTROPIC`) and `mipmapLevels:4`, and the recording
  artifact is a downscaled contact sheet rather than a full-resolution frame,
  so this is not yet proof of a mesh UV or shader LOD bug. Bug reports now
  include `videoSettings`, and terrain descriptor diagnostics expose captured
  texture bindings with mip counts and sampler anisotropy so the next full-res
  nearest-filter capture can distinguish expected filtering from VIM sampling
  errors.
- 2026-07-01: Max-feature record-first jitter at
  `run/debugbridge-recordings/req_33/recording.jpg` showed mesh replacement is
  still broken under the all-on diagnostic profile: voids/slabs/blur plus
  transparency and environment screen-effect failures. Counters included
  `meshReplacementTaskTruncations=5431` and visible translucent replacement
  draws. The current containment is to split full-layer diagnostic dispatches,
  consume prepared visible mesh-task commands, and keep visible translucent
  terrain/effects on vanilla until their compatibility contract exists.
- 2026-07-01: The follow-up max-feature capture
  `run/debugbridge-recordings/req_41/recording.jpg` proved the first
  containment pass removed task truncation and visible translucent replacement
  (`meshReplacementTaskTruncations=0`, translucent replacement draw calls 0),
  but the picture still had large sky/void gaps. Runtime counters showed
  `terrainWorkQueueRingWraps=1625`, so full-layer `drawAll` now uses direct
  segmented mesh-task dispatches instead of terrain work-queue records.
- 2026-07-03: After direct full-layer dispatch, `run/debugbridge-recordings/req_4/recording.jpg`
  stayed coherent under record-first jitter and counters showed no task
  truncation, no translucent replacement draws, no work-queue uploads, and no
  work-queue wraps. Same-camera nearest/no-mipmap screenshots still proved the
  remaining blur is a VIM mesh replacement bug rather than the recorder or
  vanilla filtering: vanilla/capture-bootstrap
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-7625853700353120804.jpg`
  was sharp, while VIM mesh replacement
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-9545749426833000992.jpg`
  had soft close terrain and over-expanded cutout vegetation. The terrain
  fragment shader now uses `texelFetch` for the block atlas, applies a strict
  cutout threshold for alpha-masked materials, and reports sampler min/mag
  filters plus max LOD in texture-binding diagnostics.
- 2026-07-03: Runtime retest after the 32-lane mesh shader and 128/32 meshlet
  granularity changes recorded `run/debugbridge-recordings/req_63/recording.jpg`.
  Strict visible replacement still produced large nearby terrain holes despite
  `meshReplacementTaskTruncations=0`, no prepared-dispatch leaks, and no
  translucent replacement. Same-camera vanilla/capture screenshot
  `C:\Users\ttski\AppData\Local\Temp\debugbridge-screenshot-13566611726126501905.jpg`
  was intact. The containment is to wire `vim.strictMeshTerrainReplacement`
  into vanilla cancellation, default it to `false`, and keep visible mesh draws
  as overlay/fallback diagnostics until coverage is proven.
- 2026-07-03: Patched non-strict retest
  `run/debugbridge-recordings/req_72/recording.jpg` kept vanilla terrain visible
  under record-first jitter with `rendererMode=mesh-overlay-vanilla-fallback`
  and `strictMeshTerrainReplacement=false`. Counters remained clean for
  truncation, translucent replacement, and prepared-dispatch leaks, but
  `terrainWorkQueueUploads=31962` and `terrainWorkQueueRingWraps=501` confirm
  the current visible mesh path is still too expensive and should be replaced by
  a GPU-resident visibility/residency architecture rather than tuned further as
  the final design.
- 2026-07-03: The next architecture step is now split into
  [GPU-resident terrain architecture](gpu-resident-terrain-architecture.md).
  It keeps named external source references in ignored local notes, preserves
  `vim.strictMeshTerrainReplacement=false`, and points future terrain work
  toward `GpuWorldTerrainLayerDirectory`, persistent section pages, dirty-event
  updates, visibility compaction, explicit residency budget diagnostics, and
  indirect command generation.
- 2026-07-04: Official source inspection filled only the missing architecture
  gaps left by the earlier pass. The public lesson is still generic: optional
  accelerated worldgen paths are diagnostics and scheduling hints, renderer
  integration should keep a thin typed Java boundary over backend capability
  gates, and terrain replacement should prioritize contention/residency
  counters before deeper cancellation work. `GpuWorldResidencyStats` now
  records live page counts, used bytes, evictions, and over-budget refusals.
- 2026-07-04: Optional accelerated-worldgen provider integration now has a
  diagnostics-only runtime contract. Bug reports expose provider presence,
  opt-in state, renderer/worldgen authority labels, fallback reason, and
  metadata relationship without adding Fabric metadata relationships or changing
  terrain capture, mesh replacement, or GPU-world page authority.
- 2026-07-04: `GpuWorldVisibilityCommandStats` now exposes the diagnostic
  contract for visibility compaction inputs, culled/visible sections, emitted
  indirect commands, command overflows, and fallback commands. The counters are
  reported through `GpuWorldDatabase.asMap()` but explicitly do not authorize
  vanilla terrain cancellation.
- 2026-06-29: `RendererCoreServices` now owns renderer-wide lifecycle state,
  domain reset, present pacing, and fragment shading-rate controller
  configuration. `MeshTerrainRenderer` delegates those shared services while
  retaining terrain-specific buffers and mesh shader resources. Bug reports now
  expose a separate `rendererServices` diagnostics block, and
  `checkRendererCoreServiceInvariants` covers the ownership boundary.
- 2026-06-29: The terrain shader build pipeline now has a release-grade
  validation gate. `-Pvim.releaseBuild=true` implies required
  `glslangValidator` for both `compileTerrainSpirv` and `checkTerrainShaders`,
  while development builds still skip SPIR-V generation when the validator is
  missing. `checkShaderBuildPipelineInvariants` covers the Gradle wiring.
- 2026-06-29: The mcdev-confirmed 26.2 client mixin target surface is now
  recorded in `docs/references/minecraft-26-4-mixin-targets.md` (the 26.2
  manifest until the 26.4 port replaced it), and
  `checkMinecraft26MixinTargetManifest` keeps the manifest, mixin config, and
  source-level hook tokens aligned.
- 2026-06-29: `RendererDomain` now includes an explicit `postProcessing`
  diagnostic key so the domain registry covers every non-terrain category named
  by this plan. Follow-up work is split into active plans for
  [mesh replacement device-loss](mesh-replacement-device-loss-bugfix.md),
  [non-terrain domain contracts](non-terrain-domain-contracts.md), and
  [RT/PT acceleration data](rt-pt-acceleration-data.md).
- 2026-06-29: `RendererDomainContract` now exposes per-domain compatibility
  contracts in renderer diagnostics. Non-terrain domains remain
  `replacementAllowed=false` with concrete fallback reasons. mcdev confirmed
  the 26.2 owner classes used so far, including `PostPass`/`PostChainConfig`
  instead of a nonexistent `PostChain`.
- 2026-06-29: RT/PT acceleration-data diagnostics now exist without enabling
  allocation or startup requirements. `RtPtAccelerationDataRegistry` reports
  allocation disabled state, page lifecycle counters, device-lost clears, and
  fallback reason counts in bug reports; `RtPtAccelerationPage` ties future
  acceleration records to GPU-world section IDs, revisions, and page kinds.

## mcdev Findings

mcdev static inspection of `26.2` and `26.3-snapshot-1` changes the plan shape:

- `26.2` `VulkanBackend` still owns mutable static
  `REQUIRED_DEVICE_EXTENSIONS` and `REQUIRED_DEVICE_FEATURES`, plus private
  `isDeviceSuitable`, `throwForMissingRequrements`, `createDevice`, and
  `createVma` methods.
- `26.2` `VulkanDevice` receives a raw enabled device-extension set in its
  constructor.
- `26.2` `VulkanPNextStruct` is a raw `(sType, structSize)` record, and
  `VulkanFeature` requires explicit offsets.
- `26.3-snapshot-1` moves Vulkan capabilities into `VulkanFeatureSets` and
  `init.FeatureSet`, removes the mutable capability fields from
  `VulkanBackend`, and changes `VulkanDevice` to receive a composed
  `FeatureSet`.

Therefore this plan targets `26.2` directly. The snapshot is useful design
evidence for feature-set grouping, but code must not assume snapshot classes or
constructor shapes exist on the stable `26.2` target.

## Optional Worldgen Provider Findings

Local external-reference notes describe an optional route for
GPU-authoritative world-generation experiments. Public plans keep that evidence
at the inferred-architecture level: GPU-authoritative worldgen remains outside
the default Vulkan renderer path, but a future optional provider integration can
add explicit dependency detection, user opt-in, backup warnings, and
compatibility gates.
The dedicated public plan is
[accelerated-worldgen provider integration](accelerated-worldgen-provider-integration.md).

## Constraints

- Keep the implementation on stable `26.2`; do not move the mod to a snapshot
  to obtain Mojang's newer feature-set classes.
- Preserve vanilla `PreferredGraphicsApi.DEFAULT` behavior. On 26.2 that means
  OpenGL is tried before Vulkan; VIM must not silently convert missing options
  entries to Vulkan.
- Do not initialize VIM terrain capture or renderer services when the active
  graphics backend is OpenGL.
- Keep mixins thin. Mojang-specific adaptation stays in `mixin.client`; reusable
  renderer behavior stays in the `client.vulkan.*` domain packages.
- Use mcdev for every Minecraft source inspection that affects mixin targets,
  method signatures, constructor descriptors, or runtime validation.
- Do not make GPU-authoritative vanilla world generation part of the default
  renderer path. CPU-authoritative Minecraft chunk state remains the default
  source of truth.
- Treat optional accelerated-worldgen providers as integration paths, not hard
  dependencies and not substitutes for the renderer's mirror-derived GPU world
  database.
- Preserve a safe fallback story for renderer domains whose Minecraft semantics
  are not yet represented by the GPU path.
- Treat runtime evidence as required for renderer behavior changes. Compile
  success alone is not sufficient.

## Steps

### 1. Repair Plan State

- Keep the Grok dependency-update result as a completed plan, not the active
  renderer roadmap.
- Keep this plan as the active plan until its exit criteria are met.
- Keep `tech-debt-tracker.md` linked to this active plan for runtime matrix,
  non-terrain replacement, and build-time SPIR-V work.

### 2. Introduce a 26.2-Local Vulkan Feature-Set Model

- Refactor `VulkanFeatureRequirements` into explicit groups:
  base Minecraft/Vulkan requirements, VIM hard requirements,
  required-if-enabled extension sets, optional acceleration sets, and RT/PT
  readiness sets.
- Keep a 26.2 adapter that emits the raw `Set<String>` and `Set<VulkanFeature>`
  needed by `VulkanBackendMixin`.
- Do not depend on `com.mojang.blaze3d.vulkan.VulkanFeatureSets` or
  `init.FeatureSet`; those are snapshot-only in the inspected mcdev data.
- Add invariant checks for feature-set membership, extension names, feature
  names, and disabled-reason reporting.
- Revisit which current requirements are truly startup-hard. If fragment
  shading rate, present-id/wait, descriptor heap, or other features remain
  hard requirements, document the reason and keep the failure message precise.

### 3. Harden the 26.2 VulkanBackend Integration

- Keep `VulkanBackendMixin` scoped to the mcdev-confirmed 26.2 surface:
  `<clinit>` requirement extension, `createVma`, `isDeviceSuitable`, and
  `throwForMissingRequrements`.
- Centralize the missing-capability message and reason selection so failures
  distinguish API version, missing extension, missing feature, and known
  problematic device cases.
- Record the exact mcdev class/method summaries used to validate the mixin
  descriptors before changing the mixin.
- Add a small source-level manifest or check that documents every Mojang target
  touched by the Vulkan requirement mixins.
- Done through `docs/references/minecraft-26-4-mixin-targets.md` and
  `checkMinecraft26MixinTargetManifest`, covering the 26.2 Vulkan, OpenGL,
  Video Settings, and terrain capture mixin targets.

### 4. Make the Runtime Profile the Capability Contract

- Extend `VulkanImprovementCapabilities` and `VulkanRuntimeProfile` so they
  report supported, enabled, selected, and disabled states separately.
- Make selected paths explicit for descriptor buffer, descriptor heap,
  mesh/task shader dispatch, fragment shading rate, present pacing,
  multi-draw fallback, device-generated commands, and RT/PT readiness.
- Ensure `RendererDiagnostics.logBugReport()` exposes the selected profile and
  missing/disabled reasons without requiring manual log interpretation.

### 5. Extract Shared Renderer Core Services

- Pull shared device-owned services out of `MeshTerrainRenderer` where they are
  no longer terrain-specific: lifecycle state, resource retirement, descriptor
  resources, frame/pass diagnostics, command-generation support, and capability
  access.
- Done for lifecycle state, renderer-domain reset, present pacing, and
  fragment shading-rate controller configuration through `RendererCoreServices`.
  Terrain descriptor resources, mesh program ownership, resource retirement,
  and command-generation buffers remain terrain-owned until their contracts are
  generalized.
- Keep `MeshTerrainRenderer` as the terrain-domain user of the shared services,
  not the owner of renderer-wide policy.
- Preserve existing shutdown and device-loss behavior while moving ownership.

### 6. Define the First GPU World Database Contracts

- Introduce renderer-owned section identity, revision, material-table, and dirty
  update contracts that terrain can use first and non-terrain domains can share
  later.
- Keep CPU chunk state authoritative. GPU pages are derived render data.
- Separate canonical mirrored section pages from future render-only LOD or
  predictive distant-terrain pages.
- Extend pure invariant checks for section IDs, revision rollover, material IDs,
  and buffer layout constants before adding broader GPU producers.

### 7. Extend the Terrain GPU Work Pipeline

- Keep default Vulkan sessions on vanilla terrain; require explicit
  `vim.enableTerrainCaptureBootstrap=true` for terrain mirror diagnostics and
  explicit `vim.replaceVanillaTerrain=true` for visible mesh replacement until
  runtime validation proves either path is safe to promote.
- Keep visible mesh replacement, work-queue mesh-task dispatch, and
  GPU-generated mesh-task indirect commands behind explicit user/developer
  opt-in flags.
- Keep vanilla cancellation behind `vim.strictMeshTerrainReplacement=false` by
  default; a successful visible mesh dispatch is diagnostic evidence, not proof
  of complete replacement coverage.
- Keep the CPU visible-meshlet ring disabled by default and treat it as a
  validation or emergency fallback only.
- Add GPU-side culling and compaction only behind explicit capability/profile
  gates, with counters for skipped, culled, emitted, truncated, and fallback
  work.
- Preserve vanilla ordering constraints and strict replacement diagnostics.
  Visible mesh replacement currently covers replaceable solid/cutout terrain;
  translucent terrain, underwater overlays, and environment screen effects stay
  vanilla-rendered until their compatibility contract is implemented.

### 8. Stage Non-Terrain Domains Without Premature Replacement

- Use `RendererDomainRegistry` to record entities, block entities, particles,
  sky, clouds, weather, and post-processing as explicit domains with observed
  vanilla paths and fallback reasons.
- Done for the registry surface: terrain, entities, block entities, items,
  particles, sky, clouds, weather, translucent effects, post-processing, and
  diagnostics now have explicit diagnostic keys. Contract details continue in
  [non-terrain-domain-contracts.md](non-terrain-domain-contracts.md).
- Done for initial contract diagnostics: each domain now exposes a
  `RendererDomainContract`, and all non-terrain render domains remain
  `replacementAllowed=false` with concrete fallback reasons until their
  compatibility contracts are implemented.
- Add GPU-facing contracts only after each domain has mapped Minecraft asset,
  material, animation, ordering, and mod-compatibility requirements.
- Do not replace a non-terrain domain simply to satisfy the roadmap. Replacement
  starts when a domain has its compatibility contract and runtime evidence.

### 9. Plan Optional Accelerated-Worldgen Provider Integration

- Add dependency detection for an optional accelerated-worldgen provider without
  making it a required mod dependency.
- Decide whether Fabric metadata should use a soft relationship such as
  suggested/recommended dependencies, then verify the exact loader metadata
  behavior before editing `fabric.mod.json`.
- Gate GPU-authoritative worldgen experiments behind explicit user opt-in,
  clear diagnostics, and world-backup warnings.
- Treat provider output as authoritative only inside worlds/configurations where
  that provider itself owns the worldgen path. The renderer still consumes
  generated chunk state through Minecraft/provider boundaries instead of
  silently inventing gameplay chunk state.
- Capture compatibility state in diagnostics: provider present, accelerated
  path active, worldgen stages accelerated, and known incompatibility/fallback
  reason.
- Keep render-only distant terrain and LOD pages separate from provider-owned
  gameplay chunk generation. They solve different contracts and must not share
  authority labels.

### 10. Finish the Shader Build Pipeline

- Convert terrain shader validation from best-effort to release-grade
  validation: either require `glslangValidator` for release builds or document a
  reproducible local/CI setup that enforces SPIR-V generation.
- Done through `-Pvim.releaseBuild=true`, which requires `glslangValidator` for
  both `compileTerrainSpirv` and `checkTerrainShaders`. In PowerShell, quote it
  as `"-Pvim.releaseBuild=true"`.
- Keep Java layout manifests and GLSL layout assumptions checked together.
- Done through `checkTerrainLayoutManifest`,
  `checkShaderBuildPipelineInvariants`, and `checkTerrainShaders`.
- Add generated shader artifacts only if the repository policy is updated to
  make them intentional source artifacts.

## Validation

- `.\gradlew.bat checkRepoDocs`
- `.\gradlew.bat checkClientMixinConfig`
- `.\gradlew.bat checkVulkanPackageBoundaries`
- `.\gradlew.bat checkTerrainLayoutManifest`
- `.\gradlew.bat checkMinecraft26MixinTargetManifest`
- `.\gradlew.bat checkTerrainRuntimeValidationPlanInvariants`
- Existing invariant checks plus new feature-set/profile/world-database checks.
- `.\gradlew.bat checkGpuWorldDatabaseInvariants`
- `.\gradlew.bat checkRendererDomainInvariants`
- `.\gradlew.bat checkRtPtAccelerationDataInvariants`
- `.\gradlew.bat check`
- `.\gradlew.bat build`
- mcdev static inspection for every touched Mojang class and method descriptor,
  at minimum:
  - `com.mojang.blaze3d.vulkan.VulkanBackend`
  - `com.mojang.blaze3d.vulkan.VulkanDevice`
  - `com.mojang.blaze3d.vulkan.VulkanPhysicalDevice`
  - `com.mojang.blaze3d.vulkan.init.VulkanPNextStruct`
  - `com.mojang.blaze3d.vulkan.init.VulkanFeature`
  - terrain render-pass and chunk-section mixin targets
- mcdev runtime smoke after behavior changes:
  - log capability profile and renderer diagnostics;
  - include video-settings and texture-binding diagnostics when investigating
    texture quality, especially `textureFiltering`, `mipmapLevels`, and
    captured sampler `maxAnisotropy`, `minFilter`, `magFilter`, and `maxLod`;
  - enter an overworld scene with `vim.enableTerrainCaptureBootstrap=true` or
    `vim.replaceVanillaTerrain=true`;
  - test mesh terrain replacement only as an explicit opt-in validation path;
  - test strict cancellation separately from mesh diagnostic overlay/fallback
    mode by toggling `vim.strictMeshTerrainReplacement`;
  - run an all-on VIM diagnostic profile, but assert that uncontracted
    translucent/environment effects remain vanilla-rendered rather than visibly
    mesh-replaced;
  - capture screenshot/video evidence;
  - test fallback flags and validation-only flags;
  - record validation-layer warnings where available.

## Decisions

- Stable `26.2` remains the implementation target.
- `26.3-snapshot-1` is design evidence only until the project intentionally
  opens a snapshot-port plan.
- The mod owns its feature-set abstraction for `26.2`; it does not backport
  Mojang snapshot classes.
- GPU-driven rendering is the mainline renderer direction, but the default
  runtime mode is vanilla terrain with VIM terrain capture/bootstrap disabled
  until capture cost and mesh replacement stability are validated.
- Mesh replacement is validation-gated after the 2026-06-29 device-loss
  reproduction in the visible work-queue path. The later render-pass state and
  vanilla camera-projection fixes improve the path, but they are not yet enough
  evidence to make mesh replacement the default.
- The 2026-07-01 max-feature recording shows visible translucent replacement
  and full-layer task truncation are still outside the safe replacement
  contract. Solid/cutout terrain can continue to advance behind opt-in flags;
  translucent terrain and environment screen effects stay vanilla until a
  separate compatibility contract exists.
- Full-layer `drawAll` is a diagnostic solid/cutout path. It must not use the
  shared terrain work queue because the layer meshlet range is already
  contiguous and the work queue can wrap heavily under all-on stress.
- Strict visible replacement is still unsafe after `req_63`; the inferred
  GPU-driven direction should replace per-draw cancellation with GPU-resident
  scene data, visibility/compaction, explicit residency, and indirect command
  production before vanilla terrain is canceled by default.
  The active [GPU-resident terrain architecture](gpu-resident-terrain-architecture.md)
  plan records this direction and the local-only external-reference boundary.
- VIM mesh replacement must sample the block atlas with texel fetch and strict
  cutout alpha until runtime evidence proves parity with vanilla texture
  sharpness and vegetation masking.
- GPU-authoritative vanilla world generation remains out of scope for the
  default client mod path, but an optional accelerated-worldgen provider is an
  acceptable integration track for explicit GPU-authoritative worldgen
  experiments.

## Exit Criteria

This plan is complete when:

- capability negotiation is grouped, checked, and documented;
- the Vulkan mixins are validated against mcdev `26.2` targets;
- renderer-wide services no longer live only inside terrain code;
- the first GPU world database contracts exist with pure checks;
- terrain capture/bootstrap remains an explicit runtime-validated opt-in on the
  stable `26.2` baseline, and mesh replacement either has a fixed runtime pass or
  [a dedicated active bugfix plan with crash evidence](mesh-replacement-device-loss-bugfix.md);
- follow-up non-terrain and RT/PT work has explicit domain contracts or new
  active plans:
  [non-terrain domain contracts](non-terrain-domain-contracts.md) and
  [RT/PT acceleration data](rt-pt-acceleration-data.md).

## Follow-Up

- Open a separate snapshot-port plan if `26.3` or later becomes the supported
  target.
- Open a dedicated accelerated-worldgen provider integration plan before
  implementing optional GPU-authoritative worldgen behavior:
  [accelerated-worldgen provider integration](accelerated-worldgen-provider-integration.md).
- Open domain-specific plans for entity, block-entity, particle, sky, cloud,
  weather, and post-processing replacement.
- Open a dedicated RT/PT acceleration-data plan after the shared GPU world
  database is stable.

## Completion

Closed on 2026-10-13. The exit criteria held on the 26.2 line: capability
negotiation is grouped, checked and documented; the Vulkan mixins were
validated against mcdev targets; renderer-wide services live outside the
terrain code; the GPU world database contracts exist with pure checks; terrain
capture stays an opt-in and mesh replacement has its own bugfix plan; and the
non-terrain and RT/PT follow-ups have their contracts.

The 26.4 port carried all of it over and changed three things this plan
decided:

- **Feature sets.** The mod no longer owns an abstraction over required
  extensions and features. Minecraft 26.4 has feature sets of its own and the
  mod adds its requirements to them.
- **Atlas sampling.** The rule that mesh replacement samples the block atlas
  with a texel fetch is withdrawn. Measured against vanilla on 26.4 it was the
  larger part of the difference; the mesh path now samples as vanilla does.
- **Draw groups.** The mapping from backend draws back to section layers is
  gone. Minecraft 26.4 collects a frame's section layers in one place and the
  mesh path takes them from there.

The mixin targets of this plan's mcdev Findings are 26.2 targets. The current
ones are in
[the 26.4 mixin target reference](../../references/minecraft-26-4-mixin-targets.md).
