# Minecraft 26.4 Snapshot Port

**Status:** Active. Started 2026-10-13 on branch `mc-26.4-snapshot`. The branch
does not compile yet.

## Goal

Run the mod on the Minecraft 26.4 snapshots, starting with `26.4-snapshot-3`,
without losing the behaviour the 26.2 line has.

## Constraints

- The 26.4 snapshots move the GPU abstraction and both backends out of
  `com.mojang.blaze3d` into a new library, `com.mojang.renderpearl`. Every
  Vulkan mixin target changed package and several changed shape.
- Windowing moved from GLFW to SDL. `org.lwjgl.glfw` is no longer on the
  classpath.
- Vanilla now has its own indirect terrain path
  (`LevelRenderer.prepareChunkRendersIndirect`, `RenderPass.drawIndexedIndirect`,
  the optional `EXT MultiDraw` feature set). This overlaps the mod's mesh-task
  command generation and changes what "replace vanilla terrain" has to hook.
- Loom `1.18` needs Gradle `9.7`; the wrapper is `9.6.1`. Loom `1.17.13`
  resolves the snapshot, so the wrapper is unchanged.
- On the development machine the snapshot client dies at startup with a native
  access violation while implicit Vulkan layers are active (ReShade, Steam, Epic
  and OBS overlay layers are registered). Start it with
  `VK_LOADER_LAYERS_DISABLE=~implicit~` until the layer at fault is known. The
  26.2 client runs with the same layers, so the snapshot's new DXGI presentation
  path (`D3D12GpuSurface`, the `DXGI Surface` feature set) is the suspect.

## Steps

1. Done: target `26.4-snapshot-3`, Fabric Loader `0.19.5`, Fabric API
   `0.162.2+26.4` in `gradle.properties`.
2. Done: relocate the imports and mixin descriptors that only changed package
   (table below). This took the compile from 100 errors to 23.
3. Open: fix the 23 remaining compile errors (list below).
4. Open: retarget the mixins whose targets changed shape (list below). Mixin
   targets are checked when the game starts, not by `javac`, and every injector
   is required.
5. Open: decide how terrain replacement attaches to the restructured chunk
   draw path, then port `MeshTerrainRenderer`, `MeshShaderTerrainProgram` and
   `TerrainRenderContext`.
6. Open: update `ARCHITECTURE.md`, `docs/RELIABILITY.md` and the Gradle
   invariant checks that name 26.2 classes.

### Package relocations applied

| 26.2 | 26.4 snapshot |
|---|---|
| `com.mojang.blaze3d.vulkan.*` (with `checkpoints`, `init`) | `com.mojang.renderpearl.backend.vulkan.*` |
| `com.mojang.blaze3d.opengl.GlBackend` | `com.mojang.renderpearl.backend.opengl.GlBackend` |
| `com.mojang.blaze3d.systems.GpuDevice`, `BackendCreationException` | `com.mojang.renderpearl.api.device.*` |
| `com.mojang.blaze3d.shaders.GpuDebugOptions` | `com.mojang.renderpearl.api.device.GpuDebugOptions` |
| `com.mojang.blaze3d.systems.RenderPass` | `com.mojang.renderpearl.api.commands.RenderPass` |
| `com.mojang.blaze3d.buffers.GpuFence` | `com.mojang.renderpearl.api.commands.GpuFence` |
| `com.mojang.blaze3d.buffers.GpuBuffer`, `GpuBufferSlice` | `com.mojang.renderpearl.api.buffers.*` |
| `com.mojang.blaze3d.textures.GpuSampler`, `GpuTextureView` | `com.mojang.renderpearl.api.textures.*` |
| `com.mojang.blaze3d.IndexType` | `com.mojang.renderpearl.api.pipeline.IndexType` |
| `com.mojang.blaze3d.pipeline.BlendFunction`, `ColorTargetState`, `DepthStencilState` | `com.mojang.renderpearl.api.pipeline.*` |
| `com.mojang.blaze3d.shaders.ShaderSource` | `com.mojang.blaze3d.pipeline.ShaderSource` |
| `net.minecraft.client.renderer.DynamicUniforms` | `net.minecraft.client.renderer.DynamicGpuData` |

### Remaining compile errors

- `VulkanFeatureRequirements`: `VulkanPNextStruct` is now built from the LWJGL
  struct class (`new VulkanPNextStruct(VkPhysicalDeviceMeshShaderFeaturesEXT.class)`),
  and a feature is named by its struct field (`new VulkanFeature(struct, "meshShader")`).
