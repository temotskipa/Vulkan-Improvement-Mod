package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public final class SectionRenderDispatcherRenderSectionMixin {
    @Shadow
    private long sectionNode;

    @Inject(method = "addSectionBuffersToUberBuffer", at = @At("HEAD"))
    private void vim$captureSectionMeshletInput(
            ChunkSectionLayer layer,
            CompiledSectionMesh key,
            @Nullable ByteBuffer vertexBuffer,
            @Nullable ByteBuffer indexBuffer,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!VulkanImprovementRuntime.isVulkanBackendActive() || !TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        SectionMeshletStore.capture(this.sectionNode, key, layer, vertexBuffer, indexBuffer);
    }
}
