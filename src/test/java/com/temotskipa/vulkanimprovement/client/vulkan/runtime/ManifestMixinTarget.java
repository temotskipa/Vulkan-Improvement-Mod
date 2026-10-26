package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.List;

record ManifestMixinTarget(String mixinName, String sourceTargetToken, String manifestTarget, List<String> memberTokens) {
}