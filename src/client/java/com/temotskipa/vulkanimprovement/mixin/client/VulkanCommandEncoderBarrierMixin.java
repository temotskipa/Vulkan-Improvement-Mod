package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuBuffer;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.TransferAccess;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.TransferBarriers;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Holds back the barrier Minecraft records after each transfer until a later command needs it
 * ({@link TransferBarriers}). A transfer that conflicts with a held one gets a barrier before it;
 * anything else recorded into the frame's command buffer fetches it through
 * {@code commandBuffer()} first, and the held barrier is recorded there, as it is before the
 * command buffer ends.
 */
@Mixin(VulkanCommandEncoder.class)
public class VulkanCommandEncoderBarrierMixin {
    @Shadow
    private @Nullable VkCommandBuffer currentCommandBuffer;
    
    @Unique
    private final TransferBarriers vim$barriers = new TransferBarriers();
    
    /**
     * Whether a transfer is being recorded with its barrier held back.
     */
    @Unique
    private boolean vim$inTransfer;
    
    @Unique
    private static long vim$image(GpuTexture texture) {
        return ((VulkanGpuTexture) texture).vkImage();
    }
    
    @Unique
    private static long vim$buffer(GpuBuffer buffer) {
        return ((VulkanGpuBuffer) buffer).vkBuffer();
    }
    
    @Inject(method = "commandBuffer", at = @At("HEAD"))
    private void vim$releaseBeforeCommand(CallbackInfoReturnable<VkCommandBuffer> cir) {
        if (!vim$inTransfer) {
            vim$releaseBarrier();
        }
    }
    
    @Inject(method = "endCommandBuffer", at = @At("HEAD"))
    private void vim$releaseBeforeEnd(CallbackInfo ci) {
        vim$releaseBarrier();
    }
    
    @Inject(method = "memoryBarrier(Lorg/lwjgl/system/MemoryStack;)V", at = @At("HEAD"), cancellable = true)
    private void vim$holdTransferBarrier(MemoryStack stack, CallbackInfo ci) {
        if (vim$inTransfer) {
            ci.cancel();
        }
        else {
            vim$barriers.barrierRecorded();
        }
    }
    
    @WrapMethod(method = "clearColorTexture")
    private void vim$clearColorTexture(GpuTexture colorTexture, Vector4fc clearColor, Operation<Void> original) {
        vim$transfer(() -> original.call(colorTexture, clearColor), TransferAccess.writeImage(vim$image(colorTexture)));
    }
    
    @WrapMethod(method = "clearColorAndDepthTextures")
    private void vim$clearColorAndDepthTextures(GpuTexture colorTexture, Vector4fc clearColor, GpuTexture depthTexture, double clearDepth, Operation<Void> original) {
        vim$transfer(() -> original.call(colorTexture, clearColor, depthTexture, clearDepth), TransferAccess.writeImage(vim$image(colorTexture)), TransferAccess.writeImage(vim$image(depthTexture)));
    }
    
    @WrapMethod(method = "clearDepthTexture")
    private void vim$clearDepthTexture(GpuTexture depthTexture, double clearDepth, Operation<Void> original) {
        vim$transfer(() -> original.call(depthTexture, clearDepth), TransferAccess.writeImage(vim$image(depthTexture)));
    }
    
    @WrapMethod(method = "writeToBuffer")
    private void vim$writeToBuffer(GpuBufferSlice destination, ByteBuffer data, Operation<Void> original) {
        vim$transfer(() -> original.call(destination, data), TransferAccess.writeBuffer(vim$buffer(destination.buffer()), destination.offset(), destination.offset() + data.remaining()));
    }
    
    @WrapMethod(method = "copyToBuffer")
    private void vim$copyToBuffer(GpuBufferSlice source, GpuBufferSlice target, Operation<Void> original) {
        vim$transfer(() -> original.call(source, target), TransferAccess.readBuffer(vim$buffer(source.buffer()), source.offset(), source.offset() + source.length()), TransferAccess.writeBuffer(vim$buffer(target.buffer()), target.offset(), target.offset() + source.length()));
    }
    
    @WrapMethod(method = "writeToTexture")
    private void vim$writeToTexture(GpuTexture destination, ByteBuffer source, int mipLevel, int depthOrLayer, int destX, int destY, int width, int height, Operation<Void> original) {
        vim$transfer(() -> original.call(destination, source, mipLevel, depthOrLayer, destX, destY, width, height), TransferAccess.writeImage(vim$image(destination)));
    }
    
    /**
     * Takes the copy to read the source buffer from the slice's start to the buffer's end: the rows
     * it reads are laid out by the arguments, not by the slice.
     */
    @WrapMethod(method = "copyBufferToTexture")
    private void vim$copyBufferToTexture(GpuBufferSlice source, int sourceX, int sourceY, int sourceWidth, int sourceHeight, GpuTexture destination, int destinationX, int destinationY, int copyWidth, int copyHeight, int mipLevel, int arrayLayer, Operation<Void> original) {
        vim$transfer(() -> original.call(source, sourceX, sourceY, sourceWidth, sourceHeight, destination, destinationX, destinationY, copyWidth, copyHeight, mipLevel, arrayLayer), TransferAccess.readBuffer(vim$buffer(source.buffer()), source.offset(), Long.MAX_VALUE), TransferAccess.writeImage(vim$image(destination)));
    }
    
    /**
     * Takes the copy to write the buffer from the offset to its end, as the bytes it writes depend
     * on the texture's format.
     */
    @WrapMethod(method = "copyTextureToBuffer")
    private void vim$copyTextureToBuffer(GpuTexture source, GpuBuffer destination, long offset, int mipLevel, int x, int y, int width, int height, Operation<Void> original) {
        vim$transfer(() -> original.call(source, destination, offset, mipLevel, x, y, width, height), TransferAccess.readImage(vim$image(source)), TransferAccess.writeBuffer(vim$buffer(destination), offset, Long.MAX_VALUE));
    }
    
    @WrapMethod(method = "copyTextureToTexture")
    private void vim$copyTextureToTexture(GpuTexture source, GpuTexture destination, int mipLevel, int destX, int destY, int sourceX, int sourceY, int width, int height, Operation<Void> original) {
        vim$transfer(() -> original.call(source, destination, mipLevel, destX, destY, sourceX, sourceY, width, height), TransferAccess.readImage(vim$image(source)), TransferAccess.writeImage(vim$image(destination)));
    }
    
    /**
     * Records a transfer with its barrier held back, after a barrier if it conflicts with a held
     * transfer. With {@code vim.barriersByResource} off, Minecraft records it with its barrier.
     */
    @Unique
    private void vim$transfer(Runnable record, TransferAccess... accesses) {
        if (!TerrainRendererDebugConfig.barriersByResourceEnabled()) {
            record.run();
            return;
        }
        if (vim$barriers.transfer(accesses)) {
            vim$recordBarrier();
        }
        vim$inTransfer = true;
        try {
            record.run();
        } finally {
            vim$inTransfer = false;
        }
    }
    
    @Unique
    private void vim$releaseBarrier() {
        if (vim$barriers.release()) {
            vim$recordBarrier();
        }
    }
    
    /**
     * Records Minecraft's barrier into the command buffer the held transfers were recorded into,
     * which stays open until the barrier is released.
     */
    @Unique
    private void vim$recordBarrier() {
        VkCommandBuffer commandBuffer = Objects.requireNonNull(this.currentCommandBuffer, "held transfers are in the open command buffer");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanCommandEncoder.memoryBarrier(commandBuffer, stack);
        }
    }
}