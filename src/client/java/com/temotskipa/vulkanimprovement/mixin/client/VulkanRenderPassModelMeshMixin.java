package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshPipelines;
import org.jspecify.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/**
 * Draws a GPU model draw with its mesh pipeline instead of the indexed draw Minecraft records for
 * it. Minecraft draws such a draw from the GPU model path's marker vertex buffer, with the draw's
 * index as its vertex offset; the descriptors it binds for the draw serve the mesh pipeline, which
 * shares the pipeline's layout ({@link ModelMeshPipelines}).
 */
@Mixin(VulkanRenderPass.class)
public abstract class VulkanRenderPassModelMeshMixin {
    @Shadow
    protected @Nullable VulkanRenderPipeline pipeline;
    
    /**
     * Whether the vertex buffer bound at slot 0 is the GPU model path's marker.
     */
    @Unique
    private boolean vim$modelMeshDraw;
    
    @Inject(method = "setVertexBuffer", at = @At("HEAD"))
    private void vim$noteModelMeshDraw(int slot, @Nullable GpuBufferSlice vertexBuffer, CallbackInfo ci) {
        if (slot == 0) {
            this.vim$modelMeshDraw = vertexBuffer != null && ModelGpuVertices.get().isMeshMarker(vertexBuffer.buffer());
        }
    }
    
    @WrapOperation(method = "drawIndexed", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK12;vkCmdDrawIndexed(Lorg/lwjgl/vulkan/VkCommandBuffer;IIIII)V"))
    private void vim$drawModelMesh(VkCommandBuffer commandBuffer, int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance, Operation<Void> original) {
        if (!this.vim$modelMeshDraw) {
            original.call(commandBuffer, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
            return;
        }
        // The marker has no vertices: a draw the mesh pipeline cannot draw is left out.
        ModelGpuVertices.get().drawMesh(commandBuffer, Objects.requireNonNull(this.pipeline, "no pipeline is bound"), vertexOffset);
    }
}