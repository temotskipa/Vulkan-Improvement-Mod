package com.temotskipa.vulkanimprovement.mixin.client;

import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.logging.LogUtils;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanFeatureRequirements;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.vulkan.VK14;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Mixin(VulkanBackend.class)
public final class VulkanBackendMixin {
    @Unique
    private static final Logger VIM_LOGGER = LogUtils.getLogger();
    @Mutable
    @Shadow
    @Final
    public static Set<String> REQUIRED_DEVICE_EXTENSIONS;
    @Mutable
    @Shadow
    @Final
    public static Set<VulkanFeature> REQUIRED_DEVICE_FEATURES;
    
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void vim$installModernVulkanRequirements(CallbackInfo ci) {
        Set<String> extensions = new LinkedHashSet<>(REQUIRED_DEVICE_EXTENSIONS);
        extensions.addAll(VulkanFeatureRequirements.requiredDeviceExtensions());
        REQUIRED_DEVICE_EXTENSIONS = Set.copyOf(extensions);
        Set<VulkanFeature> features = new LinkedHashSet<>(REQUIRED_DEVICE_FEATURES);
        features.addAll(VulkanFeatureRequirements.requiredDeviceFeatures());
        REQUIRED_DEVICE_FEATURES = Set.copyOf(features);
        VIM_LOGGER.info("[Vulkan Improvement] Installed Vulkan 1.4 mesh terrain requirements: extensions={}, features={}", REQUIRED_DEVICE_EXTENSIONS, REQUIRED_DEVICE_FEATURES);
    }
    
    @Redirect(method = "createVma", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/vma/Vma;vmaCreateAllocator(Lorg/lwjgl/util/vma/VmaAllocatorCreateInfo;Lorg/lwjgl/PointerBuffer;)I", remap = false))
    private static int vim$createModernVmaAllocator(VmaAllocatorCreateInfo pCreateInfo, PointerBuffer pAllocator) {
        pCreateInfo.vulkanApiVersion(VK14.VK_API_VERSION_1_4);
        pCreateInfo.flags(pCreateInfo.flags() | Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT | Vma.VMA_ALLOCATOR_CREATE_KHR_MAINTENANCE5_BIT);
        return Vma.vmaCreateAllocator(pCreateInfo, pAllocator);
    }
    
    @Inject(method = "isDeviceSuitable", at = @At("RETURN"), cancellable = true)
    private static void vim$requireVulkan14(VkPhysicalDevice vkPhysicalDevice, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !VulkanFeatureRequirements.supportsRequiredApi(vkPhysicalDevice)) {
            VIM_LOGGER.warn("Device [{}] does not support {}; remove Vulkan Improvement Mod to use vanilla renderer", vkPhysicalDevice, VulkanFeatureRequirements.VULKAN_14_CAPABILITY);
            cir.setReturnValue(false);
        }
    }
    
    @Inject(method = "throwForMissingRequrements", at = @At("HEAD"))
    private static void vim$throwForMissingModernVulkanCapabilities(VkPhysicalDevice vkPhysicalDevice, CallbackInfo ci) throws BackendCreationException {
        List<String> missingCapabilities = VulkanFeatureRequirements.missingRequiredCapabilities(vkPhysicalDevice);
        if (!missingCapabilities.isEmpty()) {
            throw new BackendCreationException("Vulkan Improvement Mod requires Vulkan 1.4, mesh/task shaders, " + (TerrainRendererDebugConfig.validationDescriptorBufferOnly() ? "descriptor buffer support" : "descriptor heap/buffer support") + ", fragment shading rate, and present id/wait support. Missing: " + missingCapabilities + ". Remove the mod to use vanilla renderer.", vim$reasonForMissingCapabilities(missingCapabilities), missingCapabilities);
        }
    }
    
    @Unique
    private static BackendCreationException.Reason vim$reasonForMissingCapabilities(List<String> missingCapabilities) {
        for (String missingCapability : missingCapabilities) {
            if (missingCapability.contains(VulkanFeatureRequirements.VULKAN_14_CAPABILITY)) {
                return BackendCreationException.Reason.VULKAN_DEVICE_VERSION_TOO_LOW;
            }
        }
        for (String missingCapability : missingCapabilities) {
            if (missingCapability.startsWith("VK_")) {
                return BackendCreationException.Reason.VULKAN_MISSING_EXTENSION;
            }
        }
        return BackendCreationException.Reason.VULKAN_MISSING_FEATURE;
    }
}