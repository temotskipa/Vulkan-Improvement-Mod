package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

public final class VulkanImprovementRuntime {
    private VulkanImprovementRuntime() {
    }
    
    public static boolean isVulkanBackendActive() {
        GpuDevice device = RenderSystem.tryGetDevice();
        return device != null && "Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName());
    }
    
    public static boolean isModRendererActive() {
        if (!isVulkanBackendActive()) {
            return false;
        }
        RendererLifecycleState state = RendererCoreServices.get().currentLifecycleState();
        return state == RendererLifecycleState.READY || state == RendererLifecycleState.FAILED || state == RendererLifecycleState.DEVICE_LOST;
    }
    
    public static boolean shouldShowVideoOptions(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        PreferredGraphicsApi preferred = minecraft.options.preferredGraphicsBackend().get();
        if (isVulkanBackendActive()) {
            return true;
        }
        if (preferred == PreferredGraphicsApi.OPENGL) {
            return false;
        }
        return preferred == PreferredGraphicsApi.VULKAN;
    }
}