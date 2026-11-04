package com.temotskipa.vulkanimprovement.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementSupport;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.VK14;
import org.lwjgl.vulkan.VkDevice;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Notes whether the device Minecraft creates enables the mod's optional feature set
 * ({@link VulkanFeatureSetsMixin}), and on such a device creates Minecraft's allocator for the
 * Vulkan 1.4 API with the device addresses and maintenance 5 the mod's buffers use. Any other
 * device is created exactly as Minecraft creates it.
 */
@Mixin(VulkanBackend.class)
public final class VulkanBackendMixin {
	@Unique
	private static final Logger VIM_LOGGER = LogUtils.getLogger();

	@Inject(method = "createDevice(Lcom/mojang/renderpearl/backend/vulkan/init/FeatureSet;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;)Lorg/lwjgl/vulkan/VkDevice;", at = @At("HEAD"))
	private static void vim$noteModSupport(FeatureSet featureSet, VulkanPhysicalDevice physicalDevice, CallbackInfoReturnable<VkDevice> cir) {
		boolean enabled = featureSet.contains(VulkanFeatureRequirements.optionalFeatureSet());
		List<String> missing = enabled ? List.of() : VulkanFeatureRequirements.missingCapabilities(physicalDevice);
		VulkanImprovementSupport.deviceCreating(enabled, missing);
		if (!enabled) {
			VIM_LOGGER.warn("[Vulkan Improvement] {} lacks what the mod needs, so Minecraft draws with Vulkan without it: {}", physicalDevice.deviceName(), missing);
		}
	}

	@WrapOperation(method = "createVma", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaCreateAllocator(Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;Lorg/lwjgl/PointerBuffer;)I", remap = false))
	private static int vim$createModernVmaAllocator(VmaAllocatorCreateInfo pCreateInfo, PointerBuffer pAllocator, Operation<Integer> original) {
		if (VulkanImprovementSupport.enabled()) {
			pCreateInfo.vulkanApiVersion(VK14.VK_API_VERSION_1_4);
			pCreateInfo.flags(pCreateInfo.flags() | Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT | Vma.VMA_ALLOCATOR_CREATE_KHR_MAINTENANCE5_BIT);
		}
		return original.call(pCreateInfo, pAllocator);
	}
}
