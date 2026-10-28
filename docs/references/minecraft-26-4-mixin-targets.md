# Minecraft 26.4 Mixin Targets

Verified with mcdev static analysis for Minecraft `26.4-snapshot-6` on
2026-10-28, and at runtime by starting the client with every mixin applied.
From `26.4-snapshot-5` twelve of the target classes changed. Snapshot-6
describes a render pass by a `RenderPassLayout` of attachment formats and
subpasses, and compiles a pipeline once for each subpass it draws in:
`PipelineBuilder.generateBackendCreateInfo` takes the subpass, the create info
carries it in place of a depth format, and `VulkanRenderPipeline.compile`
builds the pipeline for the layout's render pass and the subpass's index.
`VulkanRenderPipeline.compile` sets a descriptor's stages in two calls, and
`VulkanCommandEncoder.createRenderPass` takes its clear values from the native
heap and never frees them. In `VulkanConst`, `VulkanDevice`, `VulkanRenderPass`,
`FrontendCommandEncoder`, `FrontendRenderPass`, `SPIRVModule`, `LevelRenderer`
and `ChunkSectionsToRender.DrawIndirect` and `DrawSeparate` none of the members
or invocation points below changed.

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
  the command encoder; field `vkDevice`; method `close`;
  `createSurface(long, BooleanSupplier, boolean)` returns a `VulkanGpuSurface`,
  or a `D3D12GpuSurface` when its last argument asks for DXGI presentation.
- Source tokens: `private VkDevice vkDevice`, `method = "<init>"`,
  `FeatureSet enabledFeatureSet`, `VulkanPhysicalDevice;close()V`,
  `method = "close"`, `method = "createSurface"`, `instanceof VulkanGpuSurface`.
- Purpose: capture VIM runtime capabilities before the physical-device wrapper
  is closed, set up the GPU model path on the new device, tell present pacing
  whether Minecraft presents through the Vulkan swapchain, and shut renderer
  services down before the Vulkan device is destroyed.

### VulkanGpuSurfaceMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanGpuSurface`.
- mcdev 26.4 surface: fields `device` and `swapchain`; method `present`; call
  to `KHRSwapchain.vkQueuePresentKHR`. With the experimental Prefer DXGI
  Present video option on at startup, Minecraft presents through
  `D3D12GpuSurface` instead and this class is not used. The option is off by
  default.
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
  pipeline for the create info's subpass, in the render pass `RenderPassCache`
  gives the subpass's layout; the final class keeps the handles but not the
  create info.
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

### VulkanCommandEncoderFramebufferMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder`.
- mcdev 26.4 surface: `createRenderPass(RenderPassDescriptor)` fills a
  `VkFramebufferCreateInfo` with the render pass `RenderPassCache` gives the
  descriptor's layout and attachments, every attachment's image view in the
  layout's order and the size of the first attachment; calls
  `VK12.vkCreateFramebuffer`, then queues the framebuffer's
  destruction with `queueForDestroy(Destroyable)`, its only call of that
  method, which `DestructionQueue` runs two submits later, in the order
  queued. `destroy()` waits for the queue to idle and then empties the
  destruction queue. `RenderPassCache` destroys its render passes only when
  the device closes. Final field `device`.
- Source tokens: `method = "createRenderPass"`, `VK12;vkCreateFramebuffer(`,
  `VulkanCommandEncoder;queueForDestroy(`, `method = "destroy"`,
  `private VulkanDevice device`.
- Purpose: keep framebuffers across frames (`FramebufferCache`), behind
  `vim.keepFramebuffers`.

### VulkanCommandEncoderClearValuesMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder`.
- mcdev 26.4 surface: `createRenderPass(RenderPassDescriptor)` opens a
  `MemoryStack` named `stack` for the whole pass setup, but takes the clear
  value of each attachment from `VkClearValue.calloc(int)`, the native heap,
  and never frees the buffer after `vkCmdBeginRenderPass2` reads it. Its only
  call of that method.
- Source tokens: `method = "createRenderPass"`, `VkClearValue;calloc(I)`,
  `MemoryStack stack`.
- Purpose: take the clear values from that memory stack, where snapshot-5 took
  them, so a render pass leaks no memory.

### VulkanCommandEncoderBarrierMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder`.
- mcdev 26.4 surface: the transfers `clearColorTexture`,
  `clearColorAndDepthTextures`, `clearDepthTexture`, `writeToBuffer`,
  `copyToBuffer`, `writeToTexture`, `copyBufferToTexture`,
  `copyTextureToBuffer` and `copyTextureToTexture` each record one copy or
  clear into the command buffer the private `commandBuffer()` returns, then
  call the private `memoryBarrier(MemoryStack)`, which records the public
  static `VulkanCommandEncoder.memoryBarrier(VkCommandBuffer, MemoryStack)`
  into it: all commands before and after, memory read and write.
  `submitRenderPass` calls it too, after the render pass ends. Every other
  command for the frame's command buffer fetches it through `commandBuffer()`
  first, which opens `currentCommandBuffer` when none is open;
  `endCommandBuffer()` ends and queues it, for `submit()`, `execute`,
  `waitSemaphore` and `signalSemaphore`. Textures stay in the general layout,
  so transfers record no layout transitions. Buffers are `VulkanGpuBuffer`
  (public `vkBuffer()`), textures `VulkanGpuTexture` (public `vkImage()`).
- Source tokens: `method = "commandBuffer"`, `method = "endCommandBuffer"`,
  `method = "memoryBarrier(Lorg/lwjgl/system/MemoryStack;)V"`,
  `VkCommandBuffer currentCommandBuffer`, `method = "clearColorTexture"`,
  `method = "clearColorAndDepthTextures"`, `method = "clearDepthTexture"`,
  `method = "writeToBuffer"`, `method = "copyToBuffer"`,
  `method = "writeToTexture"`, `method = "copyBufferToTexture"`,
  `method = "copyTextureToBuffer"`, `method = "copyTextureToTexture"`,
  `VulkanCommandEncoder.memoryBarrier(`.
- Purpose: barriers by resource (`TransferBarriers`), behind
  `vim.barriersByResource`: hold back a transfer's barrier until a command
  needs it.

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

### VulkanGpuTextureViewMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView`.
- mcdev 26.4 surface: final fields `device` and `vkImageView`, field
  `closed`; `close()`, once, sets `closed` and queues the view for
  destruction with the device's command encoder (`createCommandEncoder()`
  returns the device's one encoder). The image view is destroyed only from
  that queue, so its handle cannot be reused before then.
- Source tokens: `private VulkanDevice device`, `private long vkImageView`,
  `private boolean closed`, `method = "close"`.
- Purpose: release the framebuffers `FramebufferCache` keeps with the view as
  an attachment, queued for destruction before the view.

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
  `FeatureRenderDispatcher;prepareFrame(`,
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
  the sections in view, built from `visibleSections` before the walk. Around
  the level's one call of `FeatureRenderDispatcher.prepareFrame(SubmitNodeStorage)`
  in `render`, which fills the feature groups' staged vertex buffer before the
  frame graph opens a render pass, it opens and closes the window in which the
  GPU model path takes model submits; Minecraft's other callers, the GUI item
  atlas, picture-in-picture, the hand and the 3D HUD, are left to Minecraft.

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
  `render(ChunkSectionLayer, RenderPass, GpuBuffer, IndexType, boolean, boolean, OitStage)`
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

## Model Hooks

These hooks let the GPU write the vertices of Minecraft's entity models into
Minecraft's own draws (see `client.vulkan.model.ModelGpuVertices`). They change
nothing unless `vim.gpuModelVertices` is on, and nothing while another
mod's mixins target one of the classes below. The mesh-stage hooks
(`PipelineBuilderMixin` to `VulkanRenderPassModelMeshMixin`) change nothing
unless `vim.gpuModelMeshShaders` was on as well when Minecraft built its
pipelines.

### ModelPartMixin

- Target: `net.minecraft.client.model.geom.ModelPart`.
- mcdev 26.4 surface: final field `cubes`, the part's `ModelPart.Cube`s;
  public `render(PoseStack, VertexConsumer, int, int, int)`, which for a visible
  part that is not `skipDraw` calls private
  `compile(PoseStack.Pose, VertexConsumer, int, int, int)` once with the posed
  part, before its children; `compile` calls `Cube.compile` for each cube, in
  order.
- Source tokens: `private List<ModelPart.Cube> cubes`,
  `method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"`,
  `ModelPart;compile(`.
- Purpose: hand a posed part's cubes to the GPU model path when the vertex
  consumer is the one it handed out, and to Minecraft's `compile` otherwise;
  remember how the part's cubes are laid out in the GPU path's cube store
  (`ModelPartResidency`), so a posed part is recorded without looking at its
  cubes.

### ModelPartCubeMixin

