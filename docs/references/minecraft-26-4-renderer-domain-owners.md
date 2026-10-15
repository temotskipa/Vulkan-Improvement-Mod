# Minecraft 26.4 Renderer Domain Owners

Verified with mcdev static analysis for Minecraft `26.4-snapshot-4` on
2026-10-15.

Each renderer domain in `RendererDomainContract` names the Mojang class that
owns the vanilla path for it. This file records what mcdev shows for those
classes. No mixin targets them: the mod draws none of these domains.

Do not change an owner in `RendererDomainContract` without re-running mcdev
against the Minecraft version in `gradle.properties` and updating this file in
the same change.

| Domain             | Owner                                                                   | mcdev 26.4 surface                                                                                                                                         |
|--------------------|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|
| terrain            | `net.minecraft.client.renderer.chunk.ChunkSectionsToRender`             | abstract class with `DrawIndirect` and `DrawSeparate`; public `renderGroup` and `renderLayers`.                                                            |
| entities           | `net.minecraft.client.renderer.entity.EntityRenderDispatcher`           | 16 fields, 14 methods; `getRenderer`, `getPackedLightCoords`.                                                                                              |
| blockEntities      | `net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher` | 9 fields, 7 methods; `prepare`, `tryExtractRenderState`, `submit`.                                                                                         |
| items              | `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer`        | 73 fields, 15 methods; replaces `ItemInHandRenderer`, which 26.4 no longer has. Called from `GameRenderer.renderItemInHand`.                               |
| particles          | `net.minecraft.client.particle.ParticleEngine`                          | 8 fields, 15 methods; `createParticle`, `add`, `tick`, `extract`.                                                                                          |
| sky                | `net.minecraft.client.renderer.SkyRenderer`                             | 35 fields, 27 methods; `extractRenderState`, `prepare`, `render`.                                                                                          |
| clouds             | `net.minecraft.client.renderer.CloudRenderer`                           | 23 fields, 20 methods; `prepare`, `render`.                                                                                                                |
| weather            | `net.minecraft.client.renderer.WeatherEffectRenderer`                   | 14 fields, 12 methods; `extractRenderState`, `prepare`, `render`, `renderOit`.                                                                             |
| translucentEffects | `net.minecraft.client.renderer.LevelRenderer`                           | 40 fields, 62 methods; `render` builds the frame graph, with `executeClassicTransparency` and `executeOit` for translucent terrain and effects.            |
| postProcessing     | `net.minecraft.client.renderer.PostPass`                                | 7 fields, 3 methods; `addToFrame`, called by `PostChain.addToFrame`. `net.minecraft.client.renderer.PostChainConfig` is the record a chain is loaded from. |

The `diagnostics` domain has no Mojang owner.