package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

public final class TerrainRendererDebugConfig {
	public static final int MIN_SECTION_CAPACITY = 1024;
	public static final int MAX_SECTION_CAPACITY = 1_048_576;
	public static final int MIN_MESHLET_CAPACITY = 4096;
	public static final int MAX_MESHLET_CAPACITY = 8_388_608;
	public static final int MIN_VERTEX_PAYLOAD_MIB = 1;
	public static final int MAX_VERTEX_PAYLOAD_MIB = 1024;
	public static final int MIN_INDEX_PAYLOAD_MIB = 1;
	public static final int MAX_INDEX_PAYLOAD_MIB = 512;
	private static volatile boolean dumpCapabilities = booleanProperty("vim.dumpCapabilities", true);
	private static volatile boolean disablePresentPacing = booleanProperty("vim.disablePresentPacing", false);
	private static volatile int presentPacingFrameLatency = intProperty("vim.presentPacingFrameLatency", 1, 0, 3);
	private static volatile boolean requireVulkanBackend = booleanProperty("vim.requireVulkanBackend", false);
	private static volatile boolean acceleratedWorldgenProviderIntegrationEnabled = booleanProperty("vim.enableAcceleratedWorldgenProviderIntegration", false);
	private static volatile boolean validationDescriptorBufferOnly = booleanProperty("vim.validationDescriptorBufferOnly", false);
	private static volatile boolean waitIdleBeforeTerrainUpload = booleanProperty("vim.waitIdleBeforeTerrainUpload", false);
	private static volatile boolean allowCpuVisibleMeshletFallback = booleanProperty("vim.allowCpuVisibleMeshletFallback", false);
	private static volatile boolean enableGpuGeneratedMeshTaskCommands = booleanProperty("vim.enableGpuGeneratedMeshTaskCommands", true);
	private static volatile int terrainFragmentShadingRate = intProperty("vim.terrainFragmentShadingRate", 1, 1, 4);
	private static volatile int initialSectionCapacity = intProperty("vim.initialSectionCapacity", 32768, MIN_SECTION_CAPACITY, MAX_SECTION_CAPACITY);
	private static volatile int initialMeshletCapacity = intProperty("vim.initialMeshletCapacity", 524288, MIN_MESHLET_CAPACITY, MAX_MESHLET_CAPACITY);
	private static volatile int initialVertexPayloadBytes = intProperty("vim.initialVertexPayloadBytes", 512 * 1024 * 1024, MIN_VERTEX_PAYLOAD_MIB * 1024 * 1024, MAX_VERTEX_PAYLOAD_MIB * 1024 * 1024);
	private static volatile int initialIndexPayloadBytes = intProperty("vim.initialIndexPayloadBytes", 64 * 1024 * 1024, MIN_INDEX_PAYLOAD_MIB * 1024 * 1024, MAX_INDEX_PAYLOAD_MIB * 1024 * 1024);
	private static volatile boolean fragmentShadingRateEnabled = !booleanProperty("vim.disableFragmentShadingRate", false);
	private static volatile boolean replaceVanillaTerrain = booleanProperty("vim.replaceVanillaTerrain", false);
	private static volatile boolean terrainCaptureBootstrap = booleanProperty("vim.enableTerrainCaptureBootstrap", false);
	private static volatile boolean meshTranslucentTerrainEnabled = booleanProperty("vim.enableMeshTranslucentTerrain", false);
	private static volatile boolean meshletFrustumCullingEnabled = booleanProperty("vim.enableMeshletFrustumCulling", false);
	private static volatile boolean strictMeshTerrainReplacement = booleanProperty("vim.strictMeshTerrainReplacement", false);
	private static volatile boolean vanillaChunkVisibilityFadeEnabled = booleanProperty("vim.enableVanillaChunkVisibilityFade", true);
	private static volatile boolean drawAllCapturedTerrainLayers = booleanProperty("vim.drawAllCapturedTerrainLayers", false);
	private static volatile boolean terrainGpuTimer = booleanProperty("vim.terrainGpuTimer", false);
	private static volatile boolean cullMeshlets = booleanProperty("vim.cullMeshlets", true);
	private static volatile boolean taskStagePageCulling = booleanProperty("vim.taskStagePageCulling", true);
	private static volatile boolean gpuDrivenTerrain = booleanProperty("vim.gpuDrivenTerrain", true);
	private static volatile boolean gpuModelVertices = booleanProperty("vim.gpuModelVertices", true);
	private static volatile boolean keepFramebuffers = booleanProperty("vim.keepFramebuffers", true);
	private static volatile boolean barriersByResource = booleanProperty("vim.barriersByResource", true);
	private static volatile boolean gpuModelMeshShaders = booleanProperty("vim.gpuModelMeshShaders", false);
	private static volatile boolean gpuItemVertices = booleanProperty("vim.gpuItemVertices", true);
	private static volatile boolean gpuParticleVertices = booleanProperty("vim.gpuParticleVertices", true);
	private static volatile boolean gpuTextVertices = booleanProperty("vim.gpuTextVertices", true);
	
