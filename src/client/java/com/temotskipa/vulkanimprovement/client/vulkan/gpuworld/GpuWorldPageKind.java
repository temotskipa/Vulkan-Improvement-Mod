package com.temotskipa.vulkanimprovement.client.vulkan.gpuworld;

public enum GpuWorldPageKind {
	CANONICAL_MIRROR("canonical-mirror", true, false), RENDER_ONLY_LOD("render-only-lod", false, false), PREDICTIVE_DISTANT("predictive-distant", false, false);
	
	private final String diagnosticName;
	private final boolean mirrorsCpuChunkState;
	private final boolean gameplayAuthoritative;
	
	GpuWorldPageKind(String diagnosticName, boolean mirrorsCpuChunkState, boolean gameplayAuthoritative) {
		this.diagnosticName = diagnosticName;
		this.mirrorsCpuChunkState = mirrorsCpuChunkState;
		this.gameplayAuthoritative = gameplayAuthoritative;
	}
	
	public String diagnosticName() {
		return this.diagnosticName;
	}
	
	boolean mirrorsCpuChunkState() {
		return this.mirrorsCpuChunkState;
	}
	
	boolean gameplayAuthoritative() {
		return this.gameplayAuthoritative;
	}
}