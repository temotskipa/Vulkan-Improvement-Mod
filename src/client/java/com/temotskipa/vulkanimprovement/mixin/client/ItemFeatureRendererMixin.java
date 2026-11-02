package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer.Submit;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Offers each item Minecraft prepares to the GPU model path. Minecraft has already worked out what
 * is drawn, where and how bright; when the path takes the item it records the item's quads from
 * their layout in place of writing them, and any item it does not take is left as it was.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin extends RenderTypeFeatureRenderer<Submit> {
	@WrapMethod(method = "prepareMainSubmit")
	private void vim$prepareOnGpu(Submit submit, Operation<Void> original) {
		ModelGpuVertices vertices = ModelGpuVertices.get();
		RenderType firstRenderType = vertices.offerItem(submit);
		if (firstRenderType == null) {
			original.call(submit);
			return;
		}
		// The draw group takes the offer here, which is how the path comes to hold the group.
		getVertexBuilder(firstRenderType);
		vertices.recordItem(submit);
	}
}