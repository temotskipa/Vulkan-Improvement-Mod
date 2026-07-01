package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TerrainRenderPassStateInvariantCheck {
    private static final Path RENDER_PASS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VulkanRenderPassMixin.java");
    private static final Path MESH_SHADER_PROGRAM = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshShaderTerrainProgram.java");

    private TerrainRenderPassStateInvariantCheck() {
    }

    @SuppressWarnings("unused")
    static void main(String[] args) throws IOException {
        checkRenderPassRestoresVanillaPipelineState(read(RENDER_PASS_MIXIN));
        checkMeshProgramDoesNotLeaveMeshPipelineBoundOnDescriptorFailure(read(MESH_SHADER_PROGRAM));
        checkMeshProgramUsesVanillaCameraProjection(read(MESH_SHADER_PROGRAM));
    }

    private static void checkRenderPassRestoresVanillaPipelineState(String source) {
        require(source.contains("private boolean anyDescriptorDirty;"), "render-pass mixin must shadow vanilla descriptor dirtiness");
        require(source.contains("vim$restoreVanillaPipelineState"), "render-pass mixin must restore native vanilla pipeline state after VIM mesh draws");
        require(source.contains("this.anyDescriptorDirty = true;"), "render-pass mixin must force vanilla descriptors to be pushed after VIM descriptor-buffer binding");
        require(source.contains("VK12.vkCmdBindPipeline(this.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS"),
                "render-pass mixin must rebind the native vanilla graphics pipeline");

        int meshDraw = source.indexOf("MeshTerrainRenderer.get().tryDrawMeshTerrain(context)");
        int restore = source.indexOf("vim$restoreVanillaPipelineState");
        require(meshDraw >= 0, "render-pass mixin must still call the terrain mesh replacement path");
        require(restore > meshDraw, "render-pass mixin must restore vanilla state after the mesh replacement attempt");
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

    private static String read(Path path) throws IOException {
        return Files.readString(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
