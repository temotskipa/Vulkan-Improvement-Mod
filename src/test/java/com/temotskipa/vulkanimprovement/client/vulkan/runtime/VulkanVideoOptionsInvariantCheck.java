package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VulkanVideoOptionsInvariantCheck {
    private static final Path RUNTIME = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementRuntime.java");
    private static final Path VIDEO_OPTIONS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementVideoOptions.java");
    private static final Path VIDEO_SETTINGS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VideoSettingsScreenMixin.java");
    private static final Path CHUNK_SECTIONS_TO_RENDER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderMixin.java");
    private static final Path SECTION_RENDER_DISPATCHER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/SectionRenderDispatcherMixin.java");
    private static final Path RENDER_SECTION_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/SectionRenderDispatcherRenderSectionMixin.java");
    private static final Path LEVEL_RENDERER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/LevelRendererMixin.java");

    private VulkanVideoOptionsInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        checkRuntimeKeepsOptionsVisibleForActiveVulkan(read(RUNTIME));
        checkVideoSettingsUsesRuntimeVisibilityGate(read(VIDEO_OPTIONS), read(VIDEO_SETTINGS_MIXIN));
        checkOpenGlActiveSessionsDoNotEnterTerrainServices(
                read(CHUNK_SECTIONS_TO_RENDER_MIXIN),
                read(SECTION_RENDER_DISPATCHER_MIXIN),
                read(RENDER_SECTION_MIXIN),
                read(LEVEL_RENDERER_MIXIN)
        );
    }

    private static void checkRuntimeKeepsOptionsVisibleForActiveVulkan(String source) {
        String body = methodBody(source, "public static boolean shouldShowVideoOptions");
        int activeVulkanCheck = body.indexOf("if (isVulkanBackendActive())");
        int pendingOpenGlReject = body.indexOf("if (preferred == PreferredGraphicsApi.OPENGL)");
        require(activeVulkanCheck >= 0, "video option visibility must check whether Vulkan is currently active");
        require(pendingOpenGlReject >= 0, "video option visibility must still reject pending OpenGL when Vulkan is inactive");
        require(activeVulkanCheck < pendingOpenGlReject,
                "active Vulkan must keep VIM Video Settings visible even when the pending restart preference is OpenGL");
        require(body.contains("return preferred == PreferredGraphicsApi.VULKAN;"),
                "inactive runtime must still expose VIM Video Settings when Vulkan is the selected backend");
    }

    private static void checkVideoSettingsUsesRuntimeVisibilityGate(String videoOptionsSource, String mixinSource) {
        require(videoOptionsSource.contains("VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())"),
                "VIM video option creation/update must use the central runtime visibility gate");
        require(mixinSource.contains("VulkanImprovementRuntime.shouldShowVideoOptions(Minecraft.getInstance())"),
                "Video Settings mixin must use the central runtime visibility gate");
        require(mixinSource.contains("VulkanImprovementVideoOptions.createOptions()"),
                "Video Settings mixin must append the VIM config flags through VulkanImprovementVideoOptions");
    }

    private static void checkOpenGlActiveSessionsDoNotEnterTerrainServices(
            String chunkSectionsSource,
            String dispatcherSource,
            String renderSectionSource,
            String levelRendererSource
    ) {
        requireCallGuard(
                methodBody(chunkSectionsSource, "private void vim$observeTerrainGroup"),
                "MeshTerrainRenderer.get().observeTerrainGroup",
                "terrain render-group observation must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(dispatcherSource, "private void vim$recordTerrainDrawSlice"),
                "SectionMeshletStore.recordDrawSlice",
                "terrain draw-slice recording must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(renderSectionSource, "private void vim$captureSectionMeshletInput"),
                "SectionMeshletStore.capture",
                "terrain section capture must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$beginChunkVisibilityFrame"),
                "SectionMeshletStore.clearSectionVisibilityFrame",
                "terrain visibility-frame reset must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForGeometryInvalidation"),
                "SectionMeshletStore.clearAll",
                "geometry invalidation cache clears must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForLevelReset"),
                "SectionMeshletStore.clearAll",
                "level reset cache clears must be gated off for OpenGL-active sessions"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForRendererClose"),
                "SectionMeshletStore.clearAll",
                "renderer-close cache clears must be gated off for OpenGL-active sessions"
        );
    }

    private static void requireCallGuard(String methodBody, String serviceCall, String message) {
        int serviceCallIndex = methodBody.indexOf(serviceCall);
        require(serviceCallIndex >= 0, "missing guarded service call: " + serviceCall);
        int gate = methodBody.indexOf("VulkanImprovementRuntime.isVulkanBackendActive()");
        require(gate >= 0 && gate < serviceCallIndex, message);
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        require(signatureStart >= 0, "missing method signature: " + signature);
        int bodyStart = source.indexOf('{', signatureStart);
        require(bodyStart >= 0, "missing method body: " + signature);
        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("unterminated method body: " + signature);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