	private TerrainRendererDebugConfig() {
	}
	
	public static void setDumpCapabilities(boolean enabled) {
		dumpCapabilities = enabled;
		setBooleanProperty("vim.dumpCapabilities", enabled);
	}
	
	private static void setBooleanProperty(String name, boolean value) {
		System.setProperty(name, Boolean.toString(value));
	}
	
	public static void setDisablePresentPacing(boolean disabled) {
		disablePresentPacing = disabled;
		setBooleanProperty("vim.disablePresentPacing", disabled);
	}
	
	public static void setPresentPacingFrameLatency(int frames) {
		presentPacingFrameLatency = Math.clamp(frames, 0, 3);
		setIntProperty("vim.presentPacingFrameLatency", presentPacingFrameLatency);
	}
	
	private static void setIntProperty(String name, int value) {
		System.setProperty(name, Integer.toString(value));
	}
	
	public static void setRequireVulkanBackend(boolean required) {
		requireVulkanBackend = required;
		setBooleanProperty("vim.requireVulkanBackend", required);
	}
	
	public static void setAcceleratedWorldgenProviderIntegrationEnabled(boolean enabled) {
		acceleratedWorldgenProviderIntegrationEnabled = enabled;
		setBooleanProperty("vim.enableAcceleratedWorldgenProviderIntegration", enabled);
	}
	
	public static void setValidationDescriptorBufferOnly(boolean enabled) {
		validationDescriptorBufferOnly = enabled;
		setBooleanProperty("vim.validationDescriptorBufferOnly", enabled);
	}
	
	public static void setWaitIdleBeforeTerrainUpload(boolean enabled) {
		waitIdleBeforeTerrainUpload = enabled;
		setBooleanProperty("vim.waitIdleBeforeTerrainUpload", enabled);
	}
	
	public static void setAllowCpuVisibleMeshletFallback(boolean enabled) {
		allowCpuVisibleMeshletFallback = enabled;
		setBooleanProperty("vim.allowCpuVisibleMeshletFallback", enabled);
	}
	
	public static void setEnableGpuGeneratedMeshTaskCommands(boolean enabled) {
		enableGpuGeneratedMeshTaskCommands = enabled;
		setBooleanProperty("vim.enableGpuGeneratedMeshTaskCommands", enabled);
	}
	
	public static void setTerrainFragmentShadingRate(int rate) {
		terrainFragmentShadingRate = Math.clamp(rate, 1, 4);
		setIntProperty("vim.terrainFragmentShadingRate", terrainFragmentShadingRate);
	}
	
	public static void setInitialSectionCapacity(int capacity) {
		initialSectionCapacity = Math.clamp(capacity, MIN_SECTION_CAPACITY, MAX_SECTION_CAPACITY);
		setIntProperty("vim.initialSectionCapacity", initialSectionCapacity);
	}
	
	public static void setInitialMeshletCapacity(int capacity) {
		initialMeshletCapacity = Math.clamp(capacity, MIN_MESHLET_CAPACITY, MAX_MESHLET_CAPACITY);
		setIntProperty("vim.initialMeshletCapacity", initialMeshletCapacity);
	}
	
	public static int initialVertexPayloadMib() {
		return initialVertexPayloadBytes / (1024 * 1024);
	}
	
	public static void setInitialVertexPayloadMib(int mib) {
		int clamped = Math.clamp(mib, MIN_VERTEX_PAYLOAD_MIB, MAX_VERTEX_PAYLOAD_MIB);
		initialVertexPayloadBytes = clamped * 1024 * 1024;
		setIntProperty("vim.initialVertexPayloadBytes", initialVertexPayloadBytes);
	}
	
