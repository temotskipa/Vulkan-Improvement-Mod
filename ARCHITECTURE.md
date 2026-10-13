# Architecture

This repository is a Fabric client mod that experiments with replacing or
augmenting vanilla terrain rendering through modern Vulkan capabilities.

## Runtime Flow

1. Fabric loads `VulkanImprovement` from `src/main` and
   `VulkanImprovementClient` from `src/client`.
2. Client mixins patch Minecraft's Vulkan backend, device, render pass, chunk
   section, and video settings classes. Their targets are listed in
   [the mixin target manifest](docs/references/minecraft-26-4-mixin-targets.md).
3. `VulkanFeatureSetsMixin` adds the mod's required extensions and features to
   the feature sets Minecraft requires of a Vulkan device, and
   `VulkanBackendMixin` adds the Vulkan 1.4 API requirement, before device
   selection completes.
4. `VulkanDeviceMixin` captures physical-device capabilities and configures the
   singleton `MeshTerrainRenderer`.
5. Chunk-section mixins mirror vanilla chunk geometry into `SectionMeshletStore`
   and related GPU buffers.
6. Each frame Minecraft collects the section layers it will draw.
   `LevelRendererMixin` and `SectionRenderDispatcherMixin` record them, and
   `MeshTerrainRenderer.prepareVisibleFrame` uploads terrain data and prepares
   mesh-task commands before Minecraft opens a render pass.
7. Inside Minecraft's terrain render pass, the layer mixins on both of its
   draw strategies call `MeshTerrainRenderer` once the layer's pipeline is
   bound, to draw mesh terrain when configured and safe; otherwise the vanilla
   draws for the layer run unchanged.

## Source Sets

- `src/main/java`: shared mod entrypoint only. Keep renderer behavior out of
  this source set.
- `src/main/resources`: Fabric mod metadata and non-client mixin config.
- `src/client/java`: client initializer, Vulkan renderer code, and client
  mixins.
- `src/client/resources`: client mixin config, client assets, and terrain GLSL
  shader resources.

## Package Boundaries

- `client.vulkan.device`: Owns Vulkan feature requirements, capability
  snapshots, runtime profile grouping, and requirement scope metadata.
- `client.vulkan.runtime`: Owns renderer lifecycle services, domain registry
  diagnostics, bug-report serialization, backend-active checks, and Video
  Settings option construction.
- `client.vulkan.terrain`: Owns terrain meshlet storage, GPU buffer layout,
  mesh shader dispatch, descriptor resources, terrain material records,
  fragment shading rate, and renderer debug flags.
- `client.vulkan.gpuworld`: Owns the CPU-authoritative GPU-world mirror,
  section identity, revisions, page-kind metadata, residency-budget diagnostics,
  pipeline-stage contracts, and dirty-update contracts.
- `client.vulkan.rtpt`: Owns RT/PT acceleration-data diagnostics and fallback
  accounting for future acceleration structure work.
- `client.vulkan.presentation`: Owns Vulkan present pacing and present
  ID/wait diagnostics.
- `mixin.client`: Owns integration points into Minecraft and Mojang's Vulkan
  classes. Keep mixin classes thin; move reusable behavior into the
  `client.vulkan.*` packages.
- `client`: Owns Fabric client initialization and top-level logging.

The dependency direction should stay:

```text
mixin.client -> client.vulkan.* -> Minecraft/LWJGL/Fabric APIs
client       -> client.vulkan.runtime
main         -> Fabric initializer only
```

Renderer packages under `client.vulkan.*` must not depend on `mixin.client`.
They must also stay in one of the allowed renderer package directories so new
renderer code does not drift back into a catch-all root package.

## Important Runtime Contracts

- `VulkanFeatureRequirements` is the source of truth for required Vulkan API
  version, extensions, and features.
- `VulkanImprovementCapabilities` captures device limits and optional extension
  availability for diagnostics and feature decisions.
- `VulkanRuntimeProfile` groups captured capabilities into hard requirements,
  preferred acceleration features, RT/PT-readiness features, and selected paths.
- `RendererLifecycleState` records whether the renderer is unconfigured,
  configuring, ready, failed, shutting down, or device-lost.
- `RendererDomainRegistry` records the selected path and CPU/GPU work split for
  terrain and non-terrain renderer domains.
