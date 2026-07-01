package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public final class CompiledSectionMeshMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$releaseMeshletCapture(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.release((CompiledSectionMesh) (Object) this);
        }
    }
}
