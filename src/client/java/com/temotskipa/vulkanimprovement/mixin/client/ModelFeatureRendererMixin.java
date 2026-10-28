package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Offers each model submit to the GPU model path as Minecraft asks for the vertex builder to
 * render it into. When the path takes the submit, the draw group hands out the path's builder;
 * the model is set up and rendered by Minecraft either way.
 */
@Mixin(ModelFeatureRenderer.class)
public final class ModelFeatureRendererMixin {
    @WrapOperation(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer vim$offerToGpu(ModelFeatureRenderer renderer, RenderType renderType, Operation<VertexConsumer> original, @Local(argsOnly = true, name = "submit") ModelFeatureRenderer.Submit<?> submit) {
        ModelGpuVertices vertices = ModelGpuVertices.get();
        if (!vertices.offer(submit)) {
            return original.call(renderer, renderType);
        }
        try {
            return original.call(renderer, renderType);
        } finally {
            vertices.clearOffer();
        }
    }
}