package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.BackendCreationException.Reason;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GlBackend.class)
public final class GlBackendMixin {
    @Inject(method = "createDevice", at = @At("HEAD"))
    private void vim$rejectOpenGlFallback(String applicationName, int applicationVersion, GpuDebugOptions debugOptions, CallbackInfoReturnable<GpuDevice> cir) throws BackendCreationException {
        if (TerrainRendererDebugConfig.requireVulkanBackend()) {
            throw new BackendCreationException("Vulkan Improvement developer config vim.requireVulkanBackend=true forbids OpenGL fallback after Vulkan backend creation failed or was not selected.", Reason.OTHER, List.of("vim.requireVulkanBackend"));
        }
    }
}