	public static int initialIndexPayloadMib() {
		return initialIndexPayloadBytes / (1024 * 1024);
	}
	
	public static void setInitialIndexPayloadMib(int mib) {
		int clamped = Math.clamp(mib, MIN_INDEX_PAYLOAD_MIB, MAX_INDEX_PAYLOAD_MIB);
		initialIndexPayloadBytes = clamped * 1024 * 1024;
		setIntProperty("vim.initialIndexPayloadBytes", initialIndexPayloadBytes);
	}
	
	public static void setReplaceVanillaTerrain(boolean enabled) {
		replaceVanillaTerrain = enabled;
		setBooleanProperty("vim.replaceVanillaTerrain", enabled);
	}
	
	public static void setTerrainCaptureBootstrapEnabled(boolean enabled) {
		terrainCaptureBootstrap = enabled;
		setBooleanProperty("vim.enableTerrainCaptureBootstrap", enabled);
	}
	
	public static boolean terrainCaptureEnabled() {
		return replaceVanillaTerrain() || terrainCaptureBootstrap;
	}
	
	public static boolean replaceVanillaTerrain() {
		return replaceVanillaTerrain;
	}
	
	public static void setMeshTranslucentTerrainEnabled(boolean enabled) {
		meshTranslucentTerrainEnabled = enabled;
		setBooleanProperty("vim.enableMeshTranslucentTerrain", enabled);
	}
	
	public static void setMeshletFrustumCullingEnabled(boolean enabled) {
		meshletFrustumCullingEnabled = enabled;
		setBooleanProperty("vim.enableMeshletFrustumCulling", enabled);
	}
	
	public static void setStrictMeshTerrainReplacement(boolean enabled) {
		strictMeshTerrainReplacement = enabled;
		setBooleanProperty("vim.strictMeshTerrainReplacement", enabled);
	}
	
	public static void setVanillaChunkVisibilityFadeEnabled(boolean enabled) {
		vanillaChunkVisibilityFadeEnabled = enabled;
		setBooleanProperty("vim.enableVanillaChunkVisibilityFade", enabled);
	}
	
	public static void setTerrainGpuTimerEnabled(boolean enabled) {
		terrainGpuTimer = enabled;
		setBooleanProperty("vim.terrainGpuTimer", enabled);
	}
	
	public static void setCullMeshletsEnabled(boolean enabled) {
		cullMeshlets = enabled;
		setBooleanProperty("vim.cullMeshlets", enabled);
	}
	
	public static void setTaskStagePageCullingEnabled(boolean enabled) {
		taskStagePageCulling = enabled;
		setBooleanProperty("vim.taskStagePageCulling", enabled);
	}
	
	public static void setGpuDrivenTerrainEnabled(boolean enabled) {
		gpuDrivenTerrain = enabled;
		setBooleanProperty("vim.gpuDrivenTerrain", enabled);
	}
	
	public static void setGpuModelVerticesEnabled(boolean enabled) {
		gpuModelVertices = enabled;
		setBooleanProperty("vim.gpuModelVertices", enabled);
	}
	
	public static void setKeepFramebuffersEnabled(boolean enabled) {
		keepFramebuffers = enabled;
		setBooleanProperty("vim.keepFramebuffers", enabled);
	}
	
	public static void setBarriersByResourceEnabled(boolean enabled) {
		barriersByResource = enabled;
		setBooleanProperty("vim.barriersByResource", enabled);
	}
	
	public static void setGpuItemVerticesEnabled(boolean enabled) {
		gpuItemVertices = enabled;
		setBooleanProperty("vim.gpuItemVertices", enabled);
	}
	
	public static void setGpuParticleVerticesEnabled(boolean enabled) {
		gpuParticleVertices = enabled;
		setBooleanProperty("vim.gpuParticleVertices", enabled);
	}
	
	public static void setGpuTextVerticesEnabled(boolean enabled) {
		gpuTextVertices = enabled;
		setBooleanProperty("vim.gpuTextVertices", enabled);
	}
	
	public static void setGpuModelMeshShadersEnabled(boolean enabled) {
		gpuModelMeshShaders = enabled;
		setBooleanProperty("vim.gpuModelMeshShaders", enabled);
	}
	
	public static void setDrawAllCapturedTerrainLayers(boolean enabled) {
		drawAllCapturedTerrainLayers = enabled;
		setBooleanProperty("vim.drawAllCapturedTerrainLayers", enabled);
	}
	
