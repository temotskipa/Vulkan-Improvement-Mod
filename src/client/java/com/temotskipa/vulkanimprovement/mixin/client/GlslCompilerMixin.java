package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.pipeline.GlslCompiler;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshPipelines;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Has Minecraft's compiler compile the mesh stage of a GPU model pipeline as a mesh shader: it
 * takes every shader but a fragment shader for a vertex shader ({@link ModelMeshPipelines}).
 */
@Mixin(GlslCompiler.class)
public abstract class GlslCompilerMixin {
    @ModifyArg(method = "compileToSpv", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_into_spv(JLjava/nio/ByteBuffer;ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;J)J"), index = 2)
    private int vim$compileMeshStage(int shaderKind) {
        return ModelMeshPipelines.compilingMeshStage() ? Shaderc.shaderc_mesh_shader : shaderKind;
    }
}