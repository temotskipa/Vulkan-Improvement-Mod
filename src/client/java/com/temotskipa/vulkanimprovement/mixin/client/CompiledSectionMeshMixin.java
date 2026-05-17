package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.SectionMeshletStore;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public final class CompiledSectionMeshMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$releaseMeshletCapture(CallbackInfo ci) {
        SectionMeshletStore.release((CompiledSectionMesh) (Object) this);
    }
}