- The mod extends Minecraft's terrain drawing and does not replace how a
  frame's sections are chosen. The visible set is the one
  `LevelRenderer.extractSectionDrawGroups` collects for Minecraft's own draws,
  and a layer is drawn by the mesh path only when every section layer in that
  set has meshlets on the GPU.
- Nothing is uploaded and no compute work is recorded while Minecraft's
  terrain render pass is open. Minecraft's command encoder fails the frame
  otherwise.
- `TerrainPipelineState` is the rasterization, depth and blend state Minecraft
  compiled a pipeline with, remembered at compile time because the Vulkan
  pipeline no longer keeps it. A mesh pipeline is built from it, for the
  render pass Minecraft's pipeline uses, and cached by it.
- `TerrainDrawContext` snapshots one terrain layer about to be drawn inside
  Minecraft's render pass: command buffer, vanilla pipeline state, layer
  ordinal, and debug draw-all mode.
- `TerrainMeshTaskDispatch` records the terrain mesh-task dispatch source,
  direct task cap, meshlet offset, visible-list address, work-queue address,
  indirect command buffer/offset, and truncation state. The current path can
  submit a CPU-written `VkDrawMeshTasksIndirectCommandEXT` record when prepared
  GPU command generation is unavailable. Frame preparation writes the work
  queues before Minecraft opens the terrain render pass: visible replacement
  queues one prepared dispatch per layer, more only when a layer exceeds the
  mesh-task limit of one dispatch. The command
  generation compute pipeline fills each indirect mesh-task command from the
  work-queue counters. The older visible meshlet ring remains a fallback when a
  visible work queue cannot be uploaded.
- `TerrainVisibleMeshletRing` owns the pure allocation rules for the CPU-written
  visible meshlet ring, including wrap decisions and fence-blocked reuse
  diagnostics.
- `TerrainMeshTaskCommandLayout` owns the CPU/GPU layout for terrain
  mesh-task indirect command records and the compute push constants used to
  generate them from work queues.
- `GpuWorldResidencyBudget` and `GpuWorldPipelineStage` describe the next
  GPU-resident terrain contract: persistent section pages, visibility
  compaction, and indirect command generation should replace per-draw CPU
  terrain work-queue uploads before strict terrain replacement is promoted.
- `GpuWorldTerrainLayerDirectory` is the current resident terrain index. It is
  keyed by section node and chunk layer, not by vanilla draw objects, so later
  GPU visibility work can consume resident section-layer records without
  depending on transient render-pass draw slices.
- `TerrainGpuLayout` is the CPU-side source of truth for terrain buffer,
  meshlet, visible-list, and push-constant layout constants that shaders depend
  on.
- `DescriptorHeapTerrainResources` owns the first GPU material table: a vanilla
  terrain material record backed by captured block-atlas and lightmap texture
  bindings. `TerrainMaterialClassifier` assigns one material ID per Minecraft
  chunk render layer and records alpha mode, render-layer ordinal, and material
  domain metadata. Terrain meshlet headers carry a material ID, and the terrain
  mesh shader reads that table through buffer device address before forwarding
  material flags to the fragment shader. Its diagnostics also expose retired GPU
  resource-set, buffer, and byte counts so terrain capacity growth and shutdown
  pressure are visible.
- `TerrainRendererDebugConfig` exposes JVM system-property flags. Treat these as
  operational controls and keep docs synchronized when adding flags.
- `vulkanimprovement.client.mixins.json` must list every active client mixin
  class by simple name.
- `MeshTerrainRenderer.shutdown()` must release renderer resources when the
  Vulkan device closes.

## Build

Use `.\gradlew.bat build` for the full build, `.\gradlew.bat checkRepoDocs`
for repository-organization checks, `.\gradlew.bat checkClientMixinConfig` for
client mixin config/source synchronization, and
`.\gradlew.bat checkVulkanPackageBoundaries` for renderer package layout and
direction.
`.\gradlew.bat checkTerrainShaders` validates terrain GLSL when
`glslangValidator` is available. `.\gradlew.bat checkTerrainLayoutManifest`
validates CPU/shader terrain layout assumptions, including the default terrain
material record and material-table push constant. The Gradle build targets Java
25.
