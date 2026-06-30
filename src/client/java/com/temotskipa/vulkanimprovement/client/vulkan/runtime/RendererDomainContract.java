package com.temotskipa.vulkanimprovement.client.vulkan.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

record RendererDomainContract(RendererDomain domain, String vanillaOwner, String assetContract, String orderingContract,
                              String materialContract, String fallbackReason, boolean replacementAllowed) {
    static RendererDomainContract defaultFor(RendererDomain domain) {
        return switch (domain) {
            case TERRAIN ->
                    new RendererDomainContract(domain, "net.minecraft.client.renderer.chunk.ChunkSectionsToRender", "vanilla compiled chunk section meshes captured from Minecraft section buffers", "vanilla ChunkSectionsToRender renderGroup layer order", "Minecraft chunk render layers mapped to terrain material records", "terrain mesh replacement validation gated", true);
            case ENTITIES ->
                    uncontracted(domain, "net.minecraft.client.renderer.entity.EntityRenderDispatcher", "vanilla entity renderer assets not mapped to GPU domain records", "vanilla entity submit and ordering rules not mapped", "entity materials, model animation, held-item, and armor layers not mapped", "vanilla entity renderer not contracted");
            case BLOCK_ENTITIES ->
                    uncontracted(domain, "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher", "vanilla block-entity renderer assets not mapped to GPU domain records", "vanilla block-entity submit and ordering rules not mapped", "block-entity dynamic model and material behavior not mapped", "vanilla block entity renderer not contracted");
            case ITEMS ->
                    uncontracted(domain, "net.minecraft.client.renderer.ItemInHandRenderer", "vanilla item model and atlas assets not mapped to GPU domain records", "vanilla item render ordering and transform rules not mapped", "item material overrides and glint/emissive behavior not mapped", "vanilla item renderer not contracted");
            case PARTICLES ->
                    uncontracted(domain, "net.minecraft.client.particle.ParticleEngine", "vanilla particle sprites and dynamic state not mapped to GPU domain records", "vanilla particle sorting and layer ordering rules not mapped", "particle material, transparency, and lighting behavior not mapped", "vanilla particle engine not contracted");
            case SKY ->
                    uncontracted(domain, "net.minecraft.client.renderer.SkyRenderer", "vanilla sky resources and celestial state not mapped to GPU domain records", "vanilla sky pass ordering rules not mapped", "sky material, fog, and weather interaction behavior not mapped", "vanilla sky renderer not contracted");
            case CLOUDS ->
                    uncontracted(domain, "net.minecraft.client.renderer.CloudRenderer", "vanilla cloud mesh and texture state not mapped to GPU domain records", "vanilla cloud pass ordering and weather interaction rules not mapped", "cloud material and transparency behavior not mapped", "vanilla cloud renderer not contracted");
            case WEATHER ->
                    uncontracted(domain, "net.minecraft.client.renderer.WeatherEffectRenderer", "vanilla weather effect resources not mapped to GPU domain records", "vanilla weather pass ordering rules not mapped", "weather material, transparency, and biome interaction behavior not mapped", "vanilla weather renderer not contracted");
            case TRANSLUCENT_EFFECTS ->
                    uncontracted(domain, "net.minecraft.client.renderer.LevelRenderer", "vanilla translucent effect resources not mapped to GPU domain records", "vanilla translucent ordering and target composition rules not mapped", "translucent effect material and blend behavior not mapped", "vanilla translucent effect ordering not contracted");
            case POST_PROCESSING ->
                    uncontracted(domain, "net.minecraft.client.renderer.PostPass", "vanilla post-processing targets and shader chain resources not mapped", "vanilla post-processing pass order and target lifetime rules not mapped", "post-processing shader inputs and material state not mapped", "vanilla post-processing chain not contracted");
            case DIAGNOSTICS ->
                    new RendererDomainContract(domain, "Minecraft client diagnostics and VIM renderer diagnostics", "diagnostic-only domain; no render assets", "diagnostic-only domain; no draw ordering", "diagnostic-only domain; no material replacement", "diagnostics do not replace vanilla rendering", false);
        };
    }
    
    private static RendererDomainContract uncontracted(RendererDomain domain, String vanillaOwner, String assetContract, String orderingContract, String materialContract, String fallbackReason) {
        return new RendererDomainContract(domain, vanillaOwner, assetContract, orderingContract, materialContract, fallbackReason, false);
    }
    
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("domain", this.domain.diagnosticName());
        map.put("vanillaOwner", this.vanillaOwner);
        map.put("assetContract", this.assetContract);
        map.put("orderingContract", this.orderingContract);
        map.put("materialContract", this.materialContract);
        map.put("fallbackReason", this.fallbackReason);
        map.put("replacementAllowed", this.replacementAllowed);
        return map;
    }
}
