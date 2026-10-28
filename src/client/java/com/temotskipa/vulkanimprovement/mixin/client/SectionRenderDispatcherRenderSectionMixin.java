package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(RenderSection.class)
public final class SectionRenderDispatcherRenderSectionMixin {
    @Shadow
    private long sectionNode;
    @Shadow
    private long uploadedTime;
    
    @Inject(method = "addSectionBuffersToUberBuffer", at = @At("HEAD"))
    private void vim$captureSectionMeshletInput(ChunkSectionLayer layer, CompiledSectionMesh key, @Nullable ByteBuffer vertexBuffer, @Nullable ByteBuffer indexBuffer, CallbackInfoReturnable<Boolean> cir) {
        if (!VulkanImprovementRuntime.isVulkanBackendActive() || !TerrainRendererDebugConfig.terrainCaptureEnabled()) {
            return;
        }
        SectionMeshletStore.capture(this.sectionNode, key, layer, vertexBuffer, indexBuffer);
    }
    
    /**
     * Minecraft shows a mesh in its section from here on. The section fades in from when it was
     * first shown, which Minecraft has just noted if this is the first time.
     */
    @Inject(method = "setSectionMesh", at = @At("RETURN"))
    private void vim$markMeshShown(SectionMesh sectionMesh, CallbackInfoReturnable<SectionMesh> cir) {
        if (VulkanImprovementRuntime.isVulkanBackendActive() && sectionMesh instanceof CompiledSectionMesh compiledSectionMesh) {
            SectionMeshletStore.meshShown(compiledSectionMesh, this.uploadedTime);
        }
    }
}