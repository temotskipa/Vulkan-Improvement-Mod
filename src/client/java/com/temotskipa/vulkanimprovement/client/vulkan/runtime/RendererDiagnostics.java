package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import com.mojang.logging.LogUtils;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementCapabilities;
import com.temotskipa.vulkanimprovement.client.vulkan.device.VulkanImprovementSupport;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.FramebufferCache;
import com.temotskipa.vulkanimprovement.client.vulkan.gpu.TransferBarriers;
import com.temotskipa.vulkanimprovement.client.vulkan.gpuworld.GpuWorldDatabase;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ItemBoundingBoxes;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ModelGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.ParticleGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.model.TextGpuVertices;
import com.temotskipa.vulkanimprovement.client.vulkan.presentation.PresentPacingController;
import com.temotskipa.vulkanimprovement.client.vulkan.rtpt.RtPtAccelerationDataRegistry;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.DescriptorHeapTerrainResources;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.FragmentShadingRateController;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshShaderTerrainProgram;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.MeshTerrainRenderer;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.SectionMeshletStore;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainPerformanceMonitor;
import com.temotskipa.vulkanimprovement.client.vulkan.terrain.TerrainRendererDebugConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class RendererDiagnostics {
	private static final Logger LOGGER = LogUtils.getLogger();
	
	public static void logBugReport() {
		LOGGER.info("[Vulkan Improvement] bug-report{}{}", System.lineSeparator(), bugReport());
	}
	
	/**
	 * What the mod runs on and with, which paths are active and why, and what each path has done.
	 */
	public static String bugReport() {
		RendererDomainObserver.observeGpuVertexPaths();
		return String.join(System.lineSeparator(), "versions: mod=" + version("vulkanimprovement") + ", minecraft=" + version("minecraft") + ", fabricLoader=" + version("fabricloader"), "flags: " + TerrainRendererDebugConfig.describe(), "videoSettings: " + VulkanVideoSettingsCompatibility.describe(), "device: " + VulkanImprovementCapabilities.describe(), "modSupport: " + VulkanImprovementSupport.describe(),"renderer: " + RendererCoreServices.get().describe(), "domains: " + RendererDomainRegistry.get().describe(), "presentPacing: " + PresentPacingController.get().describe(), "fragmentShadingRate: " + FragmentShadingRateController.get().describe(), "framebuffers: " + FramebufferCache.latest().map(FramebufferCache::describe).orElse("none"), "transferBarriers: " + TransferBarriers.latest().map(TransferBarriers::describe).orElse("none"), "terrain: " + MeshTerrainRenderer.get().describe(), "terrainProgram: " + MeshShaderTerrainProgram.get().describe(), "terrainSections: " + SectionMeshletStore.describe(), "terrainResources: " + DescriptorHeapTerrainResources.get().describe(), "performance: " + TerrainPerformanceMonitor.get().describe(), "models: " + ModelGpuVertices.get().describe(), "itemBoundingBoxes: " + ItemBoundingBoxes.describe(), "particles: " + ParticleGpuVertices.get().describe(),"text: " + TextGpuVertices.get().describe(), "gpuWorld: " + GpuWorldDatabase.get().describe(), "rtPtAccelerationData: " + RtPtAccelerationDataRegistry.get().describe(), "worldgenProvider: " + AcceleratedWorldgenProviderCompatibility.snapshot());
	}
	
	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
	}
}