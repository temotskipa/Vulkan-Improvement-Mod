package com.temotskipa.vulkanimprovement.client.vulkan.model;

import java.nio.ByteBuffer;

/**
 * A mesh stage compiled for a pipeline, its push constants starting at
 * {@code pushConstantOffset}, where Minecraft's own end.
 */
public record PendingModelMeshStage(ByteBuffer spirv, int pushConstantOffset) {
}