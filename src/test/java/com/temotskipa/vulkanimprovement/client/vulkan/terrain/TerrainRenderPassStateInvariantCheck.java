package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TerrainRenderPassStateInvariantCheck {
    private static final Path DRAW_INDIRECT_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderDrawIndirectMixin.java");
    private static final Path DRAW_SEPARATE_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/ChunkSectionsToRenderDrawSeparateMixin.java");
    private static final Path LEVEL_RENDERER_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/LevelRendererMixin.java");
    private static final Path RENDER_PASS_MIXIN = Path.of("src/client/java/com/temotskipa/vulkanimprovement/mixin/client/VulkanRenderPassMixin.java");
    private static final Path MESH_TERRAIN_RENDERER = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshTerrainRenderer.java");
    private static final Path MESH_SHADER_PROGRAM = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/MeshShaderTerrainProgram.java");
    private static final Path DESCRIPTOR_HEAP_TERRAIN_RESOURCES = Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/DescriptorHeapTerrainResources.java");
    
    private TerrainRenderPassStateInvariantCheck() {
    }
    
    
    static void main(String[] args) throws IOException {
        checkRenderPassRestoresVanillaPipelineState(read(RENDER_PASS_MIXIN), read(MESH_TERRAIN_RENDERER));
        checkLayerHooksDrawAfterMinecraftBindsThePipeline();
        checkFramePreparationStaysOutsideTheRenderPass(read(MESH_TERRAIN_RENDERER), read(MESH_SHADER_PROGRAM), read(LEVEL_RENDERER_MIXIN));
        checkMeshPipelineIsBuiltForMinecraftsRenderPass(read(MESH_SHADER_PROGRAM));
        checkPartialMeshReplacementFallsBackToVanilla(read(MESH_TERRAIN_RENDERER));
        checkVisibleReplacementRespectsStrictMode(read(MESH_TERRAIN_RENDERER));
        checkFullLayerDiagnosticsDoNotCancelVanilla(read(MESH_TERRAIN_RENDERER));
        checkMeshProgramDoesNotLeaveMeshPipelineBoundOnDescriptorFailure(read(MESH_SHADER_PROGRAM));
        checkMeshProgramUsesVanillaCameraProjection(read(MESH_SHADER_PROGRAM));
        checkTerrainTextureBindingsExposeSamplerState(read(DESCRIPTOR_HEAP_TERRAIN_RESOURCES));
    }
    
    private static void checkRenderPassRestoresVanillaPipelineState(String renderPassMixin, String meshRenderer) {
        require(renderPassMixin.contains("private boolean anyDescriptorDirty;"), "render-pass mixin must shadow vanilla descriptor dirtiness");
        String restore = methodBody(renderPassMixin, "public void vim$restoreVanillaPipeline");
        require(restore.contains("this.anyDescriptorDirty = true;"), "render-pass mixin must force vanilla descriptors to be pushed after VIM descriptor-buffer binding");
        require(restore.contains("VK12.vkCmdBindPipeline(this.commandBuffer(), VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, this.pipeline.vkPipeline())"), "render-pass mixin must rebind the native vanilla graphics pipeline");
        
        String drawLayer = methodBody(meshRenderer, "public boolean drawTerrainLayer");
        int meshDraw = drawLayer.indexOf("tryDrawMeshTerrain(");
        int drewCheck = drawLayer.indexOf("if (drawResult.drewMeshTerrain())", meshDraw);
        int restoreCall = drawLayer.indexOf("access.vim$restoreVanillaPipeline();", meshDraw);
        int cancel = drawLayer.indexOf("return drawResult.cancelVanillaDraw();", meshDraw);
        require(meshDraw >= 0, "the layer draw must still call the terrain mesh replacement path");
        require(drewCheck > meshDraw && restoreCall > drewCheck, "the layer draw must restore vanilla state after any emitted VIM mesh draw");
        require(cancel > restoreCall, "the layer draw must decide vanilla cancellation separately after restoring VIM state");
    }
    
    /**
     * Minecraft has two terrain draw paths. Both are hooked at the same point: after the layer's
     * pipeline is bound, which the mesh pipeline is derived from, and before the layer's draws.
     */
    private static void checkLayerHooksDrawAfterMinecraftBindsThePipeline() throws IOException {
        for (Path layerMixin : List.of(DRAW_INDIRECT_MIXIN, DRAW_SEPARATE_MIXIN)) {
            String source = read(layerMixin);
            require(source.contains("method = \"render\""), layerMixin.getFileName() + " must hook the layer render method");
            require(source.contains("RenderPass;setPipeline(Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;)V") && source.contains("shift = At.Shift.AFTER"), layerMixin.getFileName() + " must draw after Minecraft has bound the layer's pipeline");
            String hook = methodBody(source, "private void vim$drawMeshTerrainLayer");
            require(hook.contains("MeshTerrainRenderer.get().drawTerrainLayer(layer, renderPass)"), layerMixin.getFileName() + " must hand the layer to the mesh renderer");
            require(hook.contains("ci.cancel();"), layerMixin.getFileName() + " must still cancel vanilla draws after full VIM replacement");
        }
    }
    
    /**
     * Minecraft hands the terrain renderers a render pass that is already open, and its command
     * encoder fails the frame when another command buffer is executed inside one. Uploads and the
     * compute steps that write mesh-task commands therefore belong to frame preparation: before
     * Minecraft collects the frame's sections, and once it has.
     */
    private static void checkFramePreparationStaysOutsideTheRenderPass(String meshRenderer, String meshProgram, String levelRendererMixin) {
        String observe = methodBody(meshRenderer, "public void observeTerrainGroup");
        require(!observe.contains("uploadDirtyTerrainData"), "terrain data must not be uploaded while Minecraft's terrain render pass is open");
        require(!observe.contains("prepareTerrainCommands"), "mesh-task commands must not be generated while Minecraft's terrain render pass is open");
        String beginFrame = methodBody(meshRenderer, "public boolean beginVisibleFrame");
        String prepareFrame = methodBody(meshRenderer, "public void prepareVisibleFrame");
        require(beginFrame.contains("uploadDirtyTerrainData()") && prepareFrame.contains("prepareTerrainCommands()"), "frame preparation must upload terrain data and generate mesh-task commands");
        // Which section layers have pages is settled before Minecraft collects the frame, and the
        // commands of both kinds of frame are generated from that.
        require(beginFrame.indexOf("uploadDirtyTerrainData()") < beginFrame.indexOf("takesOverSectionLayers(wireframe)") && beginFrame.indexOf("takesOverSectionLayers(wireframe)") < beginFrame.indexOf("prepareGpuDrivenFrame("), "terrain data must be uploaded before the frame's commands are generated from it");
        require(methodBody(levelRendererMixin, "private void vim$beginVisibleFrame").contains("MeshTerrainRenderer.get().beginVisibleFrame(this.levelRenderState.renderWireframeTerrain"), "terrain data must be uploaded before Minecraft collects the frame's sections, knowing whether they are drawn as wireframe");
        require(methodBody(levelRendererMixin, "private void vim$endVisibleFrame").contains("MeshTerrainRenderer.get().prepareVisibleFrame(this.levelRenderState.renderWireframeTerrain)"), "frame preparation must run when Minecraft has collected the frame's sections, and know whether they are drawn as wireframe");
        require(prepareFrame.contains("if (!wireframe") && prepareFrame.indexOf("if (!wireframe") < prepareFrame.indexOf("prepareTerrainCommands()"), "commands must not be prepared for wireframe terrain, which the mesh path does not draw");
        require(observe.contains("TerrainRenderContext.setReplacementAllowed(!wireframe);") && observe.contains("TerrainRenderContext.setImprovedFog(improvedFog);"), "wireframe terrain must be left to Minecraft, and a group's kind of fog must be known to its draws");
        require(levelRendererMixin.contains("method = \"extractSectionDrawGroups\""), "the frame's sections must be taken from the method both Minecraft terrain paths share");
        
        String prepareCommand = methodBody(meshProgram, "public synchronized boolean prepareVisiblePageCommands");
        int guard = prepareCommand.indexOf("TerrainRenderPassAccess.insideRenderPass(encoder)");
        int execute = prepareCommand.indexOf("encoder.execute(commandBuffer)");
        require(guard >= 0 && execute > guard, "command generation must refuse to execute a command buffer inside a render pass");
        // The GPU-driven pass records into Minecraft's open command buffer, which it gets only
        // outside a render pass.
        String prepareGpuDriven = methodBody(meshProgram, "synchronized boolean prepareGpuDrivenPages");
        require(prepareGpuDriven.contains("TerrainRenderPassAccess.frameCommandBuffer(") && prepareGpuDriven.contains("if (commandBuffer == null)") && !prepareGpuDriven.contains("encoder.execute("), "the GPU-driven pass must record into Minecraft's open command buffer, and refuse inside a render pass");
    }
    
    /**
     * Minecraft records terrain inside a render pass object, so a pipeline drawn there has to be
     * created for that pass and not for dynamic rendering.
     */
    private static void checkMeshPipelineIsBuiltForMinecraftsRenderPass(String meshProgram) {
        String createPipeline = methodBody(meshProgram, "private MeshPipeline createPipeline");
        require(createPipeline.contains("device.renderpassCache().getRenderPass("), "the mesh pipeline must be created for the render pass Minecraft's pipeline uses");
        require(createPipeline.contains(".renderPass(renderPass).subpass(0)"), "the mesh pipeline must name its render pass and subpass");
        require(!createPipeline.contains("VkPipelineRenderingCreateInfo"), "the mesh pipeline must not be created for dynamic rendering");
        require(meshProgram.contains("Map<PipelineKey, MeshPipeline> pipelineCache") && meshProgram.contains("record PipelineKey(TerrainPipelineState state, boolean alphaTest)"), "mesh pipelines must be cached by the vanilla pipeline state they stand in for and whether the layer tests alpha");
        require(meshProgram.contains("alphaTestValue.putInt(0, alphaTest ? VK10.VK_TRUE : VK10.VK_FALSE);"), "a solid layer's mesh pipeline must be specialized without the alpha test, so depth is tested before its fragments run");
        // Minecraft culls the back faces of terrain. Drawn unculled, both faces of a plant's crossed
        // planes show and their mirrored textures add up to foliage denser than Minecraft's.
        require(createPipeline.contains(".cullMode(pipelineInfo.cull() ? VK10.VK_CULL_MODE_BACK_BIT : VK10.VK_CULL_MODE_NONE).frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)"), "the mesh pipeline must cull the faces the vanilla pipeline it stands in for culls");
    }
    
    private static void checkPartialMeshReplacementFallsBackToVanilla(String source) {
        require(source.contains("public record DrawResult(boolean drewMeshTerrain, boolean cancelVanillaDraw)"), "mesh renderer must return whether it drew VIM commands separately from whether vanilla should be cancelled");
        require(source.contains("fallbackAfterPartialDraw()"), "mesh renderer must expose a partial-draw result that restores state but leaves vanilla uncancelled");
        require(methodBody(source, "private DrawResult tryDrawVisibleMeshTerrain").contains("TerrainLayerReplacementPolicy.dispatchBatches(ranges)"), "a layer's visible ranges must be split into dispatches instead of truncated");
        require(methodBody(source, "private DrawResult drawPreparedLayerDispatches").contains("return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();"), "prepared multi-dispatch failure must fall back to vanilla after restoring any emitted VIM draw");
        String fullLayerSegments = methodBody(source, "private DrawResult drawFullLayerSegments");
        require(fullLayerSegments.contains("TerrainMeshTaskDispatch.fullLayer"), "full-layer diagnostic replacement must use direct segmented mesh-task dispatches");
        require(!fullLayerSegments.contains("writeLayerWorkQueue"), "full-layer diagnostic replacement must not churn the shared terrain work queue");
        require(fullLayerSegments.contains("return drewAny ? DrawResult.fallbackAfterPartialDraw() : DrawResult.passThrough();"), "full-layer segmented failure must fall back to vanilla after restoring any emitted VIM draw");
    }
    
    /**
     * In strict replacement the mesh path takes the section layers it can draw out of Minecraft's
     * draw lists as Minecraft collects them, and Minecraft draws the rest; nothing it collected is
     * cancelled. Without strict replacement both draw.
     */
    private static void checkVisibleReplacementRespectsStrictMode(String source) {
        String takeover = methodBody(source, "public boolean takesOverSectionLayers");
        require(takeover.contains("!TerrainRendererDebugConfig.strictMeshTerrainReplacement()"), "taking section layers from Minecraft's draw lists must be gated by the strict replacement flag");
        require(takeover.contains("wireframe ||"), "wireframe terrain must stay Minecraft's to draw");
        require(takeover.contains("MeshShaderTerrainProgram.get().ready()"), "a section layer may only be taken when the mesh path can draw it");
        String visibleReplacement = methodBody(source, "private DrawResult visibleDrawResult");
        require(visibleReplacement.contains("return DrawResult.overlayOnly();"), "Minecraft's draws for a layer must run after the mesh path's: they hold only what was not taken");
        String store = readUnchecked(Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/SectionMeshletStore.java"));
        String take = methodBody(store, "public static boolean takeSectionLayer");
        require(take.contains("!VISIBLE_FRAME.takeover() || !TerrainLayerReplacementPolicy.canReplaceLayer(layer)"), "only a takeover frame takes section layers, and never translucent ones");
        require(take.contains("if (page == null || page.meshletCount() <= 0") && take.indexOf("return false;", take.indexOf("if (page == null")) < take.indexOf("VISIBLE_FRAME.take("), "a section layer without a page on the GPU must stay in Minecraft's draw lists");
        require(take.contains("page.meshletCount() > TerrainGpuLayout.MAX_PAGE_MESHLETS"), "a page with more meshlets than its task records can name must stay in Minecraft's draw lists");
        String configSource = readUnchecked(Path.of("src/client/java/com/temotskipa/vulkanimprovement/client/vulkan/terrain/TerrainRendererDebugConfig.java"));
        require(configSource.contains("strictMeshTerrainReplacement = booleanProperty(\"vim.strictMeshTerrainReplacement\", false)"), "strict mesh terrain replacement must default to vanilla fallback until visible coverage is proven");
    }
    
    private static void checkFullLayerDiagnosticsDoNotCancelVanilla(String source) {
        String layerReplacement = methodBody(source, "private DrawResult tryDrawLayerMeshTerrain");
        require(layerReplacement.contains("if (!context.drawAllCapturedTerrainLayers())"), "full-layer diagnostic mesh draws must only run when drawAllCapturedTerrainLayers is enabled");
        require(layerReplacement.indexOf("if (!context.drawAllCapturedTerrainLayers())") < layerReplacement.indexOf("drawFullLayerSegments"), "drawAllCapturedTerrainLayers must gate full-layer diagnostic mesh draws before dispatch");
        String fullLayerSegments = methodBody(source, "private DrawResult drawFullLayerSegments");
        require(fullLayerSegments.contains("return drewAny ? DrawResult.overlayOnly() : DrawResult.passThrough();"), "full-layer diagnostic mesh draws must restore state but leave vanilla terrain uncancelled");
        require(source.contains("private static final DrawResult OVERLAY_ONLY = new DrawResult(true, false);"), "draw result must expose a named overlay-only outcome for diagnostic full-layer draws");
    }
    
    private static void checkMeshProgramDoesNotLeaveMeshPipelineBoundOnDescriptorFailure(String source) {
        int drawMethod = source.indexOf("public synchronized boolean drawTerrain");
        require(drawMethod >= 0, "mesh shader program must expose drawTerrain");
        
        int descriptorBind = source.indexOf("bindTerrainTextureDescriptorBuffer(commandBuffer, meshPipeline, improvedFog)", drawMethod);
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
            }
            else if (c == '}') {
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