	public static void setFragmentShadingRateEnabled(boolean enabled) {
		fragmentShadingRateEnabled = enabled;
		setBooleanProperty("vim.disableFragmentShadingRate", !enabled);
	}
	
	public static String describe() {
		return "rendererMode=" + rendererMode() + ", requireVulkanBackend=" + requireVulkanBackend() + ", enableTerrainCaptureBootstrap=" + terrainCaptureBootstrapEnabled() + ", enableMeshTranslucentTerrain=" + meshTranslucentTerrainEnabled() + ", enableMeshletFrustumCulling=" + meshletFrustumCullingEnabled() + ", strictMeshTerrainReplacement=" + strictMeshTerrainReplacement() + ", enableVanillaChunkVisibilityFade=" + vanillaChunkVisibilityFadeEnabled() + ", drawAllCapturedTerrainLayers=" + drawAllCapturedTerrainLayers() + ", terrainGpuTimer=" + terrainGpuTimerEnabled() + ", cullMeshlets=" + cullMeshletsEnabled() + ", taskStagePageCulling=" + taskStagePageCullingEnabled() + ", gpuDrivenTerrain=" + gpuDrivenTerrainEnabled() + ", gpuModelVertices=" + gpuModelVerticesEnabled() + ", gpuModelMeshShaders=" + gpuModelMeshShadersEnabled() + ", gpuItemVertices=" + gpuItemVerticesEnabled() + ", gpuParticleVertices=" + gpuParticleVerticesEnabled() + ", gpuTextVertices=" + gpuTextVerticesEnabled() + ", keepFramebuffers=" + keepFramebuffersEnabled() + ", barriersByResource=" + barriersByResourceEnabled() + ", defaultSolidCutoutLayerMode=" + solidCutoutLayerMode() + ", defaultTranslucentLayerMode=" + translucentLayerMode() + ", dumpCapabilities=" + dumpCapabilities() + ", enableAcceleratedWorldgenProviderIntegration=" + acceleratedWorldgenProviderIntegrationEnabled() + ", disableFragmentShadingRate=" + !fragmentShadingRateEnabled() + ", disablePresentPacing=" + disablePresentPacing() + ", presentPacingFrameLatency=" + presentPacingFrameLatency() + ", validationDescriptorBufferOnly=" + validationDescriptorBufferOnly() + ", waitIdleBeforeTerrainUpload=" + waitIdleBeforeTerrainUpload() + ", allowCpuVisibleMeshletFallback=" + allowCpuVisibleMeshletFallback() + ", enableGpuGeneratedMeshTaskCommands=" + enableGpuGeneratedMeshTaskCommands() + ", terrainFragmentShadingRate=" + terrainFragmentShadingRate() + ", initialSectionCapacity=" + initialSectionCapacity() + ", initialMeshletCapacity=" + initialMeshletCapacity() + ", initialVertexPayloadBytes=" + initialVertexPayloadBytes() + ", initialIndexPayloadBytes=" + initialIndexPayloadBytes();
	}
	
	public static String rendererMode() {
		if (replaceVanillaTerrain()) {
			return strictMeshTerrainReplacement() ? "mesh-strict-replacement" : "mesh-overlay-vanilla-fallback";
		}
		return terrainCaptureBootstrapEnabled() ? "mesh-capture-bootstrap" : "vanilla";
	}
	
	public static boolean requireVulkanBackend() {
		return requireVulkanBackend;
	}
	
	public static boolean terrainCaptureBootstrapEnabled() {
		return terrainCaptureBootstrap;
	}
	
	public static boolean meshTranslucentTerrainEnabled() {
		return meshTranslucentTerrainEnabled;
	}
	
	public static boolean meshletFrustumCullingEnabled() {
		return meshletFrustumCullingEnabled;
	}
	
	public static boolean strictMeshTerrainReplacement() {
		return strictMeshTerrainReplacement;
	}
	
	public static boolean vanillaChunkVisibilityFadeEnabled() {
		return vanillaChunkVisibilityFadeEnabled;
	}
	
	public static boolean drawAllCapturedTerrainLayers() {
		return drawAllCapturedTerrainLayers;
	}
	
	/**
	 * Whether GPU timestamps bracket the mod's prepared work and each group of terrain layers, for
	 * {@link TerrainPerformanceMonitor}.
	 */
	public static boolean terrainGpuTimerEnabled() {
		return terrainGpuTimer;
	}
	
