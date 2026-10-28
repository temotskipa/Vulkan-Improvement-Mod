package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshPipelines;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelMeshShader;
import org.lwjgl.util.spvc.Spv;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reflects a GPU model mesh stage as the mesh shader it is: Minecraft's reflection looks for the
 * entry point of the module's shader type, which is only ever vertex or fragment
 * ({@link ModelMeshPipelines}).
 */
@Mixin(SPIRVModule.class)
public class SPIRVModuleMixin {
    @Shadow
    @Final
    private String name;
    
    @WrapOperation(method = "doReflection", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/spvc/Spvc;spvc_compiler_set_entry_point(JLjava/lang/CharSequence;I)I"))
    private int vim$reflectMeshStage(long compiler, CharSequence name, int model, Operation<Integer> original) {
        return original.call(compiler, name, ModelMeshShader.isMeshModule(this.name) ? Spv.SpvExecutionModelMeshEXT : model);
    }
}