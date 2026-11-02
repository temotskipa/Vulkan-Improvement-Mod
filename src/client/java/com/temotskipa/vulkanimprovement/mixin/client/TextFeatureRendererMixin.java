package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.temotskipa.vulkanimprovement.client.vulkan.model.TextGpuVertices;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Content;
import net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Offers each text to the GPU path as Minecraft reaches for what to lay out. When the path takes
 * it, Minecraft is handed an empty text and lays out nothing; any other text is left as it was.
 */
@Mixin(TextFeatureRenderer.class)
public abstract class TextFeatureRendererMixin extends RenderTypeFeatureRenderer<Submit> {
	@WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;content()Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content;"))
	private Content vim$takeOnGpu(Submit submit, Operation<Content> original, @Local(argsOnly = true, name = "context") FeatureFrameContext context) {
		TextGpuVertices text = TextGpuVertices.get();
		RenderType firstRenderType = text.offer(submit, context.font());
		if (firstRenderType == null) {
			return original.call(submit);
		}
		// The draw group takes the offer here, which is how the path comes to hold the group.
		getVertexBuilder(firstRenderType);
		return text.record(submit);
	}
}