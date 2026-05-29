# Architecture

This repository is a Fabric client mod that experiments with replacing or
augmenting vanilla terrain rendering through modern Vulkan capabilities.

## Runtime Flow

1. Fabric loads `VulkanImprovement` from `src/main` and
   `VulkanImprovementClient` from `src/client`.
2. Client mixins patch Minecraft's Vulkan backend, device, render pass, chunk
   section, and video settings classes.
3. `VulkanBackendMixin` extends required Vulkan API, extension, and feature
   requirements before device selection completes.
4. `VulkanDeviceMixin` captures physical-device capabilities and configures the
   singleton `MeshTerrainRenderer`.
5. Chunk-section mixins mirror vanilla chunk geometry into `SectionMeshletStore`
   and related GPU buffers.
6. Render-pass mixins call `MeshTerrainRenderer` to draw mesh terrain when
   configured and safe; otherwise the vanilla path remains the fallback where
   supported by the active mode.

## Source Sets

- `src/main/java`: shared mod entrypoint only. Keep renderer behavior out of
  this source set.
- `src/main/resources`: Fabric mod metadata and non-client mixin config.
- `src/client/java`: client initializer, Vulkan renderer code, and client
  mixins.
- `src/client/resources`: client mixin config, client assets, and terrain GLSL
  shader resources.

## Package Boundaries

- `client.vulkan`: Owns Vulkan feature requirements, capability snapshots,
  terrain meshlet storage, GPU buffer management, mesh shader dispatch,
  descriptor resources, fragment shading rate, present pacing, and renderer
  debug flags.
- `mixin.client`: Owns integration points into Minecraft and Mojang's Vulkan
  classes. Keep mixin classes thin; move reusable behavior into `client.vulkan`.
- `client`: Owns Fabric client initialization and top-level logging.

The dependency direction should stay:

```text
mixin.client -> client.vulkan -> Minecraft/LWJGL/Fabric APIs
client       -> client.vulkan
main         -> Fabric initializer only
```

`client.vulkan` must not depend on `mixin.client`.

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
- `TerrainDrawContext` snapshots the terrain draw request from the Vulkan render
  pass, including command buffer, vanilla pipeline, layer ordinal, draw list,
  and debug draw-all mode.
- `TerrainMeshTaskDispatch` records the terrain mesh-task dispatch source,
  direct task cap, meshlet offset, visible-list address, work-queue address,
  indirect command buffer/offset, and truncation state. The current path can
  submit a CPU-written `VkDrawMeshTasksIndirectCommandEXT` record when prepared
  GPU command generation is unavailable. `ChunkSectionsToRenderMixin` prepares
  work queues before Minecraft creates the terrain render pass: normal visible
  draw-list replacement queues one prepared dispatch per vanilla draw group, and
  full-layer diagnostic mode queues one prepared dispatch per layer. The command
  generation compute pipeline fills each indirect mesh-task command from the
  work-queue counters. The older visible meshlet ring remains a fallback when a
  visible work queue cannot be uploaded.
- `TerrainVisibleMeshletRing` owns the pure allocation rules for the CPU-written
  visible meshlet ring, including wrap decisions and fence-blocked reuse
  diagnostics.
- `TerrainMeshTaskCommandLayout` owns the CPU/GPU layout for terrain
  mesh-task indirect command records and the compute push constants used to
  generate them from work queues.
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
`.\gradlew.bat checkVulkanPackageBoundaries` for renderer package direction.
`.\gradlew.bat checkTerrainShaders` validates terrain GLSL when
`glslangValidator` is available. `.\gradlew.bat checkTerrainLayoutManifest`
validates CPU/shader terrain layout assumptions, including the default terrain
material record and material-table push constant. The Gradle build targets Java
25.
