# Minecraft 26.2 Vulkan Renderer Extension

**Status:** Active.

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
  `0.153.0+26.2`, Fabric Loader `0.19.3`, and Loom `1.17.13`.
- The Gradle wrapper is `9.6.1`; the 2026-06-28, 2026-06-29, and
  2026-07-01 dependency
  checks found no newer stable 26.2 Fabric/Minecraft pins to take.
- The completed dependency update is recorded in
  [../completed/minecraft-26-2-dependency-update.md](../completed/minecraft-26-2-dependency-update.md).
- The previous terrain renderer foundation is recorded in
  [../completed/vulkan-1-4-renderer-refactor.md](../completed/vulkan-1-4-renderer-refactor.md).
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
  recorded in `docs/references/minecraft-26-2-mixin-targets.md`, and
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

## C2ME GitHub Findings

GitHub inspection on 2026-06-28 adds an optional route for GPU-authoritative
world-generation experiments:

- [RelativityMC/C2ME-fabric](https://github.com/RelativityMC/C2ME-fabric)
  is the public GitHub repository for base C2ME.
- [The `dev/26.2.0` branch](https://github.com/RelativityMC/C2ME-fabric/tree/dev/26.2.0)
  provides the relevant stable-Minecraft-26.2 C2ME source line.
- [The `0.4.1+beta.1` GitHub release](https://github.com/RelativityMC/C2ME-fabric/releases/tag/0.4.1%2Bbeta.1)
  explicitly mentions an OpenCL C backend, experimental OpenCL-accelerated
  world generation, and 26.2-compatible Fabric plus OCL builds.
- GitHub repository search did not find a standalone public `c2me-ocl` source
  repository. Treat the C2ME release page as the GitHub-side source of truth
  for the separate OCL project until a dedicated public GitHub repo appears.

This means GPU-authoritative worldgen should remain outside the default Vulkan
renderer path, but it can become an optional C2ME/C2ME-OCL integration track
with explicit dependency detection, user opt-in, backup warnings, and
compatibility gates.

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
- Treat C2ME plus `c2me-ocl` as an optional integration path, not a hard
  dependency and not a substitute for the renderer's mirror-derived GPU world
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
- Done through `docs/references/minecraft-26-2-mixin-targets.md` and
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
- Keep the CPU visible-meshlet ring disabled by default and treat it as a
  validation or emergency fallback only.
- Add GPU-side culling and compaction only behind explicit capability/profile
  gates, with counters for skipped, culled, emitted, truncated, and fallback
  work.
- Preserve vanilla ordering constraints, translucent opt-in behavior, and
  strict replacement diagnostics.

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

### 9. Plan Optional C2ME OpenCL Worldgen Integration

- Add dependency detection for C2ME and `c2me-ocl` without making either a
  required mod dependency.
- Decide whether Fabric metadata should use a soft relationship such as
  suggested/recommended dependencies, then verify the exact loader metadata
  behavior before editing `fabric.mod.json`.
- Gate GPU-authoritative worldgen experiments behind explicit user opt-in,
  clear diagnostics, and world-backup warnings.
- Treat C2ME-OCL output as authoritative only inside worlds/configurations where
  C2ME-OCL itself owns that worldgen path. The renderer still consumes generated
  chunk state through Minecraft/C2ME boundaries instead of silently inventing
  gameplay chunk state.
- Capture compatibility state in diagnostics: base C2ME present, C2ME-OCL
  present, OpenCL path active, worldgen stages accelerated, and known
  incompatibility/fallback reason.
- Keep render-only distant terrain and LOD pages separate from C2ME-OCL
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
    captured sampler `maxAnisotropy`;
  - enter an overworld scene with `vim.enableTerrainCaptureBootstrap=true` or
    `vim.replaceVanillaTerrain=true`;
  - test mesh terrain replacement only as an explicit opt-in validation path;
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
- GPU-authoritative vanilla world generation remains out of scope for the
  default client mod path, but C2ME plus `c2me-ocl` is an acceptable optional
  integration track for explicit GPU-authoritative worldgen experiments.

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
- Open a dedicated C2ME/C2ME-OCL integration plan before implementing optional
  GPU-authoritative worldgen behavior.
- Open domain-specific plans for entity, block-entity, particle, sky, cloud,
  weather, and post-processing replacement.
- Open a dedicated RT/PT acceleration-data plan after the shared GPU world
  database is stable.
