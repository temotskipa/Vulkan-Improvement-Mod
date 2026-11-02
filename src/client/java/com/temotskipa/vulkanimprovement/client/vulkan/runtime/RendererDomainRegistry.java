package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.stream.Collectors;

public final class RendererDomainRegistry {
	private static final RendererDomainRegistry INSTANCE = new RendererDomainRegistry();
	
	private final EnumMap<RendererDomain, RendererDomainState> domains = new EnumMap<>(RendererDomain.class);
	
	private RendererDomainRegistry() {
		reset();
	}
	
	synchronized void reset() {
		this.domains.clear();
		for (RendererDomain domain : RendererDomain.values()) {
			this.domains.put(domain, RendererDomainState.vanilla("not configured"));
		}
	}
	
	public static RendererDomainRegistry get() {
		return INSTANCE;
	}
	
	synchronized void set(RendererDomain domain, RendererDomainState state) {
		this.domains.put(domain, state);
	}
	
	synchronized RendererDomainState state(RendererDomain domain) {
		return this.domains.getOrDefault(domain, RendererDomainState.vanilla("not configured"));
	}
	
	/**
	 * The path each domain draws with, and why.
	 */
	public synchronized String describe() {
		return Arrays.stream(RendererDomain.values()).map(domain -> domain.diagnosticName() + "=" + state(domain).path() + " (" + state(domain).reason() + ")").collect(Collectors.joining(", "));
	}
}