package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VulkanVideoOptionsInvariantCheck {
    private static final Path RUNTIME = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementRuntime.java");
    private static final Path VIDEO_OPTIONS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementVideoOptions.java");
    private static final Path VIDEO_SETTINGS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VideoSettingsScreenMixin.java");
    private static final Path TERRAIN_CONFIG = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/TerrainRendererDebugConfig.java");
    private static final Path MESH_TERRAIN_RENDERER = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshTerrainRenderer.java");
    private static final Path CHUNK_SECTIONS_TO_RENDER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderMixin.java");
    private static final Path SECTION_RENDER_DISPATCHER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/SectionRenderDispatcherMixin.java");
    private static final Path RENDER_SECTION_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/SectionRenderDispatcherRenderSectionMixin.java");
    private static final Path LEVEL_RENDERER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/LevelRendererMixin.java");
    private static final Path LANG = Path.of("src/client/resources/assets/vulkanimprovement/lang/en_us.json");

    private VulkanVideoOptionsInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        checkRuntimeKeepsOptionsVisibleForActiveVulkan(read(RUNTIME));
        checkVideoSettingsUsesRuntimeVisibilityGate(read(VIDEO_OPTIONS), read(VIDEO_SETTINGS_MIXIN));
        checkTerrainCaptureBootstrapIsExplicitOptIn(read(TERRAIN_CONFIG), read(VIDEO_OPTIONS), read(LANG));
        checkOpenGlActiveSessionsDoNotEnterTerrainServices(
                read(CHUNK_SECTIONS_TO_RENDER_MIXIN),
                read(SECTION_RENDER_DISPATCHER_MIXIN),
                read(RENDER_SECTION_MIXIN),
                read(LEVEL_RENDERER_MIXIN)
        );
        checkTerrainMirrorHotPathsRequireCaptureOptIn(
                read(MESH_TERRAIN_RENDERER),
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

    private static void checkTerrainCaptureBootstrapIsExplicitOptIn(String configSource, String videoOptionsSource, String langSource) {
        requireContains(configSource, "booleanProperty(\"vim.enableTerrainCaptureBootstrap\", false)",
                "terrain capture/bootstrap must be disabled by default");
        requireContains(configSource, "public static boolean terrainCaptureEnabled()",
                "terrain config must expose one predicate for capture/upload hot paths");
        requireContains(methodBody(configSource, "public static boolean terrainCaptureEnabled"), "replaceVanillaTerrain() || terrainCaptureBootstrap",
                "terrain capture must be enabled only by mesh replacement or explicit capture-bootstrap opt-in");
        requireContains(methodBody(configSource, "public static String rendererMode"), "\"vanilla\"",
                "renderer mode must report vanilla when mesh replacement and capture bootstrap are disabled");
        requireContains(methodBody(configSource, "public static String rendererMode"), "\"mesh-capture-bootstrap\"",
                "renderer mode must retain an explicit capture/bootstrap mode");
        requireContains(configSource, "enableTerrainCaptureBootstrap=\" + terrainCaptureBootstrapEnabled()",
                "diagnostics must report whether terrain capture/bootstrap is enabled");
        requireContains(videoOptionsSource, "terrainCaptureBootstrap",
                "Video Settings must expose the terrain capture/bootstrap opt-in flag");
        requireContains(videoOptionsSource, "TerrainRendererDebugConfig.terrainCaptureBootstrapEnabled()",
                "Video Settings must sync from the terrain capture/bootstrap config flag");
        requireContains(videoOptionsSource, "TerrainRendererDebugConfig::setTerrainCaptureBootstrapEnabled",
                "Video Settings must update the terrain capture/bootstrap config flag");
        requireContains(langSource, "\"options.vulkanimprovement.terrainCaptureBootstrap\"",
                "lang file must name the terrain capture/bootstrap option");
        requireContains(langSource, "\"options.vulkanimprovement.terrainCaptureBootstrap.tooltip\"",
                "lang file must describe the terrain capture/bootstrap option");
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

    private static void checkTerrainMirrorHotPathsRequireCaptureOptIn(
            String meshRendererSource,
            String chunkSectionsSource,
            String dispatcherSource,
            String renderSectionSource,
            String levelRendererSource
    ) {
        requireCallGuard(
                methodBody(meshRendererSource, "public void observeTerrainGroup"),
                "DescriptorHeapTerrainResources.get().uploadDirtyTerrainData",
                "terrain metadata uploads must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(chunkSectionsSource, "private void vim$observeTerrainGroup"),
                "MeshTerrainRenderer.get().observeTerrainGroup",
                "terrain render-group observation must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(dispatcherSource, "private void vim$recordTerrainDrawSlice"),
                "SectionMeshletStore.recordDrawSlice",
                "terrain draw-slice recording must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(renderSectionSource, "private void vim$captureSectionMeshletInput"),
                "SectionMeshletStore.capture",
                "terrain section capture must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$beginChunkVisibilityFrame"),
                "SectionMeshletStore.clearSectionVisibilityFrame",
                "terrain visibility resets must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForGeometryInvalidation"),
                "SectionMeshletStore.clearAll",
                "geometry invalidation cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForLevelReset"),
                "SectionMeshletStore.clearAll",
                "level reset cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
        requireCallGuard(
                methodBody(levelRendererSource, "private void vim$clearMeshletCacheForRendererClose"),
                "SectionMeshletStore.clearAll",
                "renderer-close cache clears must be skipped unless capture/bootstrap or mesh replacement is enabled",
                "TerrainRendererDebugConfig.terrainCaptureEnabled()"
        );
    }

    private static void requireCallGuard(String methodBody, String serviceCall, String message) {
        requireCallGuard(methodBody, serviceCall, message, "VulkanImprovementRuntime.isVulkanBackendActive()");
    }

    private static void requireCallGuard(String methodBody, String serviceCall, String message, String requiredGate) {
        int serviceCallIndex = methodBody.indexOf(serviceCall);
        require(serviceCallIndex >= 0, "missing guarded service call: " + serviceCall);
        int gate = methodBody.indexOf(requiredGate);
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

    private static void requireContains(String source, String token, String message) {
        require(source.contains(token), message + " (missing `" + token + "`)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
