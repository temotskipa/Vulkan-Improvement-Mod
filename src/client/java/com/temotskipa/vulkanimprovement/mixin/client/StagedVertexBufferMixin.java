package com.temotskipa.vulkanimprovement.mixin.client;

import com.temotskipa.vulkanimprovement.client.vulkan.model.*;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.StagedVertexBuffer.Draw;
import net.minecraft.client.renderer.StagedVertexBuffer.ExecuteInfo;
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
public class StagedVertexBufferMixin {
	// Every return: Minecraft returns early when it has no vertices of its own, as when every model in view is a GPU one.
	@Inject(method = "upload", at = @At("RETURN"))
	private void vim$writeModelVertices(CallbackInfo ci) {
		ModelGpuVertices.get().upload(this);
		ParticleGpuVertices.get().upload(this);
		TextGpuVertices.get().upload(this);
	}
	
	@Inject(method = "getExecuteInfo", at = @At("HEAD"), cancellable = true)
	private void vim$executeModelDraw(Draw draw, CallbackInfoReturnable<ExecuteInfo> cir) {
		ModelVertexDraw modelDraw = ((ModelVertexDrawHolder) draw).vim$modelDraw();
		if (modelDraw != null) {
			cir.setReturnValue(ModelGpuVertices.get().executeInfo(modelDraw));
			return;
		}
		ParticleVertexDraw particleDraw = ((ModelVertexDrawHolder) draw).vim$particleDraw();
		if (particleDraw != null) {
			cir.setReturnValue(ParticleGpuVertices.get().executeInfo(particleDraw));
			return;
		}
		TextVertexDraw textDraw = ((ModelVertexDrawHolder) draw).vim$textDraw();
		if (textDraw != null) {
			cir.setReturnValue(TextGpuVertices.get().executeInfo(textDraw));
		}
	}
	
	@Inject(method = "endDraw", at = @At("HEAD"))
	private void vim$forgetModelDraws(CallbackInfo ci) {
		ModelGpuVertices.get().endDraw(this);
		ParticleGpuVertices.get().endDraw(this);
		TextGpuVertices.get().endDraw(this);
	}
	
	@Inject(method = "endFrame", at = @At("HEAD"))
	private void vim$recycleModelBuffers(CallbackInfo ci) {
		ModelGpuVertices.get().endFrame(this);
		ParticleGpuVertices.get().endFrame(this);
		TextGpuVertices.get().endFrame(this);
	}
}