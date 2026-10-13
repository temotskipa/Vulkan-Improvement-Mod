package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import com.mojang.renderpearl.backend.opengl.GlBackend;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.blaze3d.pipeline.ShaderSource;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GlBackend.class)
public final class GlBackendMixin {
    @Inject(method = "createDevice", at = @At("HEAD"))
    private void vim$rejectOpenGlFallback(long window, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions, Runnable criticalShaderLoader, CallbackInfoReturnable<GpuDevice> cir) throws BackendCreationException {
        if (TerrainRendererDebugConfig.requireVulkanBackend()) {
            throw new BackendCreationException("Vulkan Improvement developer config vim.requireVulkanBackend=true forbids OpenGL fallback after Vulkan backend creation failed or was not selected.", BackendCreationException.Reason.OTHER, List.of("vim.requireVulkanBackend"));
        }
    }
}