package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RendererCoreServiceInvariantCheck {
    private static final Path CORE_SERVICES = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererCoreServices.java");
    private static final Path MESH_TERRAIN_RENDERER = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshTerrainRenderer.java");
    private static final Path RUNTIME = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/VulkanImprovementRuntime.java");
    private static final Path DIAGNOSTICS = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/runtime/RendererDiagnostics.java");
    private static final Path TERRAIN_RESOURCES = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/DescriptorHeapTerrainResources.java");
    
    private RendererCoreServiceInvariantCheck() {
    }
    
    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        String core = read(CORE_SERVICES);
        String terrain = read(MESH_TERRAIN_RENDERER);
        checkCoreOwnsLifecycleAndRendererWideControllers(core);
        checkTerrainRendererDelegatesSharedLifecycle(terrain);
        checkRuntimeAndDiagnosticsUseCoreServices(read(RUNTIME), read(DIAGNOSTICS));
        checkVisualDiagnosticsCaptureTextureFiltering(read(DIAGNOSTICS), read(TERRAIN_RESOURCES));
    }
    
    private static void checkCoreOwnsLifecycleAndRendererWideControllers(String source) {
        require(source.contains("private volatile RendererLifecycleState lifecycleState"), "renderer core services must own renderer lifecycle state");
        require(source.contains("PresentPacingController.get().configure(capabilities)"), "renderer core services must configure present pacing");
        require(source.contains("FragmentShadingRateController.get().configure(capabilities)"), "renderer core services must configure fragment shading rate");
        require(source.contains("RendererDomainRegistry.get().reset()"), "renderer core services must reset renderer domain state");
        require(source.contains("Map<String, Object> asMap()"), "renderer core services must expose diagnostics");
    }
    
    private static void checkTerrainRendererDelegatesSharedLifecycle(String source) {
        require(!source.contains("private volatile RendererLifecycleState lifecycleState"), "terrain renderer must not own renderer-wide lifecycle state");
        require(!source.contains("private volatile String lifecycleReason"), "terrain renderer must not own renderer-wide lifecycle reason");
        require(source.contains("RendererCoreServices.get().beginConfiguration"), "terrain renderer configuration must enter renderer core lifecycle");
        require(source.contains("RendererCoreServices.get().markReady"), "terrain renderer configuration must mark renderer core services ready");
        require(source.contains("RendererCoreServices.get().markDeviceLost"), "terrain renderer device-loss reporting must delegate to renderer core services");
    }
    
    private static void checkRuntimeAndDiagnosticsUseCoreServices(String runtimeSource, String diagnosticsSource) {
        require(runtimeSource.contains("RendererCoreServices.get().currentLifecycleState()"), "VIM runtime active-state checks must use renderer core lifecycle");
        require(diagnosticsSource.contains("report.put(\"rendererServices\", RendererCoreServices.get().asMap())"), "bug reports must expose renderer core service diagnostics separately from terrain diagnostics");
    }
    
    private static void checkVisualDiagnosticsCaptureTextureFiltering(String diagnosticsSource, String terrainResourceSource) {
        require(diagnosticsSource.contains("report.put(\"videoSettings\", VulkanVideoSettingsCompatibility.snapshot())"), "bug reports must include video settings so blurry captures can be tied to filtering and mipmap options");
        require(terrainResourceSource.contains("map.put(\"textureBindings\", textureBindingMap())"), "terrain descriptor diagnostics must expose captured terrain texture bindings");
        require(terrainResourceSource.contains("int maxAnisotropy"), "captured terrain texture bindings must record sampler anisotropy");
        require(terrainResourceSource.contains("map.put(\"maxAnisotropy\", this.maxAnisotropy)"), "terrain texture binding diagnostics must emit sampler anisotropy");
        require(terrainResourceSource.contains("map.put(\"mipLevels\", this.mipLevels)"), "terrain texture binding diagnostics must emit mip level count");
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