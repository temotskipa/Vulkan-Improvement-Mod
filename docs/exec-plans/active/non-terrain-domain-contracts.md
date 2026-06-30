# Non-Terrain Domain Contracts

## Goal

Define explicit compatibility contracts for non-terrain renderer domains before
any entity, block-entity, item, particle, sky, cloud, weather, translucent
effect, or post-processing path is replaced by VIM GPU rendering.

## Constraints

- CPU-authoritative Minecraft render state remains the source of truth.
- Non-terrain domains must stay on vanilla paths until their assets, ordering,
  animation, material, and mod-compatibility requirements are represented.
- Diagnostics must distinguish observed vanilla paths from selected VIM GPU
  paths.
- Use mcdev before adding mixins or depending on Mojang method descriptors.
- OpenGL-active sessions must not initialize VIM renderer services.

## Steps

1. Keep the domain list plan-aligned.
   - Source: `RendererDomain.java`.
   - Invariant: `RendererDomainInvariantCheck.java`.
   - Required diagnostic keys: `terrain`, `entities`, `blockEntities`, `items`,
     `particles`, `sky`, `clouds`, `weather`, `translucentEffects`,
     `postProcessing`, and `diagnostics`.
   - Run: `.\gradlew.bat checkRendererDomainInvariants --console=plain`.
   - Status: done for the current registry surface.

2. Add a domain contract record.
   - Create `RendererDomainContract.java` with fields:
     `RendererDomain domain`, `String vanillaOwner`, `String assetContract`,
     `String orderingContract`, `String materialContract`,
     `String fallbackReason`, and `boolean replacementAllowed`.
   - Add `asMap()` so `RendererDomainRegistry.asMap()` can expose each
     contract beside the current domain state.
   - Status: done for default domain contracts.

3. Add failing invariants for contract defaults.
   - Extend `RendererDomainInvariantCheck`.
   - Assert every non-terrain domain defaults to `replacementAllowed=false`.
   - Assert fallback reasons are concrete:
     `vanilla entity renderer not contracted`,
     `vanilla block entity renderer not contracted`,
     `vanilla item renderer not contracted`,
     `vanilla particle engine not contracted`,
     `vanilla sky renderer not contracted`,
     `vanilla cloud renderer not contracted`,
     `vanilla weather renderer not contracted`,
     `vanilla translucent effect ordering not contracted`,
     `vanilla post-processing chain not contracted`.
   - Status: done for default non-terrain replacement gates.

4. Populate contract owners from mcdev-confirmed 26.2 classes.
   - Inspect and record summaries for:
     `net.minecraft.client.renderer.entity.EntityRenderDispatcher`,
     `net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher`,
     `net.minecraft.client.renderer.ItemInHandRenderer`,
     `net.minecraft.client.particle.ParticleEngine`,
     `net.minecraft.client.renderer.SkyRenderer`,
     `net.minecraft.client.renderer.CloudRenderer`,
     `net.minecraft.client.renderer.WeatherEffectRenderer`,
     `net.minecraft.client.renderer.PostPass`,
     `net.minecraft.client.renderer.PostChainConfig`,
     and `net.minecraft.client.renderer.LevelRenderer`.
   - Add the owners to the contract map without adding replacement mixins.
   - Status: partially done. mcdev confirmed the owner class names currently
     used in `RendererDomainContract`, including `PostPass` and
     `PostChainConfig` for the 26.2 post-processing surface.

5. Expose contract diagnostics.
   - Update `RendererDiagnostics.bugReport()` to include domain contracts under
     `rendererServices.domains`.
   - Keep current `DomainState` selected-path fields unchanged.
   - Add source invariants that diagnostics include both state and contract.

6. Add domain-specific follow-up plans before replacement.
   - Open one plan per domain that changes behavior.
   - Each domain plan must include mcdev target summaries, asset/material
     contract, fallback trigger, counters, and runtime validation steps.

## Validation

- `.\gradlew.bat checkRendererDomainInvariants --console=plain`
- `.\gradlew.bat checkRendererCoreServiceInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- mcdev static summaries for every owner class named above before behavior
  changes
- Runtime diagnostics showing all non-terrain domains remain `vanilla` until a
  domain-specific plan changes one

## Decisions

- Terrain remains the only domain with an implemented VIM GPU rendering path in
  the 26.2 renderer extension.
- Non-terrain replacement starts from contracts and diagnostics, not mixins.

## Follow-Up

- Create domain-specific active plans for entity, block-entity, particle, sky,
  cloud, weather, translucent effect, or post-processing replacement.
