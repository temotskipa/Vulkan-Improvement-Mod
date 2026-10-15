package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelDrawGroup;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelVertexDraw;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Makes GPU model draws draws of Minecraft's draw group: added to its lists where their geometry
 * comes, so the group executes them in order with Minecraft's render types, and consolidated per
 * render type as Minecraft consolidates its own. Minecraft's own draws never take GPU geometry,
 * and its geometry after GPU geometry lands after it.
 */
@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group")
public abstract class RenderTypeFeatureRendererGroupMixin implements ModelDrawGroup {
    @Shadow
    @Final
    private StagedVertexBuffer stagedBuffer;
    @Shadow
    @Final
    private boolean canReorder;
    @Shadow
    @Final
    private List<StagedVertexBuffer.Draw> draws;
    @Shadow
    @Final
    private List<PreparedRenderType> drawRenderTypes;
    @Shadow
    private StagedVertexBuffer.@Nullable Draw lastDraw;
    @Unique
    private StagedVertexBuffer.@Nullable Draw vim$lastModelDraw;
    @Unique
    private @Nullable RenderType vim$lastModelRenderType;
    
    @Shadow
    public abstract VertexConsumer getVertexBuilder(RenderType renderType);
    
    /**
     * Hands out the GPU model path's builder for the submit it just took, and Minecraft's own
     * otherwise. Asking for Minecraft's ends the GPU draw that was being filled, as a change of
     * render type ends Minecraft's.
     */
    @WrapMethod(method = "getVertexBuilder")
    private VertexConsumer vim$handOutModelBuilder(RenderType renderType, Operation<VertexConsumer> original) {
        VertexConsumer gpuBuilder = ModelGpuVertices.get().takeOffer(this, renderType);
        if (gpuBuilder != null) {
            return gpuBuilder;
        }
        this.vim$lastModelDraw = null;
        return original.call(renderType);
    }
    
    /**
     * Minecraft looks for a draw of the same render type to consolidate into; GPU draws are not
     * such draws for its geometry.
     */
    @WrapOperation(method = "getOrAddDraw", at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int vim$skipModelDraws(List<?> renderTypes, Object preparedRenderType, Operation<Integer> original) {
        int index = original.call(renderTypes, preparedRenderType);
        if (index < 0 || !vim$isModelDraw(index)) {
            return index;
        }
        for (int i = index + 1; i < this.drawRenderTypes.size(); i++) {
            if (!vim$isModelDraw(i) && this.drawRenderTypes.get(i).equals(preparedRenderType)) {
                return i;
            }
        }
        return -1;
    }
    
    @Override
    public StagedVertexBuffer.Draw vim$modelDraw(RenderType renderType) {
        if (this.vim$lastModelDraw == null || this.vim$lastModelRenderType != renderType || !renderType.canConsolidateConsecutiveGeometry()) {
            this.vim$lastModelDraw = vim$getOrAddModelDraw(renderType);
            this.vim$lastModelRenderType = renderType;
        }
        // Minecraft's next geometry starts a draw after this one, or finds its own to consolidate into.
        this.lastDraw = null;
        return this.vim$lastModelDraw;
    }
    
    @Override
    public VertexConsumer vim$cpuVertexBuilder(RenderType renderType) {
        // Through the wrapper above, which hands out Minecraft's builder once the offer is taken.
        return getVertexBuilder(renderType);
    }
    
    @Unique
    private StagedVertexBuffer.Draw vim$getOrAddModelDraw(RenderType renderType) {
        PreparedRenderType preparedRenderType = renderType.prepare();
        if (this.canReorder && renderType.canConsolidateConsecutiveGeometry()) {
            for (int i = 0; i < this.draws.size(); i++) {
                if (vim$isModelDraw(i) && this.drawRenderTypes.get(i).equals(preparedRenderType)) {
                    return this.draws.get(i);
                }
            }
        }
        StagedVertexBuffer.Draw draw = this.stagedBuffer.appendDraw(renderType.format(), renderType.primitiveTopology());
        ModelGpuVertices.get().adopt(this.stagedBuffer, draw);
        this.draws.add(draw);
        this.drawRenderTypes.add(preparedRenderType);
        return draw;
    }
    
    @Unique
    private boolean vim$isModelDraw(int index) {
        return ((ModelVertexDraw.Holder) this.draws.get(index)).vim$modelDraw() != null;
    }
}
