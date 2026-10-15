package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import org.jspecify.annotations.Nullable;

public final class VulkanImprovementRuntime {
    private VulkanImprovementRuntime() {
    }
    
    private static volatile Backend backend = new Backend(null, false);
    
    /**
     * Whether Minecraft renders through Vulkan. Asked once per section layer and frame, so the
     * answer is kept for as long as the device is the same.
     */
    public static boolean isVulkanBackendActive() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return false;
        }
        Backend known = backend;
        if (known.device() != device) {
            known = new Backend(device, "Vulkan".equalsIgnoreCase(device.getDeviceInfo().backendName()));
            backend = known;
        }
        return known.vulkan();
    }
    
    private record Backend(@Nullable GpuDevice device, boolean vulkan) {
    }
    
    public static boolean isModRendererActive() {
        if (!isVulkanBackendActive()) {
            return false;
        }
        RendererLifecycleState state = RendererCoreServices.get().currentLifecycleState();
        return state == RendererLifecycleState.READY || state == RendererLifecycleState.FAILED;
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