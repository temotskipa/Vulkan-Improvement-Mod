package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

/**
 * Whether an accelerated worldgen provider is installed, and that it decides nothing yet.
 */
public record AcceleratedWorldgenProviderState(boolean present, boolean integrationEnabled, String rendererAuthority, String worldgenAuthority, String fallbackReason, String metadataRelationship, int configuredProviderCount) {
}
