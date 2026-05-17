package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.opengl.GlBackend;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuDevice;
import com.temotskipa.vulkanimprovement.client.vulkan.TerrainRendererDebugConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GlBackend.class)
public final class GlBackendMixin {
    @Inject(method = "createDevice", at = @At("HEAD"))
    private void vim$rejectOpenGlFallback(long window, ShaderSource defaultShaderSource, GpuDebugOptions debugOptions, CallbackInfoReturnable<GpuDevice> cir) throws BackendCreationException {
        if (TerrainRendererDebugConfig.REQUIRE_VULKAN_BACKEND) {
            throw new BackendCreationException("Vulkan Improvement developer config vim.requireVulkanBackend=true forbids OpenGL fallback after Vulkan backend creation failed or was not selected.", BackendCreationException.Reason.OTHER, List.of("vim.requireVulkanBackend"));
        }
    }
}