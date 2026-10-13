# Minecraft 26.4 Mixin Targets

Verified with mcdev static analysis for Minecraft `26.4-snapshot-3` on
2026-10-13, and at runtime by starting the client with every mixin applied.

This manifest records the Mojang classes, fields, methods, and invocation
points touched by the client mixins.

Do not edit these target method names without re-running mcdev against the
Minecraft version in `gradle.properties` and updating this file in the same
change.

The 26.4 snapshots moved the GPU abstraction and both backends from
`com.mojang.blaze3d` to `com.mojang.renderpearl`, replaced the required
extension and feature lists with feature sets, and changed terrain drawing: a
frame's section layers are collected once and drawn by one of two strategies,
inside a render pass the level renderer has already opened.

## Vulkan Backend And Device Hooks

### VulkanBackendMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanBackend`.
- mcdev 26.4 surface: private static `createVma(VkDevice)`, which calls
  `Vma.vmaCreateAllocator`; private static
  `checkDeviceSuitability(VkPhysicalDevice, Set<FeatureSet>, Set<FeatureSet>)`,
  which returns the reason a device is unsuitable or `null`.
- Source tokens: `method = "createVma"`, `vmaCreateAllocator`,
  `method = "checkDeviceSuitability"`.
- Purpose: create the allocator for Vulkan 1.4 with buffer device addresses,
  and reject a device whose API version is below 1.4, which a feature set
  cannot express.

### VulkanFeatureSetsMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets`.
- mcdev 26.4 surface: public static `requiredFeatureSets()` returns a new
  mutable set on every call; `VulkanBackend` reads it for the availability
  probe and for device creation.
- Source tokens: `method = "requiredFeatureSets"`.
- Purpose: add the mod's required extensions and features as feature sets, so
  Minecraft checks, reports and enables them with its own.

### VulkanInstanceMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanInstance`.
- mcdev 26.4 surface: protected constructor creates `VkApplicationInfo` with
  the Vulkan 1.2 API version.
- Source tokens: `method = "<init>"`, `VkApplicationInfo;apiVersion(I)`.
- Purpose: request Vulkan 1.4 at instance creation while preserving the rest of
  vanilla instance setup.

### VulkanDeviceMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanDevice`.
- mcdev 26.4 surface: constructor receives `VulkanInstance`,
  `VulkanPhysicalDevice`, the enabled `FeatureSet`, `VkDevice`, the VMA handle
  and `CheckpointExtension`, and closes the physical device before it creates
  the command encoder; field `vkDevice`; method `close`.
- Source tokens: `private VkDevice vkDevice`, `method = "<init>"`,
  `FeatureSet enabledFeatureSet`, `VulkanPhysicalDevice;close()V`,
  `method = "close"`.
- Purpose: capture VIM runtime capabilities before the physical-device wrapper
  is closed and shut renderer services down before the Vulkan device is
  destroyed.

### VulkanGpuSurfaceMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanGpuSurface`.
- mcdev 26.4 surface: fields `device` and `swapchain`; method `present`; call
  to `KHRSwapchain.vkQueuePresentKHR`. With the `DXGI Surface` feature set
  Minecraft presents through `D3D12GpuSurface` instead and this class is not
  used.
- Source tokens: `private VulkanDevice device`, `private long swapchain`,
  `method = "present"`, `vkQueuePresentKHR`.
- Purpose: route present-id/present-wait pacing through
  `PresentPacingController` while keeping vanilla swapchain ownership.

### VulkanRenderPassMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanRenderPass`.
- mcdev 26.4 surface: fields `pipeline` and `anyDescriptorDirty`; private
  `commandBuffer()` method. The pass no longer has a `hasDepth` field, a
  `bindTexture` method or a `drawMultipleIndexed` method: depth belongs to the
  pipeline, uniforms are set by binding index, and multi-draw splitting moved
  to the frontend.
