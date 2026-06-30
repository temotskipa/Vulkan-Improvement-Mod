# Minecraft 26.2 Mixin Targets

Verified with mcdev static analysis for Minecraft `26.2` on 2026-06-29.

This manifest records the Mojang classes, fields, methods, and invocation
points touched by the client mixins.

Do not edit these target method names without re-running mcdev against
Minecraft `26.2` and updating this file in the same change.

## Vulkan Backend And Device Hooks

### VulkanBackendMixin

- Target: `com.mojang.blaze3d.vulkan.VulkanBackend`.
- mcdev 26.2 surface: mutable static fields `REQUIRED_DEVICE_EXTENSIONS` and
  `REQUIRED_DEVICE_FEATURES`; private static methods `createVma`,
  `isDeviceSuitable`, and `throwForMissingRequrements`.
- Source tokens: `REQUIRED_DEVICE_EXTENSIONS`, `REQUIRED_DEVICE_FEATURES`,
  `method = "<clinit>"`, `method = "createVma"`,
  `method = "isDeviceSuitable"`, `method = "throwForMissingRequrements"`.
- Purpose: install VIM's 26.2-local Vulkan requirement groups, force the VMA
  allocator to use Vulkan 1.4-compatible flags, reject devices below the VIM
  startup floor, and emit a capability-specific backend creation reason.

### VulkanInstanceMixin

- Target: `com.mojang.blaze3d.vulkan.VulkanInstance`.
- mcdev 26.2 surface: protected constructor creates `VkApplicationInfo` with
  vanilla Vulkan 1.2 API version.
- Source tokens: `method = "<init>"`, `VkApplicationInfo;apiVersion(I)`.
- Purpose: request Vulkan 1.4 at instance creation while preserving the rest of
  vanilla instance setup.

### VulkanDeviceMixin

- Target: `com.mojang.blaze3d.vulkan.VulkanDevice`.
- mcdev 26.2 surface: constructor receives `ShaderSource`, `VulkanInstance`,
  `VulkanPhysicalDevice`, enabled device extensions, `VkDevice`, VMA handle,
  and `CheckpointExtension`; constructor closes the physical device before
  returning.
- Source tokens: `private VkDevice vkDevice`, `method = "<init>"`,
  `VulkanPhysicalDevice;close()V`, `method = "close"`.
- Purpose: capture VIM runtime capabilities before the physical-device wrapper
  is closed and shut renderer services down before the Vulkan device is
  destroyed.

### VulkanGpuSurfaceMixin

- Target: `com.mojang.blaze3d.vulkan.VulkanGpuSurface`.
- mcdev 26.2 surface: fields `device` and `swapchain`; method `present`; call
  to `KHRSwapchain.vkQueuePresentKHR`.
- Source tokens: `private VulkanDevice device`, `private long swapchain`,
  `method = "present"`, `vkQueuePresentKHR`.
- Purpose: route present-id/present-wait pacing through
  `PresentPacingController` while keeping vanilla swapchain ownership.

### VulkanRenderPassMixin

- Target: `com.mojang.blaze3d.vulkan.VulkanRenderPass`.
- mcdev 26.2 surface: fields `pipeline`, `hasDepth`, and
  `anyDescriptorDirty`; private `commandBuffer()` method; methods
  `setPipeline`, `bindTexture`, and `drawMultipleIndexed`.
- Source tokens: `protected VulkanRenderPipeline pipeline`,
  `private boolean hasDepth`, `private boolean anyDescriptorDirty`,
  `private VkCommandBuffer commandBuffer()`, `method = "setPipeline"`,
  `method = "bindTexture"`, `method = "drawMultipleIndexed"`.
- Purpose: observe terrain render passes, capture bound terrain textures, draw
  VIM mesh terrain when explicitly enabled, then restore vanilla pipeline state
  and force descriptor rebinding for fallback draws.

## OpenGL And Settings Hooks

### GlBackendMixin

- Target: `com.mojang.blaze3d.opengl.GlBackend`.
- mcdev 26.2 surface: method `createDevice`.
- Source tokens: `method = "createDevice"`.
- Purpose: when the developer-only `vim.requireVulkanBackend` flag is enabled,
  reject OpenGL fallback after Vulkan selection or creation fails.

### VideoSettingsScreenMixin

- Target: `net.minecraft.client.gui.screens.options.VideoSettingsScreen`.
- mcdev 26.2 surface: protected `addOptions` and public `tick`.
- Source tokens: `method = "addOptions"`, `method = "tick"`.
- Purpose: append VIM Video Settings controls only when the active or selected
  graphics API makes them relevant, and refresh control enabled state live.

## Terrain Capture Hooks

### LevelRendererMixin

- Target: `net.minecraft.client.renderer.LevelRenderer`.
- mcdev 26.2 surface: public `prepareChunkRenders`,
  `invalidateCompiledGeometry`, `resetLevelRenderData`, and `close`.
- Source tokens: `method = "prepareChunkRenders"`,
  `method = "invalidateCompiledGeometry"`, `method = "resetLevelRenderData"`,
  `method = "close"`.
- Purpose: reset per-frame terrain visibility and clear VIM terrain-derived
  caches for geometry invalidation, level reset, and renderer close while
  OpenGL-active sessions stay gated off.

### SectionRenderDispatcherMixin

- Target: `net.minecraft.client.renderer.chunk.SectionRenderDispatcher`.
- mcdev 26.2 surface: method `getRenderSectionSlice` returns
  `SectionRenderDispatcher.@Nullable RenderSectionBufferSlice`.
- Source tokens: `method = "getRenderSectionSlice"`,
  `RenderSectionBufferSlice`.
- Purpose: observe vanilla uber-buffer slices after upload so VIM can map
  compiled section meshes to their current GPU buffer locations.

### SectionRenderDispatcherRenderSectionMixin

- Target: `net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection`.
- mcdev 26.2 surface: volatile field `sectionNode`; private method
  `addSectionBuffersToUberBuffer`.
- Source tokens: `private long sectionNode`,
  `method = "addSectionBuffersToUberBuffer"`.
- Purpose: capture terrain vertex and index payloads at the exact point vanilla
  inserts compiled section buffers into the chunk uber-buffers.

### ChunkSectionsToRenderMixin

- Target: `net.minecraft.client.renderer.chunk.ChunkSectionsToRender`.
- mcdev 26.2 surface: record method `renderGroup`.
- Source tokens: `method = "renderGroup"`.
- Purpose: bracket vanilla terrain group rendering so VIM can observe layer
  ordering, bound samplers, and replacement eligibility.

### ChunkSectionInfoMixin

- Target: `net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo`.
- mcdev 26.2 surface: record constructor accepts `Matrix4fc modelView`,
  section coordinates, visibility, and texture-atlas dimensions.
- Source tokens: `method = "<init>"`, `Matrix4fc modelView`,
  `int textureAtlasWidth`, `int textureAtlasHeight`.
- Purpose: capture vanilla section visibility information from the dynamic
  uniform payload that vanilla terrain rendering already prepares.

### CompiledSectionMeshMixin

- Target: `net.minecraft.client.renderer.chunk.CompiledSectionMesh`.
- mcdev 26.2 surface: method `close`.
- Source tokens: `method = "close"`.
- Purpose: release VIM meshlet mappings when vanilla closes a compiled section
  mesh.
