package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.TextGpuVertices;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the GPU text path that glyphs it keeps have gone, on a resource reload or a change of font
 * options.
 */
@Mixin(FontManager.class)
public final class FontManagerMixin {
	@Inject(method = {"apply", "updateOptions"}, at = @At("RETURN"))
	private void vim$fontsChanged(CallbackInfo ci) {
		TextGpuVertices.get().fontsChanged();
	}
}