	/**
	 * Whether the task stage drops a page's meshlets that face away from the camera or are out of
	 * view, before the mesh stage reads any of their vertices.
	 */
	public static boolean cullMeshletsEnabled() {
		return cullMeshlets;
	}
	
	/**
	 * Whether each layer's listed pages go straight to the task stage, in Minecraft's order, the
	 * task stage dropping what is out of view; otherwise the GPU visibility pass culls and compacts
	 * them first.
	 */
	public static boolean taskStagePageCullingEnabled() {
		return taskStagePageCulling;
	}
	
	/**
	 * Whether, in strict replacement, the GPU draws the opaque terrain of every section in view
	 * from its own page table, told only which sections are in view; otherwise each frame lists
	 * the pages of the section layers Minecraft collects.
	 */
	public static boolean gpuDrivenTerrainEnabled() {
		return gpuDrivenTerrain;
	}
	
	/**
	 * Whether the GPU writes the vertices of entity and block-entity models, which Minecraft then
	 * draws as its own. Read as Minecraft prepares each frame's features.
	 */
	public static boolean gpuModelVerticesEnabled() {
		return gpuModelVertices;
	}
	
	public static boolean gpuModelMeshShadersEnabled() {
		return gpuModelMeshShaders;
	}
	
	public static boolean gpuItemVerticesEnabled() {
		return gpuItemVertices;
	}
	
	public static boolean gpuParticleVerticesEnabled() {
		return gpuParticleVertices;
	}
	
	public static boolean gpuTextVerticesEnabled() {
		return gpuTextVertices;
	}
	
	public static boolean keepFramebuffersEnabled() {
		return keepFramebuffers;
	}
	
	public static boolean barriersByResourceEnabled() {
		return barriersByResource;
	}
	
	private static String solidCutoutLayerMode() {
		if (replaceVanillaTerrain()) {
			return strictMeshTerrainReplacement() ? "mesh-strict-replacement" : "mesh-overlay-vanilla-fallback";
		}
		return terrainCaptureBootstrapEnabled() ? "capture-bootstrap" : "vanilla";
	}
	
	public static String translucentLayerMode() {
		return meshTranslucentTerrainEnabled() ? "metadata-capture-vanilla-visible" : "vanilla-translucent-fallback";
	}
	
	public static boolean dumpCapabilities() {
		return dumpCapabilities;
	}
	
	public static boolean acceleratedWorldgenProviderIntegrationEnabled() {
		return acceleratedWorldgenProviderIntegrationEnabled;
	}
	
	public static boolean fragmentShadingRateEnabled() {
		return fragmentShadingRateEnabled;
	}
	
	public static boolean disablePresentPacing() {
		return disablePresentPacing;
	}
	
	/**
	 * How many presented frames may wait for the screen while Minecraft renders the next one. At 0
	 * every frame waits until it is on the screen before the next one starts.
	 */
	public static int presentPacingFrameLatency() {
		return presentPacingFrameLatency;
	}
	
	public static boolean validationDescriptorBufferOnly() {
		return validationDescriptorBufferOnly;
	}
	
	public static boolean waitIdleBeforeTerrainUpload() {
		return waitIdleBeforeTerrainUpload;
	}
	
	public static boolean allowCpuVisibleMeshletFallback() {
		return allowCpuVisibleMeshletFallback;
	}
	
	public static boolean enableGpuGeneratedMeshTaskCommands() {
		return enableGpuGeneratedMeshTaskCommands;
	}
	
	public static int terrainFragmentShadingRate() {
		return terrainFragmentShadingRate;
	}
	
	public static int initialSectionCapacity() {
		return initialSectionCapacity;
	}
	
	public static int initialMeshletCapacity() {
		return initialMeshletCapacity;
	}
	
	public static int initialVertexPayloadBytes() {
		return initialVertexPayloadBytes;
	}
	
	public static int initialIndexPayloadBytes() {
		return initialIndexPayloadBytes;
	}
	
	private static boolean booleanProperty(String name, boolean fallback) {
		String value = System.getProperty(name);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}
	
	private static int intProperty(String name, int fallback, int min, int max) {
		String value = System.getProperty(name);
		if (value == null) {
			return fallback;
		}
		try {
			return Math.clamp(Integer.parseInt(value), min, max);
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}
}