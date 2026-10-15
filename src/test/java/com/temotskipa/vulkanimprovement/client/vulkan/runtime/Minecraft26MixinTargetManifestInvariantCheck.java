package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Minecraft26MixinTargetManifestInvariantCheck {
    private static final Path MIXIN_CONFIG = Path.of("src/client/resources/vulkanimprovement.client.mixins.json");
    private static final Path MIXIN_SOURCE_DIR = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client");
    private static final Path MANIFEST = Path.of("docs/references/minecraft-26-4-mixin-targets.md");
    
    private static final List<Target> TARGETS = List.of(target("VulkanBackendMixin", "@Mixin(VulkanBackend.class)", "com.mojang.renderpearl.backend.vulkan.VulkanBackend", List.of("method = \"createVma\"", "vmaCreateAllocator", "method = \"checkDeviceSuitability\"")), target("VulkanFeatureSetsMixin", "@Mixin(VulkanFeatureSets.class)", "com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets", List.of("method = \"requiredFeatureSets\"")), target("VulkanInstanceMixin", "@Mixin(VulkanInstance.class)", "com.mojang.renderpearl.backend.vulkan.VulkanInstance", List.of("method = \"<init>\"", "VkApplicationInfo;apiVersion(I)")), target("VulkanDeviceMixin", "@Mixin(VulkanDevice.class)", "com.mojang.renderpearl.backend.vulkan.VulkanDevice", List.of("private VkDevice vkDevice", "method = \"<init>\"", "FeatureSet enabledFeatureSet", "VulkanPhysicalDevice;close()V", "method = \"close\"")), target("VulkanGpuSurfaceMixin", "@Mixin(VulkanGpuSurface.class)", "com.mojang.renderpearl.backend.vulkan.VulkanGpuSurface", List.of("private VulkanDevice device", "private long swapchain", "method = \"present\"", "vkQueuePresentKHR")), target("VulkanRenderPassMixin", "@Mixin(VulkanRenderPass.class)", "com.mojang.renderpearl.backend.vulkan.VulkanRenderPass", List.of("protected VulkanRenderPipeline pipeline", "private boolean anyDescriptorDirty", "private VkCommandBuffer commandBuffer()")), target("VulkanRenderPipelineMixin", "@Mixin(VulkanRenderPipeline.class)", "com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline", List.of("method = \"compile\"", "CompiledRenderPipeline.CreateInfo pipelineCreateInfo")), target("VulkanCommandEncoderMixin", "@Mixin(VulkanCommandEncoder.class)", "com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder", List.of("VulkanRenderPass currentRenderPass", "private VkCommandBuffer commandBuffer()")), target("FrontendRenderPassMixin", "@Mixin(FrontendRenderPass.class)", "com.mojang.renderpearl.frontend.FrontendRenderPass", List.of("private RenderPassBackend backend")), target("GlBackendMixin", "@Mixin(GlBackend.class)", "com.mojang.renderpearl.backend.opengl.GlBackend", List.of("method = \"createDevice\"")), target("VideoSettingsScreenMixin", "@Mixin(VideoSettingsScreen.class)", "net.minecraft.client.gui.screens.options.VideoSettingsScreen", List.of("method = \"addOptions\"", "method = \"tick\"")), target("LevelRendererMixin", "@Mixin(LevelRenderer.class)", "net.minecraft.client.renderer.LevelRenderer", List.of("private LevelRenderState levelRenderState", "private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections", "private OptionsRenderState optionsRenderState", "method = \"extractSectionDrawGroups\"", "SectionRenderDispatcher;getRenderSectionSlice(", "ObjectArrayList;iterator()", "method = \"invalidateCompiledGeometry\"", "method = \"resetLevelRenderData\"", "method = \"close\"")), target("SectionRenderDispatcherRenderSectionMixin", "@Mixin(SectionRenderDispatcher.RenderSection.class)", "net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection", List.of("private long sectionNode", "method = \"addSectionBuffersToUberBuffer\"", "private long uploadedTime", "method = \"setSectionMesh\"")), target("ChunkSectionsToRenderMixin", "@Mixin(ChunkSectionsToRender.class)", "net.minecraft.client.renderer.chunk.ChunkSectionsToRender", List.of("method = \"renderGroup\"", "ImprovedFogTextures improvedFogTextures", "boolean renderWireframeTerrain")), target("FogRendererMixin", "@Mixin(FogRenderer.class)", "net.minecraft.client.renderer.fog.FogRenderer", List.of("private static boolean fogEnabled", "method = \"updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V\"")), target("ChunkSectionsToRenderDrawIndirectMixin", "@Mixin(ChunkSectionsToRender.DrawIndirect.class)", "net.minecraft.client.renderer.chunk.ChunkSectionsToRender.DrawIndirect", List.of("method = \"render\"", "RenderPass;setPipeline(")), target("ChunkSectionsToRenderDrawSeparateMixin", "@Mixin(ChunkSectionsToRender.DrawSeparate.class)", "net.minecraft.client.renderer.chunk.ChunkSectionsToRender.DrawSeparate", List.of("method = \"render\"", "RenderPass;setPipeline(")), target("ChunkSectionInfoMixin", "@Mixin(DynamicGpuData.ChunkSectionInfo.class)", "net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo", List.of("method = \"<init>\"", "float visibility")), target("CompiledSectionMeshMixin", "@Mixin(CompiledSectionMesh.class)", "net.minecraft.client.renderer.chunk.CompiledSectionMesh", List.of("method = \"close\"")));
    
    private Minecraft26MixinTargetManifestInvariantCheck() {
    }
    
    
    static void main(String[] args) throws IOException {
        String config = read(MIXIN_CONFIG);
        require(Files.isRegularFile(MANIFEST), "missing mcdev-verified 26.4 mixin target manifest: " + MANIFEST);
        String manifest = read(MANIFEST);
        require(manifest.contains("Minecraft `" + minecraftVersion() + "`"), "manifest must identify the Minecraft version in gradle.properties as the validated target");
        require(manifest.contains("mcdev static analysis"), "manifest must record that the target surface came from mcdev static analysis");
        require(manifest.contains("Do not edit these target method names without re-running mcdev"), "manifest must preserve the mcdev revalidation rule");
        
        checkEveryConfiguredMixinIsDocumented(config);
        for (Target target : TARGETS) {
            checkMixinConfig(config, target.mixinName());
            String source = read(MIXIN_SOURCE_DIR.resolve(target.mixinName() + ".java"));
            checkSource(target, source);
            checkManifest(target, manifest);
        }
    }
    
    private static String minecraftVersion() throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(Path.of("gradle.properties"))) {
            properties.load(input);
        }
        return properties.getProperty("minecraft_version");
    }
    
    /**
     * A mixin added to the config without a manifest entry has targets nobody verified.
     */
    private static void checkEveryConfiguredMixinIsDocumented(String config) {
        Matcher mixin = Pattern.compile("\"(\\w+Mixin)\"").matcher(config);
        while (mixin.find()) {
            String name = mixin.group(1);
            require(TARGETS.stream().anyMatch(target -> target.mixinName().equals(name)), "client mixin config lists a mixin the target manifest does not cover: " + name);
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