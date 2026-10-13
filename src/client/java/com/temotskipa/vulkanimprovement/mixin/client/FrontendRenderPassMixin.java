package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.FrontendRenderPass;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderPassAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Leads from the render pass Minecraft's renderers are handed to the backend pass that records it.
 */
@Mixin(FrontendRenderPass.class)
public abstract class FrontendRenderPassMixin implements TerrainRenderPassAccess.Frontend {
    @Shadow
    @Final
    private RenderPassBackend backend;
    
    @Override
    public Object vim$backend() {
        return this.backend;
    }
}