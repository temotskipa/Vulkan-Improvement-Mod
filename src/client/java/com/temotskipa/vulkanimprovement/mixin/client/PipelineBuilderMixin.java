package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.GlslCompiler;
import com.mojang.blaze3d.pipeline.PipelineBuilder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ShaderSource;
import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.pipeline.RenderPassLayout;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshPipelines;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshShader;
import net.minecraft.client.renderer.ShaderDefines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

/**
 * Compiles the mesh stage of a pipeline that draws the entity format, with Minecraft's compiler,
 * defines and includes, right after Minecraft compiles the pipeline's vertex shader
 * ({@link ModelMeshPipelines}).
 */
@Mixin(PipelineBuilder.class)
public abstract class PipelineBuilderMixin {
    @WrapOperation(method = "generateBackendCreateInfo", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/GlslCompiler;compileToSpv(Ljava/lang/String;Ljava/lang/String;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lnet/minecraft/client/renderer/ShaderDefines;Lcom/mojang/blaze3d/pipeline/ShaderSource;Lcom/mojang/renderpearl/api/device/DeviceInfo;)Ljava/nio/ByteBuffer;"))
    private ByteBuffer vim$compileModelMeshStage(GlslCompiler compiler, String name, String source, ShaderType type, ShaderDefines shaderDefines, ShaderSource shaderSource, DeviceInfo deviceInfo, Operation<ByteBuffer> original, @Local(argsOnly = true, name = "pipeline") RenderPipeline pipeline, @Local(argsOnly = true, name = "subpass") RenderPassLayout.Subpass subpass) {
        ByteBuffer spirv = original.call(compiler, name, source, type, shaderDefines, shaderSource, deviceInfo);
        if (type == ShaderType.VERTEX && ModelMeshPipelines.wanted(pipeline)) {
            ModelMeshPipelines.compileMeshStage(pipeline, subpass, source, meshSource -> original.call(compiler, name + ModelMeshShader.MODULE_SUFFIX, meshSource, type, shaderDefines, shaderSource, deviceInfo));
        }
        return spirv;
    }
}