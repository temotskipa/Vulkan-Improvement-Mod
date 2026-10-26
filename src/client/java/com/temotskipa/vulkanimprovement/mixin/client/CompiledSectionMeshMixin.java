package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.runtime.VulkanImprovementRuntime;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletCaptureHolder;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompiledSectionMesh.class)
public final class CompiledSectionMeshMixin implements SectionMeshletCaptureHolder {
    @Unique
    private volatile @Nullable Object vim$capturedSection;
    @Unique
    private volatile byte vim$frameRole;
    @Unique
    private volatile long vim$shownSince;
    
    @Override
    public @Nullable Object vim$capturedSection() {
        return this.vim$capturedSection;
    }
    
    @Override
    public void vim$setCapturedSection(@Nullable Object section) {
        this.vim$capturedSection = section;
    }
    
    @Override
    public byte vim$frameRole() {
        return this.vim$frameRole;
    }
    
    @Override
    public void vim$setFrameRole(byte role) {
        this.vim$frameRole = role;
    }
    
    @Override
    public long vim$shownSince() {
        return this.vim$shownSince;
    }
    
    @Override
    public void vim$setShownSince(long millis) {
        this.vim$shownSince = millis;
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void vim$releaseMeshletCapture(CallbackInfo ci) {
        if (VulkanImprovementRuntime.isVulkanBackendActive()) {
            SectionMeshletStore.release((CompiledSectionMesh) (Object) this);
        }
    }
}