package com.temotskipa.vulkanimprovement.client.vulkan.device;

import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record VulkanRequirementGroup(String id, RequirementScope scope, boolean startupRequired, String disabledReason, Set<String> deviceExtensions, Set<VulkanFeature> deviceFeatures) {
    public VulkanRequirementGroup {
        deviceExtensions = Collections.unmodifiableSet(new LinkedHashSet<>(deviceExtensions));
        deviceFeatures = Collections.unmodifiableSet(new LinkedHashSet<>(deviceFeatures));
    }
}