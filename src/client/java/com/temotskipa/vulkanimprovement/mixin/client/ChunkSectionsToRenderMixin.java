package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.DescriptorHeapTerrainResources;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainPerformanceMonitor;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRenderContext;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets Minecraft's drawing of a group of terrain layers. The textures the mesh path samples
 * are the ones Minecraft is about to bind for the same layers, the sky and cloud images of
 * improved fog among them.
 */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsToRenderMixin {
    @Inject(method = "renderGroup", at = @At("HEAD"))
    private void vim$observeTerrainGroup(ChunkSectionLayerGroup group, RenderPass renderPass, GpuSampler sampler, GpuTextureView atlas, ChunkSectionsToRender.@Nullable ImprovedFogTextures improvedFogTextures, boolean renderWireframeTerrain, CallbackInfo ci) {
        if (!VulkanImprovementRuntime.isVulkanBackendActive()) {
            return;
        }
        TerrainPerformanceMonitor.get().markGroup(group, false);
        if (!TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        TerrainRenderContext.enter();
        DescriptorHeapTerrainResources resources = DescriptorHeapTerrainResources.get();
        resources.recordTextureBinding("Sampler0", atlas, sampler);
        resources.recordTextureBinding("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if (improvedFogTextures != null) {
            resources.recordImageBinding(DescriptorHeapTerrainResources.SKY_IMAGE, improvedFogTextures.skyView());
            resources.recordImageBinding(DescriptorHeapTerrainResources.CLOUDS_IMAGE, improvedFogTextures.cloudsView());
            resources.recordImageBinding(DescriptorHeapTerrainResources.CLOUDS_DEPTH_IMAGE, improvedFogTextures.cloudsDepthView());
        }
        MeshTerrainRenderer.get().observeTerrainGroup(renderWireframeTerrain, improvedFogTextures != null);
    }
    
    @Inject(method = "renderGroup", at = @At("RETURN"))
    private void vim$leaveTerrainGroup(ChunkSectionLayerGroup group, RenderPass renderPass, GpuSampler sampler, GpuTextureView atlas, ChunkSectionsToRender.@Nullable ImprovedFogTextures improvedFogTextures, boolean renderWireframeTerrain, CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            TerrainPerformanceMonitor.get().markGroup(group, true);
        }
        if (!TerrainRenderContext.isTerrainPass()) {
            return;
        }
        TerrainRenderContext.exit();
    }
}