- Target: `net.minecraft.client.model.geom.ModelPart.Cube`.
- mcdev 26.4 surface: public final field `polygons`, each a `ModelPart.Polygon`
  record of four `ModelPart.Vertex` records and a normal, fixed once the cube is
  built; `compile(PoseStack.Pose, VertexConsumer, int, int, int)` writes each
  polygon's vertices with `Matrix4f.transformPosition` and the normal with
  `PoseStack.Pose.transformNormal`.
- Source tokens: none; the mixin only adds fields.
- Purpose: remember where the GPU model path stored the cube's faces, so a
  cube is looked at once.

### ModelFeatureRendererMixin

- Target: `net.minecraft.client.renderer.feature.ModelFeatureRenderer`.
- mcdev 26.4 surface: private `prepareModel(Submit)`, which sets its pose
  stack to the submit's pose, gets the vertex consumer with
  `getVertexBuilder(RenderType)`, wraps it for a sheeted decal or a UV mapping,
  and has the model render into it.
- Source tokens: `method = "prepareModel"`,
  `ModelFeatureRenderer;getVertexBuilder(`.
- Purpose: offer the submit to the GPU model path while Minecraft asks for its
  vertex consumer; the path takes only submits whose vertices it writes
  exactly as Minecraft would.

### RenderTypeFeatureRendererGroupMixin

- Target: `net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer.Group`.
- mcdev 26.4 surface: final fields `stagedBuffer`, `canReorder`, `draws` and
  `drawRenderTypes`; field `lastDraw`; public `getVertexBuilder(RenderType)`,
  which keeps filling `lastDraw` while the render type stays the same and can
  consolidate consecutive geometry, and otherwise asks private
  `getOrAddDraw(RenderType)`, which, in a group that can reorder, finds a draw
  of the same prepared render type with `List.indexOf`, and otherwise appends
  a draw to the staged buffer.
- Source tokens: `private StagedVertexBuffer stagedBuffer`,
  `private boolean canReorder`, `private List<StagedVertexBuffer.Draw> draws`,
  `private List<PreparedRenderType> drawRenderTypes`,
  `private StagedVertexBuffer.@Nullable Draw lastDraw`,
  `method = "getVertexBuilder"`, `method = "getOrAddDraw"`, `List;indexOf(`.
- Purpose: make GPU model draws draws of the group, in its lists, so Minecraft
  executes them in order with its render types, and keep Minecraft's own
  geometry out of them.

### StagedVertexBufferMixin

- Target: `net.minecraft.client.renderer.StagedVertexBuffer`.
- mcdev 26.4 surface: public `upload()`, which puts the frame's vertices and
  sorted indices into Minecraft buffers outside a render pass and has three
  returns in its bytecode: early when there are no draws, early when the
  draws hold no vertices of Minecraft's own, and at the end;
  `getExecuteInfo(Draw)`, which returns null for an empty draw and otherwise
  the vertex buffer, index buffer and counts to draw it with, using the
  sequential index buffer for a draw without quad sorting; `endDraw()` and
  `endFrame()`.
- Source tokens: `method = "upload", at = @At("RETURN")`,
  `method = "getExecuteInfo"`, `method = "endDraw"`, `method = "endFrame"`.
- Purpose: write the GPU model vertices once Minecraft has uploaded its own,
  draw a GPU draw from them, and recycle their buffers with Minecraft's. The
  upload hook runs at every return: a frame whose only draws are GPU model
  draws leaves at the second, and a hook at the last return alone would leave
  them unwritten.

### StagedVertexBufferDrawMixin

- Target: `net.minecraft.client.renderer.StagedVertexBuffer.Draw`.
- mcdev 26.4 surface: fields `vertexCount` and `indexCount`; `isEmpty()` is
  true while `vertexCount` is 0.
- Source tokens: `private int vertexCount`, `private int indexCount`.
- Purpose: mark a draw as a GPU model draw and count the vertices the GPU
  writes into it, so Minecraft sizes its index buffer for them and does not
  skip the draw as empty.

### PipelineBuilderMixin

- Target: `com.mojang.blaze3d.pipeline.PipelineBuilder`.
- mcdev 26.4 surface: private `generateBackendCreateInfo(RenderPipeline,
  RenderPassLayout.Subpass, ShaderSource)` compiles each shader of the
  pipeline with
  `GlslCompiler.compileToSpv(String, String, ShaderType, ShaderDefines,
  ShaderSource, DeviceInfo)`, vertex before fragment, then reflects the
  modules and numbers their uniforms by name across the stages;
  `compilePipeline(RenderPipeline, RenderPassLayout.Subpass, ShaderSource)`
  hands the create info, which carries the subpass, to the device on the same
  thread, one of the threads Minecraft compiles pipelines on. Minecraft
  compiles a pipeline once for each subpass it is drawn in, and may compile
  them at the same time.
