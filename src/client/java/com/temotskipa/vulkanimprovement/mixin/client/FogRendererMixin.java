package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainFogState;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers the fog Minecraft uploads for the frame, so mesh terrain fades into it as Minecraft's
 * own terrain does.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    private static boolean fogEnabled;
    
    @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
    private void vim$rememberFrameFog(FogData fog, CallbackInfo ci) {
        TerrainFogState.record(fogEnabled, fog.color.x(), fog.color.y(), fog.color.z(), fog.color.w(), fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart, fog.renderDistanceEnd);
    }
}