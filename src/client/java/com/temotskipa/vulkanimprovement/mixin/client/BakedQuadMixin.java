package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ItemQuadLayout;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelQuadResidency;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Lets the first quad of a baked item's quad list remember where the GPU model path keeps the list.
 */
@Mixin(BakedQuad.class)
public final class BakedQuadMixin implements ModelQuadResidency {
	@Unique
	private @Nullable ItemQuadLayout vim$itemLayout;
	
	@Override
	public @Nullable ItemQuadLayout vim$itemLayout() {
		return this.vim$itemLayout;
	}
	
	@Override
	public void vim$setItemLayout(ItemQuadLayout layout) {
		this.vim$itemLayout = layout;
	}
}