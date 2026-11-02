package com.temotskipa.vulkanimprovement.client.vulkan.model;

import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What other mods' mixins change in the Minecraft classes a replacement stands in for. A
 * replacement steps aside for such a change, since it cannot know what the change does.
 * <p>
 * Read from the classes as they were loaded: Mixin marks each method it merges into a class with
 * {@link MixinMerged}. An accessor or invoker only exposes what the class already has, as Fabric
 * API's on {@code ModelPart} does, so it does not count. Mixin's own listings cannot tell:
 * {@code Mixins.getConfigs()} holds only configurations it has yet to prepare, and
 * {@code Mixins.getMixinsForClass} comes back empty for a target class.
 */
final class OtherModMixins {
	private static final String OWN_MIXINS = "com.temotskipa.vulkanimprovement.";
	
	private OtherModMixins() {
	}
	
	/**
	 * The methods other mods' mixins merged into the given classes, each with its mixin. A class
	 * that cannot be found is reported too, as nothing can be said about it.
	 */
	static List<String> changing(Set<String> classNames) {
		List<String> changes = new ArrayList<>();
		for (String className : classNames) {
			try {
				for (Method method : Class.forName(className, false, OtherModMixins.class.getClassLoader()).getDeclaredMethods()) {
					MixinMerged merged = method.getAnnotation(MixinMerged.class);
					if (merged != null && !merged.mixin().startsWith(OWN_MIXINS) && !method.isAnnotationPresent(Accessor.class) && !method.isAnnotationPresent(Invoker.class)) {
						changes.add(className + "." + method.getName() + " from " + merged.mixin());
					}
				}
			} catch (ClassNotFoundException ex) {
				changes.add(className + " was not found");
			}
		}
		changes.sort(null);
		return changes;
	}
}