- Source tokens: `protected VulkanRenderPipeline pipeline`,
  `private boolean anyDescriptorDirty`,
  `private VkCommandBuffer commandBuffer()`.
- Purpose: give the mesh terrain path the command buffer and bound pipeline,
  and rebind Minecraft's pipeline and descriptors after a mesh draw.

### VulkanRenderPipelineMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline`.
- mcdev 26.4 surface: public static
  `compile(VulkanDevice, CompiledRenderPipeline.CreateInfo)` builds one Vulkan
  pipeline for a render pass taken from `RenderPassCache`; the final class
  keeps the handles but not the create info.
- Source tokens: `method = "compile"`,
  `CompiledRenderPipeline.CreateInfo pipelineCreateInfo`.
- Purpose: remember the rasterization, depth and blend state a pipeline was
  compiled with, from which the mesh pipeline that stands in for it is built.

### VulkanCommandEncoderMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder`.
- mcdev 26.4 surface: field `currentRenderPass`; `execute(VkCommandBuffer)`
  throws while it is set.
- Source tokens: `VulkanRenderPass currentRenderPass`.
- Purpose: let the terrain path refuse to record compute work while a render
  pass is open instead of failing the frame.

### FrontendRenderPassMixin

- Target: `com.mojang.renderpearl.frontend.FrontendRenderPass`.
- mcdev 26.4 surface: field `backend` of type `RenderPassBackend`; this is the
  `RenderPass` the level renderer hands to terrain rendering.
- Source tokens: `private RenderPassBackend backend`.
- Purpose: lead from the pass a renderer is handed to the Vulkan pass that
  records it.

## OpenGL And Settings Hooks

### GlBackendMixin

- Target: `com.mojang.renderpearl.backend.opengl.GlBackend`.
- mcdev 26.4 surface: public `createDevice(String, int, GpuDebugOptions)`.
- Source tokens: `method = "createDevice"`.
- Purpose: when the developer-only `vim.requireVulkanBackend` flag is enabled,
  reject OpenGL fallback after Vulkan selection or creation fails.

### VideoSettingsScreenMixin

- Target: `net.minecraft.client.gui.screens.options.VideoSettingsScreen`.
- mcdev 26.4 surface: protected `addOptions` and public `tick`.
- Source tokens: `method = "addOptions"`, `method = "tick"`.
- Purpose: append VIM Video Settings controls only when the active or selected
  graphics API makes them relevant, and refresh control enabled state live.

## Terrain Capture And Draw Hooks

### LevelRendererMixin

- Target: `net.minecraft.client.renderer.LevelRenderer`.
- mcdev 26.4 surface: final field `levelRenderState`, whose
  `renderWireframeTerrain` says whether the frame's terrain is wireframe;
  private `extractSectionDrawGroups(boolean, List, Map)`, called by
  `prepareChunkRenders` and by `prepareChunkRendersIndirect` before the frame
  graph opens a render pass; public `invalidateCompiledGeometry`,
  `resetLevelRenderData` and `close`.
- Source tokens: `private LevelRenderState levelRenderState`,
  `method = "extractSectionDrawGroups"`,
  `method = "invalidateCompiledGeometry"`, `method = "resetLevelRenderData"`,
  `method = "close"`.
- Purpose: bracket the collection of a frame's section layers, prepare the mesh
  path once they are known, and clear VIM terrain-derived caches for geometry
  invalidation, level reset and renderer close.

### SectionRenderDispatcherMixin

- Target: `net.minecraft.client.renderer.chunk.SectionRenderDispatcher`.
- mcdev 26.4 surface: method `getRenderSectionSlice` returns
  `SectionRenderDispatcher.@Nullable RenderSectionBufferSlice`;
  `extractSectionDrawGroups` calls it once per visible section layer, in draw
  order.
- Source tokens: `method = "getRenderSectionSlice"`,
  `RenderSectionBufferSlice`.
- Purpose: learn which section layers Minecraft draws this frame, and in which
  order.