- Source tokens: `method = "generateBackendCreateInfo"`,
  `GlslCompiler;compileToSpv(`, `RenderPipeline pipeline`,
  `RenderPassLayout.Subpass subpass`.
- Purpose: compile the GPU model mesh stage of an entity pipeline's vertex
  shader with Minecraft's compiler, defines and includes, for the subpass
  being compiled (`ModelMeshPipelines`).

### GlslCompilerMixin

- Target: `com.mojang.blaze3d.pipeline.GlslCompiler`.
- mcdev 26.4 surface: `compileToSpv` targets Vulkan 1.2 (SPIR-V 1.5), binds
  uniforms automatically, resolves Minecraft's includes through the
  `ShaderSource`, and passes `Shaderc.shaderc_compile_into_spv` the shader
  kind as its third argument: fragment for a fragment shader, vertex for any
  other.
- Source tokens: `method = "compileToSpv"`, `Shaderc;shaderc_compile_into_spv(`,
  `index = 2`.
- Purpose: compile the mesh stage as a mesh shader.

### SPIRVModuleMixin

- Target: `com.mojang.renderpearl.frontend.shaders.SPIRVModule`.
- mcdev 26.4 surface: final field `name`; private `doReflection()`, run by
  `reflect()`, sets the SPIRV-Cross entry point with
  `Spvc.spvc_compiler_set_entry_point(long, CharSequence, int)` and the
  execution model `SpvUtil.executionModel(ShaderType)` gives, vertex or
  fragment. Its descriptors rewrite their binding and set decorations in the
  module's SPIR-V, which `close()` frees.
- Source tokens: `private String name`, `method = "doReflection"`,
  `Spvc;spvc_compiler_set_entry_point(`.
- Purpose: reflect the mesh stage with Minecraft's reflection, to give its
  uniforms Minecraft's bindings.

### VulkanRenderPipelineMeshMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline`.
- mcdev 26.4 surface: static `compile(VulkanDevice,
  CompiledRenderPipeline.CreateInfo)` sets the stages of every descriptor of
  its one set layout with two calls of
  `VkDescriptorSetLayoutBinding.stageFlags(int)`: the fragment stage (16), and
  then the vertex stage too (17) unless the descriptor is an input attachment;
  reads
  `CreateInfo.pushConstantsSize()` twice and, when it is not 0, adds one push
  constant range of that size for every stage; creates one shader module per
  stage into `shaderModules`, in the order of `CreateInfo.shaders()`; and
  builds the pipeline for the create info's subpass in the `RenderPassCache`
  render pass of the subpass's layout, with a blend state for each color
  attachment of the subpass, viewport and scissor dynamic, front faces
  clockwise, depth bias
  when either factor is not 0, and blending from private static
  `applyBlendInformation(VkPipelineColorBlendAttachmentState.Buffer,
  BlendFunction)`. `destroy()` destroys what `compile` created. Final fields
  `device` and `shaderModules`.
- Source tokens: `method = "compile"`, `VkDescriptorSetLayoutBinding;stageFlags(`,
  `CreateInfo;pushConstantsSize()`, `method = "destroy"`,
  `private LongList shaderModules`, `private static void applyBlendInformation`.
- Purpose: open to the mesh stage what an entity pipeline's layout opens to
  the vertex stage, build the GPU model mesh pipeline beside it with its
  layout, fragment module and state, and destroy the mesh pipeline with it.

### VulkanRenderPassModelMeshMixin

- Target: `com.mojang.renderpearl.backend.vulkan.VulkanRenderPass`.
- mcdev 26.4 surface: `setVertexBuffer(int, GpuBufferSlice)` binds the
  slice's Vulkan buffer at the slot; `drawIndexed(int, int, int, int, int)`
  writes and binds the pipeline's descriptors, then records
  `VK12.vkCmdDrawIndexed`; field `pipeline`, which `setPipeline` binds.
  `PreparedRenderType.draw` sets the pipeline, uniforms, vertex buffer 0 and
  index buffer before each `drawIndexed`.
- Source tokens: `VulkanRenderPipeline pipeline`, `method = "setVertexBuffer"`,
  `method = "drawIndexed"`, `VK12;vkCmdDrawIndexed(`.
- Purpose: draw a GPU model mesh draw, marked by its vertex buffer, with the
  mesh pipeline where Minecraft records its indexed draw.
