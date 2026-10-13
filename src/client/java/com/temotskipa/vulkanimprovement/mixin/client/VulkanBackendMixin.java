package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.VK14;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/**
 * The parts of device selection that a feature set cannot express: the Vulkan 1.4 device API and
 * the allocator flags that go with it. Extensions and features are added as feature sets by
 * {@link VulkanFeatureSetsMixin}.
 */
@Mixin(VulkanBackend.class)
public final class VulkanBackendMixin {
    @Unique
    private static final Logger VIM_LOGGER = LogUtils.getLogger();
    
    @Redirect(method = "createVma", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaCreateAllocator(Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;Lorg/lwjgl/PointerBuffer;)I", remap = false))
    private static int vim$createModernVmaAllocator(VmaAllocatorCreateInfo pCreateInfo, PointerBuffer pAllocator) {
        pCreateInfo.vulkanApiVersion(VK14.VK_API_VERSION_1_4);
        pCreateInfo.flags(pCreateInfo.flags() | Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT | Vma.VMA_ALLOCATOR_CREATE_KHR_MAINTENANCE5_BIT);
        return Vma.vmaCreateAllocator(pCreateInfo, pAllocator);
    }
    
    @Inject(method = "checkDeviceSuitability", at = @At("RETURN"), cancellable = true)
    private static void vim$requireVulkan14(VkPhysicalDevice vkPhysicalDevice, Set<FeatureSet> requiredFeatureSets, Set<FeatureSet> requiredIfExtensionsAvailable, CallbackInfoReturnable<BackendCreationException> cir) {
        if (cir.getReturnValue() == null && !VulkanFeatureRequirements.supportsRequiredApi(vkPhysicalDevice)) {
            VIM_LOGGER.warn("Device [{}] does not support {}", vkPhysicalDevice, VulkanFeatureRequirements.VULKAN_14_CAPABILITY);
            cir.setReturnValue(new BackendCreationException("Device missing capabilities", BackendCreationException.Reason.VULKAN_DEVICE_VERSION_TOO_LOW, List.of("VULKAN_CORE_1_4")));
        }
    }
}