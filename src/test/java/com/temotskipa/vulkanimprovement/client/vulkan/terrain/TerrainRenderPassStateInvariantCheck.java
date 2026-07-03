package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainRenderPassStateInvariantCheck {
    private static final Path RENDER_PASS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VulkanRenderPassMixin.java");
    private static final Path MESH_TERRAIN_RENDERER = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshTerrainRenderer.java");
    private static final Path MESH_SHADER_PROGRAM = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshShaderTerrainProgram.java");
    private static final Path DESCRIPTOR_HEAP_TERRAIN_RESOURCES = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/DescriptorHeapTerrainResources.java");

    private TerrainRenderPassStateInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        checkRenderPassRestoresVanillaPipelineState(read(RENDER_PASS_MIXIN));
        checkPartialMeshReplacementFallsBackToVanilla(read(MESH_TERRAIN_RENDERER));
        checkVisibleReplacementRespectsStrictMode(read(MESH_TERRAIN_RENDERER));
        checkFullLayerDiagnosticsDoNotCancelVanilla(read(MESH_TERRAIN_RENDERER));
        checkMeshProgramDoesNotLeaveMeshPipelineBoundOnDescriptorFailure(read(MESH_SHADER_PROGRAM));
        checkMeshProgramUsesVanillaCameraProjection(read(MESH_SHADER_PROGRAM));
        checkTerrainTextureBindingsExposeSamplerState(read(DESCRIPTOR_HEAP_TERRAIN_RESOURCES));
    }

    private static void checkRenderPassRestoresVanillaPipelineState(String source) {
        require(source.contains("private boolean anyDescriptorDirty;"), "render-pass mixin must shadow vanilla descriptor dirtiness");
        require(source.contains("vim$restoreVanillaPipelineState"), "render-pass mixin must restore native vanilla pipeline state after VIM mesh draws");
        require(source.contains("this.anyDescriptorDirty = true;"), "render-pass mixin must force vanilla descriptors to be pushed after VIM descriptor-buffer binding");
        require(source.contains("VK12.vkCmdBindPipeline(this.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS"),
                "render-pass mixin must rebind the native vanilla graphics pipeline");

        int meshDraw = source.indexOf("MeshTerrainRenderer.get().tryDrawMeshTerrain(context)");
        int restore = source.indexOf("if (drawResult.drewMeshTerrain())", meshDraw);
        int cancel = source.indexOf("if (drawResult.cancelVanillaDraw())", meshDraw);
        require(meshDraw >= 0, "render-pass mixin must still call the terrain mesh replacement path");
        require(restore > meshDraw, "render-pass mixin must restore vanilla state after any emitted VIM mesh draw");
        require(cancel > restore, "render-pass mixin must decide vanilla cancellation separately after restoring VIM state");
        require(methodBody(source, "private <T> void vim$drawMeshTerrain").contains("ci.cancel();"),
                "render-pass mixin must still cancel vanilla draws after full VIM replacement");
    }

    private static void checkPartialMeshReplacementFallsBackToVanilla(String source) {
        require(source.contains("public record DrawResult(boolean drewMeshTerrain, boolean cancelVanillaDraw)"),
                "mesh renderer must return whether it drew VIM commands separately from whether vanilla should be cancelled");
        require(source.contains("fallbackAfterPartialDraw()"),
                "mesh renderer must expose a partial-draw result that restores state but leaves vanilla uncancelled");
        require(methodBody(source, "private DrawResult drawPreparedLayerDispatches").contains("return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();"),
                "prepared multi-dispatch failure must fall back to vanilla after restoring any emitted VIM draw");
        String fullLayerSegments = methodBody(source, "private DrawResult drawFullLayerSegments");
        require(fullLayerSegments.contains("TerrainMeshTaskDispatch.fullLayer"),
                "full-layer diagnostic replacement must use direct segmented mesh-task dispatches");
        require(!fullLayerSegments.contains("writeLayerWorkQueue"),
                "full-layer diagnostic replacement must not churn the shared terrain work queue");
        require(fullLayerSegments.contains("return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();"),
                "full-layer segmented failure must fall back to vanilla after restoring any emitted VIM draw");
    }

    private static void checkVisibleReplacementRespectsStrictMode(String source) {
        String visibleReplacement = methodBody(source, "private DrawResult visibleDrawResult");
        require(visibleReplacement.contains("TerrainRendererDebugConfig.strictMeshTerrainReplacement()"),
                "visible mesh replacement cancellation must be gated by the strict replacement flag");
        require(visibleReplacement.contains("return TerrainRendererDebugConfig.strictMeshTerrainReplacement() ? DrawResult.replaced() : DrawResult.overlayOnly();"),
                "visible mesh replacement must draw overlay-only in non-strict mode so vanilla terrain remains visible");
        String configSource = readUnchecked(Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/TerrainRendererDebugConfig.java"));
        require(configSource.contains("strictMeshTerrainReplacement = booleanProperty(\"vim.strictMeshTerrainReplacement\", false)"),
                "strict mesh terrain replacement must default to vanilla fallback until visible coverage is proven");
    }

    private static void checkFullLayerDiagnosticsDoNotCancelVanilla(String source) {
        String layerReplacement = methodBody(source, "private DrawResult tryDrawLayerMeshTerrain");
        require(layerReplacement.contains("if (!context.drawAllCapturedTerrainLayers())"),
                "full-layer diagnostic mesh draws must only run when drawAllCapturedTerrainLayers is enabled");
        require(layerReplacement.indexOf("if (!context.drawAllCapturedTerrainLayers())") < layerReplacement.indexOf("drawFullLayerSegments"),
                "drawAllCapturedTerrainLayers must gate full-layer diagnostic mesh draws before dispatch");
        String fullLayerSegments = methodBody(source, "private DrawResult drawFullLayerSegments");
        require(fullLayerSegments.contains("return drewAny ? DrawResult.overlayOnly() : DrawResult.passThrough();"),
                "full-layer diagnostic mesh draws must restore state but leave vanilla terrain uncancelled");
        require(source.contains("private static final DrawResult OVERLAY_ONLY = new DrawResult(true, false);"),
                "draw result must expose a named overlay-only outcome for diagnostic full-layer draws");
    }

    private static void checkMeshProgramDoesNotLeaveMeshPipelineBoundOnDescriptorFailure(String source) {
        int drawMethod = source.indexOf("public synchronized boolean drawTerrain");
        require(drawMethod >= 0, "mesh shader program must expose drawTerrain");

        int descriptorBind = source.indexOf("bindTerrainTextureDescriptorBuffer(commandBuffer, meshPipeline)", drawMethod);
        int pipelineBind = source.indexOf("VK12.vkCmdBindPipeline(commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, meshPipeline.handle())", drawMethod);
        require(descriptorBind >= 0, "mesh shader program must bind terrain descriptors before drawing");
        require(pipelineBind >= 0, "mesh shader program must bind the mesh graphics pipeline before drawing");
        require(descriptorBind < pipelineBind, "mesh pipeline must only be bound after descriptor binding succeeds");
    }

    private static void checkMeshProgramUsesVanillaCameraProjection(String source) {
        require(source.contains("getViewRotationProjectionMatrix"), "mesh shader program must use Minecraft's camera view-rotation-projection matrix");
        require(!source.contains("camera.getFov()"), "mesh shader program must not rebuild projection from camera FOV");
        require(!source.contains("Math.tan(Math.toRadians(fovDegrees)"), "mesh shader program must not hand-roll perspective projection");
    }

    private static void checkTerrainTextureBindingsExposeSamplerState(String source) {
        require(source.contains("sampler.getMinFilter()"), "terrain texture binding diagnostics must capture min sampler filtering");
        require(source.contains("sampler.getMagFilter()"), "terrain texture binding diagnostics must capture mag sampler filtering");
        require(source.contains("sampler.getMaxLod()"), "terrain texture binding diagnostics must capture sampler max LOD");
        require(source.contains("map.put(\"minFilter\""), "terrain texture binding diagnostics must report min sampler filtering");
        require(source.contains("map.put(\"magFilter\""), "terrain texture binding diagnostics must report mag sampler filtering");
        require(source.contains("map.put(\"maxLod\""), "terrain texture binding diagnostics must report sampler max LOD");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static String readUnchecked(Path path) {
        try {
            return read(path);
        } catch (IOException ex) {
            throw new AssertionError("failed to read " + path, ex);
        }
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