### SectionRenderDispatcherRenderSectionMixin

- Target:
  `net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection`.
- mcdev 26.4 surface: volatile field `sectionNode`; private method
  `addSectionBuffersToUberBuffer`.
- Source tokens: `private long sectionNode`,
  `method = "addSectionBuffersToUberBuffer"`.
- Purpose: capture terrain vertex and index payloads at the exact point vanilla
  inserts compiled section buffers into the chunk uber-buffers.

### ChunkSectionsToRenderMixin

- Target: `net.minecraft.client.renderer.chunk.ChunkSectionsToRender`.
- mcdev 26.4 surface: abstract class; public
  `renderGroup(ChunkSectionLayerGroup, RenderPass, GpuSampler, GpuTextureView, ImprovedFogTextures, boolean)`
  is handed a render pass that is already open, the atlas and its sampler, the
  record `ImprovedFogTextures(skyView, cloudsView, cloudsDepthView)` of images
  terrain fades into when improved transparency is on (otherwise `null`), and
  whether terrain is drawn as wireframe.
- Source tokens: `method = "renderGroup"`,
  `ImprovedFogTextures improvedFogTextures`, `boolean renderWireframeTerrain`.
- Purpose: bracket vanilla terrain group rendering, record the textures
  Minecraft binds for it, the sky and cloud images of improved fog among them,
  and leave wireframe terrain to Minecraft.

### FogRendererMixin

- Target: `net.minecraft.client.renderer.fog.FogRenderer`.
- mcdev 26.4 surface: private static field `fogEnabled`; public
  `updateBuffer(FogData)`, which `GameRenderer` calls once a frame with the
  camera state's fog before the level is drawn. `FogData` has public fields
  `color`, `environmentalStart`, `environmentalEnd`, `renderDistanceStart` and
  `renderDistanceEnd`.
- Source tokens: `private static boolean fogEnabled`,
  `method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V"`.
- Purpose: remember the frame's fog so mesh terrain fades into it as
  Minecraft's terrain does.

### ChunkSectionsToRenderDrawIndirectMixin

- Target:
  `net.minecraft.client.renderer.chunk.ChunkSectionsToRender.DrawIndirect`.
- mcdev 26.4 surface: protected
  `render(ChunkSectionLayer, RenderPass, GpuBuffer, IndexType, boolean, RenderPipeline, RenderPipeline)`
  binds the layer's multi-draw pipeline with `RenderPass.setPipeline` and then
  issues `drawIndexedIndirect` over the frame's command buffer.
- Source tokens: `method = "render"`, `RenderPass;setPipeline(`.
- Purpose: draw the layer's meshlets once the layer's pipeline is bound, and
  skip Minecraft's indirect draws only when the mesh path covered them.

### ChunkSectionsToRenderDrawSeparateMixin

- Target:
  `net.minecraft.client.renderer.chunk.ChunkSectionsToRender.DrawSeparate`.
- mcdev 26.4 surface: protected `render` with the same parameters binds the
  layer's pipeline with `RenderPass.setPipeline` and then issues
  `drawMultipleIndexed`.
- Source tokens: `method = "render"`, `RenderPass;setPipeline(`.
- Purpose: the same hook for the path Minecraft uses when multi-draw-indirect
  is unavailable or switched off.

### ChunkSectionInfoMixin

- Target: `net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo`.
- mcdev 26.4 surface: record
  `ChunkSectionInfo(int x, int y, int z, float visibility)`.
- Source tokens: `method = "<init>"`, `float visibility`.
- Purpose: capture vanilla section visibility from the per-section data vanilla
  terrain rendering already prepares.

### CompiledSectionMeshMixin

- Target: `net.minecraft.client.renderer.chunk.CompiledSectionMesh`.
- mcdev 26.4 surface: method `close`.
- Source tokens: `method = "close"`.
- Purpose: release VIM meshlet mappings when vanilla closes a compiled section
  mesh.
