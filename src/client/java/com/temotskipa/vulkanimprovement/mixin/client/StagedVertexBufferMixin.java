package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelVertexDraw;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Follows Minecraft's staged vertex buffer through a frame for the GPU model path: once Minecraft
 * has uploaded its vertices the GPU writes the model draws' vertices, a model draw is drawn from
 * them, and the path forgets the draws when Minecraft does. Minecraft's own draws are untouched.
 */
@Mixin(StagedVertexBuffer.class)
public abstract class StagedVertexBufferMixin {
    // Every return: Minecraft returns early when it has no vertices of its own, as when every model in view is a GPU one.
    @Inject(method = "upload", at = @At("RETURN"))
    private void vim$writeModelVertices(CallbackInfo ci) {
        ModelGpuVertices.get().upload(this);
    }
    
    @Inject(method = "getExecuteInfo", at = @At("HEAD"), cancellable = true)
    private void vim$executeModelDraw(StagedVertexBuffer.Draw draw, CallbackInfoReturnable<StagedVertexBuffer.ExecuteInfo> cir) {
        ModelVertexDraw modelDraw = ((ModelVertexDraw.Holder) draw).vim$modelDraw();
        if (modelDraw != null) {
            cir.setReturnValue(ModelGpuVertices.get().executeInfo(modelDraw));
        }
    }
    
    @Inject(method = "endDraw", at = @At("HEAD"))
    private void vim$forgetModelDraws(CallbackInfo ci) {
        ModelGpuVertices.get().endDraw(this);
    }
    
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void vim$recycleModelBuffers(CallbackInfo ci) {
        ModelGpuVertices.get().endFrame(this);
    }
}
