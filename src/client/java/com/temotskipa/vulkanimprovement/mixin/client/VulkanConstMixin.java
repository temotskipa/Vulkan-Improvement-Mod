package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.ShaderStorage;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Teaches Minecraft's Vulkan backend the buffer usage the mod adds: a buffer made with
 * {@link ShaderStorage#USAGE_SHADER_STORAGE} is a storage buffer with a device address.
 */
@Mixin(VulkanConst.class)
public final class VulkanConstMixin {
    @ModifyReturnValue(method = "bufferUsageToVk", at = @At("RETURN"))
    private static int vim$addShaderStorage(int vkUsage, @Local(argsOnly = true, name = "usage") int usage) {
        if ((usage & ShaderStorage.USAGE_SHADER_STORAGE) == 0) {
            return vkUsage;
        }
        return vkUsage | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
    }
}
