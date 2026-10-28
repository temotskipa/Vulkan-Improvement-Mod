package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkClearValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Puts the clear values of a render pass on the memory stack Minecraft begins the pass with, where
 * Minecraft kept them before 26.4-snapshot-6. Minecraft now takes them from the native heap and
 * never frees them, which leaks a little memory on every render pass.
 */
@Mixin(VulkanCommandEncoder.class)
public class VulkanCommandEncoderClearValuesMixin {
    @Redirect(method = "createRenderPass", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkClearValue;calloc(I)Lorg/lwjgl/vulkan/VkClearValue$Buffer;"))
    private VkClearValue.Buffer vim$clearValuesOnStack(int capacity, @Local(name = "stack") MemoryStack stack) {
        return VkClearValue.calloc(capacity, stack);
    }
}