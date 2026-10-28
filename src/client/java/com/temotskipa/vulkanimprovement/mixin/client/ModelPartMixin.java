package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelPartCubes;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelPartResidency;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Hands a posed part's cubes to the GPU model path when the part is rendered into its builder.
 * Minecraft still walks the model, poses each part and decides what is drawn; only the compiling
 * of the part's cubes into vertices is taken.
 */
@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ModelPartResidency {
    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;
    @Unique
    private @Nullable ModelPartCubes vim$residentCubes;
    
    @Override
    public @Nullable ModelPartCubes vim$residentCubes() {
        return this.vim$residentCubes;
    }
    
    @Override
    public void vim$setResidentCubes(ModelPartCubes residentCubes) {
        this.vim$residentCubes = residentCubes;
    }
    
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void vim$compileOnGpu(ModelPart part, Pose pose, VertexConsumer builder, int lightCoords, int overlayCoords, int color, Operation<Void> original) {
        if (!ModelGpuVertices.get().compileOnGpu(builder, this, this.cubes, pose, lightCoords, overlayCoords, color)) {
            original.call(part, pose, builder, lightCoords, overlayCoords, color);
        }
    }
}