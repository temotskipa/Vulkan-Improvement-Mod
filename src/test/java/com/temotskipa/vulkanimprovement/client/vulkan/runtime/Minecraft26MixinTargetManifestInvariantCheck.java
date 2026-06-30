package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Minecraft26MixinTargetManifestInvariantCheck {
    private static final Path MIXIN_CONFIG = Path.of("src/client/resources/vulkanimprovement.client.mixins.json");
    private static final Path MIXIN_SOURCE_DIR = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client");
    private static final Path MANIFEST = Path.of("docs/references/minecraft-26-2-mixin-targets.md");

    private static final List<Target> TARGETS = List.of(
            target(
                    "VulkanBackendMixin",
                    "@Mixin(VulkanBackend.class)",
                    "com.mojang.blaze3d.vulkan.VulkanBackend",
                    List.of(
                            "REQUIRED_DEVICE_EXTENSIONS",
                            "REQUIRED_DEVICE_FEATURES",
                            "method = \"<clinit>\"",
                            "method = \"createVma\"",
                            "method = \"isDeviceSuitable\"",
                            "method = \"throwForMissingRequrements\""
                    )
            ),
            target(
                    "VulkanInstanceMixin",
                    "@Mixin(VulkanInstance.class)",
                    "com.mojang.blaze3d.vulkan.VulkanInstance",
                    List.of(
                            "method = \"<init>\"",
                            "VkApplicationInfo;apiVersion(I)"
                    )
            ),
            target(
                    "VulkanDeviceMixin",
                    "@Mixin(VulkanDevice.class)",
                    "com.mojang.blaze3d.vulkan.VulkanDevice",
                    List.of(
                            "private VkDevice vkDevice",
                            "method = \"<init>\"",
                            "VulkanPhysicalDevice;close()V",
                            "method = \"close\""
                    )
            ),
            target(
                    "VulkanGpuSurfaceMixin",
                    "@Mixin(VulkanGpuSurface.class)",
                    "com.mojang.blaze3d.vulkan.VulkanGpuSurface",
                    List.of(
                            "private VulkanDevice device",
                            "private long swapchain",
                            "method = \"present\"",
                            "vkQueuePresentKHR"
                    )
            ),
            target(
                    "VulkanRenderPassMixin",
                    "@Mixin(VulkanRenderPass.class)",
                    "com.mojang.blaze3d.vulkan.VulkanRenderPass",
                    List.of(
                            "protected VulkanRenderPipeline pipeline",
                            "private boolean hasDepth",
                            "private boolean anyDescriptorDirty",
                            "private VkCommandBuffer commandBuffer()",
                            "method = \"setPipeline\"",
                            "method = \"bindTexture\"",
                            "method = \"drawMultipleIndexed\""
                    )
            ),
            target(
                    "GlBackendMixin",
                    "@Mixin(GlBackend.class)",
                    "com.mojang.blaze3d.opengl.GlBackend",
                    List.of("method = \"createDevice\"")
            ),
            target(
                    "VideoSettingsScreenMixin",
                    "@Mixin(VideoSettingsScreen.class)",
                    "net.minecraft.client.gui.screens.options.VideoSettingsScreen",
                    List.of(
                            "method = \"addOptions\"",
                            "method = \"tick\""
                    )
            ),
            target(
                    "LevelRendererMixin",
                    "@Mixin(LevelRenderer.class)",
                    "net.minecraft.client.renderer.LevelRenderer",
                    List.of(
                            "method = \"prepareChunkRenders\"",
                            "method = \"invalidateCompiledGeometry\"",
                            "method = \"resetLevelRenderData\"",
                            "method = \"close\""
                    )
            ),
            target(
                    "SectionRenderDispatcherMixin",
                    "@Mixin(SectionRenderDispatcher.class)",
                    "net.minecraft.client.renderer.chunk.SectionRenderDispatcher",
                    List.of(
                            "method = \"getRenderSectionSlice\"",
                            "RenderSectionBufferSlice"
                    )
            ),
            target(
                    "SectionRenderDispatcherRenderSectionMixin",
                    "@Mixin(SectionRenderDispatcher.RenderSection.class)",
                    "net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection",
                    List.of(
                            "private long sectionNode",
                            "method = \"addSectionBuffersToUberBuffer\""
                    )
            ),
            target(
                    "ChunkSectionsToRenderMixin",
                    "@Mixin(ChunkSectionsToRender.class)",
                    "net.minecraft.client.renderer.chunk.ChunkSectionsToRender",
                    List.of("method = \"renderGroup\"")
            ),
            target(
                    "ChunkSectionInfoMixin",
                    "@Mixin(DynamicUniforms.ChunkSectionInfo.class)",
                    "net.minecraft.client.renderer.DynamicUniforms.ChunkSectionInfo",
                    List.of(
                            "method = \"<init>\"",
                            "Matrix4fc modelView",
                            "int textureAtlasWidth",
                            "int textureAtlasHeight"
                    )
            ),
            target(
                    "CompiledSectionMeshMixin",
                    "@Mixin(CompiledSectionMesh.class)",
                    "net.minecraft.client.renderer.chunk.CompiledSectionMesh",
                    List.of("method = \"close\"")
            )
    );

    private Minecraft26MixinTargetManifestInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String config = read(MIXIN_CONFIG);
        require(Files.isRegularFile(MANIFEST), "missing mcdev-verified 26.2 mixin target manifest: " + MANIFEST);
        String manifest = read(MANIFEST);
        require(manifest.contains("Minecraft `26.2`"), "manifest must identify Minecraft 26.2 as the validated target");
        require(manifest.contains("mcdev static analysis"), "manifest must record that the target surface came from mcdev static analysis");
        require(manifest.contains("Do not edit these target method names without re-running mcdev"),
                "manifest must preserve the mcdev revalidation rule");

        for (Target target : TARGETS) {
            checkMixinConfig(config, target.mixinName());
            String source = read(MIXIN_SOURCE_DIR.resolve(target.mixinName() + ".java"));
            checkSource(target, source);
            checkManifest(target, manifest);
        }
    }

    private static void checkMixinConfig(String config, String mixinName) {
        require(config.contains("\"" + mixinName + "\""), "client mixin config must list " + mixinName);
    }

    private static void checkSource(Target target, String source) {
        require(source.contains(target.sourceTargetToken()), target.mixinName() + " must target " + target.manifestTarget());
        for (String token : target.memberTokens()) {
            require(source.contains(token), target.mixinName() + " source must still touch mcdev-confirmed token: " + token);
        }
    }

    private static void checkManifest(Target target, String manifest) {
        String heading = "### " + target.mixinName();
        int start = manifest.indexOf(heading);
        require(start >= 0, "manifest must document " + target.mixinName());
        int next = manifest.indexOf("\n### ", start + heading.length());
        String section = next < 0 ? manifest.substring(start) : manifest.substring(start, next);
        require(section.contains(target.manifestTarget()), "manifest section must document target " + target.manifestTarget());
        for (String token : target.memberTokens()) {
            require(section.contains(token), target.mixinName() + " manifest section must document mcdev-confirmed token: " + token);
        }
    }

    private static Target target(String mixinName, String sourceTargetToken, String manifestTarget, List<String> memberTokens) {
        return new Target(mixinName, sourceTargetToken, manifestTarget, memberTokens);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Target(String mixinName, String sourceTargetToken, String manifestTarget, List<String> memberTokens) {
    }
}