- `VulkanImprovementClient`: the key binding uses `InputConstants.Type.KEYSYM`
  and a GLFW key code. The type is now `KEYBOARD` and codes are SDL scancodes.
- `VulkanVideoSettingsCompatibility`: `GameRenderState.useShaderTransparency()`
  is gone.
- `MeshShaderTerrainProgram`: `VulkanRenderPipeline` no longer exposes `info()`
  (it keeps `device()`, `vkPipeline()`, `pipelineLayout()`,
  `descriptorSetLayout()`, `uniforms()`); `getColorTargetStates()` returns a list.
- `MeshTerrainRenderer`: `ChunkSectionsToRender.drawGroupsPerLayer()` is gone.
  The class is now abstract with a `render(layer, renderPass, ...)` method per
  draw strategy.
- `TerrainRenderContext`: `ChunkSectionLayer.pipeline()` takes
  `(boolean multiDraw, boolean cloudsInFog)`.
- `VulkanRenderPassMixin`: `VulkanRenderPipeline.isValid()`,
  `withDepthPipeline()` and `withoutDepthPipeline()` are gone. One Vulkan
  pipeline per compiled pipeline; depth is part of the render pass.

### Mixin targets

| Mixin | 26.4 status |
|---|---|
| `VulkanInstanceMixin` | `<init>` still calls `VkApplicationInfo.apiVersion`; it passes `VK_API_VERSION_1_2`. |
| `VulkanBackendMixin` | `createVma` still calls `vmaCreateAllocator`. `isDeviceSuitable` and `throwForMissingRequrements` are gone; device selection is `findPhysicalDevice` with `checkDeviceSuitability`, driven by `VulkanFeatureSets.requiredFeatureSets()`, `requiredIfExtensionsAvailableFeatureSets()` and `optionalFeatureSets()`. Those three return mutable sets and are the natural place to add the mod's `FeatureSet`s. |
| `VulkanDeviceMixin` | `<init>` still calls `VulkanPhysicalDevice.close()`; the constructor gained parameters (`FeatureSet enabledFeatureSet`, `CheckpointExtension`). |
| `VulkanGpuSurfaceMixin` | `present` still calls `vkQueuePresentKHR`. With the `DXGI Surface` feature set the game presents through `D3D12GpuSurface` instead, where present pacing would not run. |
| `VulkanRenderPassMixin` | `setPipeline` exists (takes `BackendRenderPipeline`). `bindTexture` is replaced by `setUniform(int, Object)`. `drawMultipleIndexed` moved to the frontend (`FrontendRenderPass`); the backend has `multiDrawIndexed`, `drawIndexedIndirect`. |
| `GlBackendMixin` | `createDevice` exists with a new signature. |
| `ChunkSectionInfoMixin` | The record is `DynamicGpuData.ChunkSectionInfo(int x, int y, int z, float visibility)`. |
| `ChunkSectionsToRenderMixin` | `renderGroup` exists with a new signature. |
| `CompiledSectionMeshMixin` | `close` exists. |
| `LevelRendererMixin` | `prepareChunkRenders`, `invalidateCompiledGeometry`, `resetLevelRenderData` and `close` exist. `prepareChunkRendersIndirect` is new and is not hooked. |
| `SectionRenderDispatcherMixin` | `getRenderSectionSlice` exists. |
| `SectionRenderDispatcherRenderSectionMixin` | `RenderSection.addSectionBuffersToUberBuffer` exists (private). |
| `VideoSettingsScreenMixin` | `addOptions` and `tick` exist. |

## Validation

- `.\gradlew.bat build` and the repository checks pass.
- `.\gradlew.bat runClient` reaches a world with the default vanilla-terrain
  mode and every mixin applied.
- A DebugBridge 26.4 build in `run/mods` answers `mc_connect`; a screenshot and
  a `mc_record_video` contact sheet show correct terrain in each renderer mode.
  A local DebugBridge branch `mc-26.4-snapshot` has the module
  (`debugbridge-26.4-2.0.0.jar`); it was exercised against the vanilla snapshot.

## Decisions

- Open: build terrain replacement on vanilla's new indirect path, or keep the
  mod's own capture and dispatch and hook it into the restructured
  `ChunkSectionsToRender`.
- Open: whether present pacing should also cover `D3D12GpuSurface`.

## Follow-Up

- Find which implicit Vulkan layer crashes the snapshot client and whether it
  is the DXGI presentation path.
- Move the wrapper to Gradle `9.7` or newer when Loom `1.18` is wanted.
