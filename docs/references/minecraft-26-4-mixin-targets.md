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
  throws while it is set, and otherwise ends the open command buffers first,
  the object-init one behind a barrier on all work before it; private
  `commandBuffer()` returns the command buffer the frame is recorded into,
  starting one if none is open, and throws while a render pass is open.
  Final field `device`, whose `instance().debug()` is the `VulkanDebug` that
  `createRenderPass` opens a debug group of the pass's label with; public
  static `memoryBarrier(VkCommandBuffer, MemoryStack)`, the barrier on all
  commands and memory that `copyToBuffer` records after its copy.
- Source tokens: `VulkanRenderPass currentRenderPass`,
  `private VkCommandBuffer commandBuffer()`, `private VulkanDevice device`,
  `VulkanCommandEncoder.memoryBarrier(`.
- Purpose: let the terrain path refuse to record compute work while a render
  pass is open instead of failing the frame, and record its page uploads and
  GPU-driven pass into the frame's own command buffer. Record the compute
  dispatches of `ComputeCommands` into that command buffer, in a debug group
  of their label, each followed by the barrier Minecraft records after a copy.

### FrontendCommandEncoderMixin

- Target: `com.mojang.renderpearl.frontend.FrontendCommandEncoder`.
- mcdev 26.4 surface: final field `backend` of type `CommandEncoderBackend`,
  which every command is handed to once checked; field `isInRenderPass`,
  which `copyToBuffer` and the other commands throw on with "Close the
  existing render pass before performing additional commands".
- Source tokens: `private CommandEncoderBackend backend`,
  `private boolean isInRenderPass`.
- Purpose: add compute dispatch to Minecraft's command encoder the way it adds
  its own commands: checked here, then recorded by the backend.

### VulkanConstMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanConst`.
- mcdev 26.4 surface: public static `bufferUsageToVk(int)`, which turns the
  `GpuBuffer` usage bits (1 to 512) into `VkBufferUsageFlags` for
  `VulkanGpuBuffer.Direct`; `FrontendGpuDevice.createBuffer` passes the usage
  through unchecked.
- Source tokens: `method = "bufferUsageToVk"`.
- Purpose: turn the usage the mod adds, `ShaderStorage.USAGE_SHADER_STORAGE`,
  into storage-buffer and device-address use.

### VulkanGpuBufferDirectMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer.Direct`.
- mcdev 26.4 surface: the class `VulkanDevice.createBuffer` makes every buffer
  of; protected final field `device`; `vkBuffer()`, inherited from
  `VulkanGpuBuffer`. The allocator is created with buffer device addresses
  (see `VulkanBackendMixin`).
- Source tokens: `protected VulkanDevice device`, `vkBuffer()`.
- Purpose: give a buffer made with `ShaderStorage.USAGE_SHADER_STORAGE` its
  device address, asked of the driver once.

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
  final field `visibleSections`, the `ObjectArrayList` of the
  `RenderSection`s in view; final field `optionsRenderState`, whose
  `chunkSectionFadeInTime` is the fade-in in seconds, which
  `Util.toMillis(double)` turns into the fade duration Minecraft uses;
  private `extractSectionDrawGroups(boolean, List, Map)`, called by
  `prepareChunkRenders` and by `prepareChunkRendersIndirect` before the frame
  graph opens a render pass; public `invalidateCompiledGeometry`,
  `resetLevelRenderData` and `close`.
  Inside `extractSectionDrawGroups`, for each section of `visibleSections` and
  each `ChunkSectionLayer`, Minecraft calls
  `SectionRenderDispatcher.getRenderSectionSlice(SectionMesh, ChunkSectionLayer)`
  and builds a draw only when it returns a slice and the layer's
  `SectionMesh.SectionDraw`, looked up just before, is not null; its locals
  include that draw, the `RenderSection` and, as its first two `long`s, the
  fade duration and the time, which `RenderSection.getVisibility(long, long)`
  takes. It walks `visibleSections` through the one
  `ObjectArrayList.iterator()` call in the method.
- Source tokens: `private LevelRenderState levelRenderState`,
  `private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections`,
  `private OptionsRenderState optionsRenderState`,
  `method = "extractSectionDrawGroups"`,
  `SectionRenderDispatcher;getRenderSectionSlice(`, `ObjectArrayList;iterator()`,
  `method = "invalidateCompiledGeometry"`, `method = "resetLevelRenderData"`,
  `method = "close"`.
- Purpose: bracket the collection of a frame's section layers, record them
  in Minecraft's draw order and, in strict replacement, take the section
  layers the mesh path draws out of it (a `@Redirect` of the slice lookup
  that returns no slice for a taken layer, and in such a frame skips the
  lookup for a layer the section has nothing in), prepare the mesh path once
  they are known, and clear VIM
  terrain-derived caches for geometry invalidation, level reset and renderer
  close. The lookup runs for every layer of every visible section, so the
  hook calls Minecraft's method directly and returns at once while no frame
  is collected: a MixinExtras `@WrapOperation` there, through its operation
  object, cost about a tenth of the render thread at render distance 32 even
  with the mesh path off, and an `@Inject` into the lookup itself, with its
  callback object, about a twentieth. In a GPU-driven frame a `@Redirect` of
  the iterator call hands Minecraft only the visible sections that draw
  something the GPU does not; the GPU draws the others whole from a grid of
  the sections in view, built from `visibleSections` before the walk.

### SectionRenderDispatcherRenderSectionMixin

- Target:
  `net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection`.
- mcdev 26.4 surface: volatile field `sectionNode`; private method
  `addSectionBuffersToUberBuffer`; private field `uploadedTime`, the time the
  section was first shown, set once by `updateUploadTime()` and cleared by
  `reset()`, which `getVisibility(long, long)` fades in from; private
  `setSectionMesh(SectionMesh)`, which shows a mesh in the section, notes
  `uploadedTime` if it was not set, and returns the mesh it replaces, which
  its caller `checkSectionMesh` then releases. A compiled mesh's buffers are
  added to the uber-buffers before it is shown.
- Source tokens: `private long sectionNode`,
  `method = "addSectionBuffersToUberBuffer"`, `private long uploadedTime`,
  `method = "setSectionMesh"`.
- Purpose: capture terrain vertex and index payloads at the exact point vanilla
  inserts compiled section buffers into the chunk uber-buffers, and note when
  Minecraft shows a mesh, and from when its section fades in: a mesh's layers
  get pages on the GPU only once it is shown, so a section has the pages of
  one mesh only.

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
  mesh, and carry the mod's record of the mesh in a field, so a frame finds it
  without a map lookup.
