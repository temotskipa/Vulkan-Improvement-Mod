# Accelerated Worldgen Provider Integration

**Status:** Active.

## Goal

Add an optional accelerated-worldgen provider integration track without making
the provider a renderer dependency, loader dependency, or source of default
renderer authority.

## Architecture

The first implementation is diagnostics-only detection. VIM records whether a
compatible accelerated-worldgen provider appears to be present, whether the
integration is enabled by user configuration, and why the renderer is still
using CPU-authoritative Minecraft chunk state by default.

CPU-authoritative Minecraft chunk state remains the renderer default. Provider
output can only become worldgen-authoritative inside a later explicit
integration mode where the provider itself owns the accelerated generation path,
the user opted in, and compatibility diagnostics are clean.

## Public Reference Boundary

Public docs describe only inferred architecture and compatibility boundaries.
Named provider projects, source links, module names, branch notes, and
license-sensitive source details stay in ignored local notes under
`docs/local/`.

## Constraints

- Do not make the provider a required dependency.
- The initial implementation must not add `suggests` or `recommends` to
  `fabric.mod.json`; decide Fabric metadata only in a later explicit metadata
  change.
- Do not initialize provider-specific renderer behavior when OpenGL is active.
- Do not label provider output as renderer-authoritative by default.
- Keep render-only distant terrain and LOD pages separate from provider-owned
  gameplay chunk generation.
- Diagnostics must be useful when the provider is absent, present but disabled,
  present and enabled, or blocked by a compatibility reason.

## Implementation Tasks

1. Add a source invariant for provider diagnostics.
   - Extend an existing pure Java invariant or add a new runtime invariant that
     requires a provider diagnostics object in `RendererDiagnostics.bugReport()`.
   - The invariant must verify absent-provider behavior reports
     `present=false`, `integrationEnabled=false`, and
     `rendererAuthority="cpu-chunk-state"`.
   - Run `.\gradlew.bat check --console=plain` and confirm the new invariant
     fails before production code exists.

2. Add a small runtime compatibility contract.
   - Create a focused runtime class that exposes provider diagnostics as a
     `Map<String, Object>`.
   - Detection must use loader mod-container presence only, not provider source
     APIs.
   - Configuration must default off.
   - The map must include `present`, `integrationEnabled`, `rendererAuthority`,
     `worldgenAuthority`, `fallbackReason`, and `metadataRelationship`.

3. Add diagnostics to bug reports.
   - Include the compatibility map under a stable key in
     `RendererDiagnostics.bugReport()`.
   - Keep diagnostics visible even when the provider is absent.
   - Do not change terrain capture, mesh replacement, GPU-world page authority,
     or Fabric metadata in this task.

4. Add Video Settings and live-debug flag exposure only after diagnostics exist.
   - Add a config flag that enables the optional provider integration track.
   - Expose the flag through the same Video Settings and live debug setter path
     used by other VIM config flags.
   - The flag must only change diagnostics until a later implementation plan
     defines provider-owned worldgen behavior.

5. Add runtime validation notes.
   - Capture bug-report diagnostics with the provider absent.
   - Capture bug-report diagnostics with the provider present but integration
     disabled.
   - Capture bug-report diagnostics with the provider present and integration
     enabled, proving renderer authority stays `cpu-chunk-state` until an
     explicit provider-owned worldgen mode exists.

## Validation

- `.\gradlew.bat checkGpuResidentTerrainArchitectureInvariants --console=plain`
- `.\gradlew.bat checkDependencyBaselineInvariants --console=plain`
- `.\gradlew.bat checkVulkanVideoOptionsInvariants --console=plain`
- `.\gradlew.bat check --console=plain`
- `.\gradlew.bat build --console=plain`
- Public-doc external-reference scan excluding `docs/local/`.

Runtime validation is required before any provider-owned worldgen behavior
changes. It must include bug-report diagnostics and preserve vanilla renderer
fallback under OpenGL-active sessions.

## Decisions

- The dedicated provider track exists so the renderer extension can move
  forward without adding hidden loader or source-code coupling.
- Diagnostics come before behavior. Presence detection alone must not affect
  renderer correctness, chunk authority, or dependency resolution.
- Fabric metadata stays unchanged until this plan explicitly approves a later
  metadata task.

## Exit Criteria

This plan is complete when:

- bug-report diagnostics expose provider presence, user opt-in state, authority
  labels, metadata relationship, and fallback reason;
- provider absence and presence both have pure invariant coverage;
- Video Settings and live-debug paths expose the opt-in flag;
- no `fabric.mod.json` hard or soft dependency relationship is added unless a
  later explicit metadata decision is implemented;
- renderer authority remains CPU chunk state unless a separate provider-owned
  worldgen implementation plan is opened and validated.
