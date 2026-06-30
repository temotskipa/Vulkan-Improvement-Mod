# Technical Debt Tracker

Track cleanup work that should be handled in small follow-up changes.

| Item                              | Area         | Status   | Notes                                                                                                                             |
|-----------------------------------|--------------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| Document GPU validation matrix    | Reliability  | Partial  | Template and build entries in RELIABILITY.md; 26.2 stable row added 2026-06-23; fill GPU/driver rows after runtime smoke.         |
| Active plan maintenance           | Documentation| Done     | Refactor and MC 26.2 dependency update in `completed/`; active README lists the 26.2 renderer extension plan and recent completion. |
| Runtime matrix coverage           | Validation   | Planned  | Covered by [26.2 renderer extension](active/26-2-vulkan-renderer-extension.md) and [mesh replacement device-loss bugfix](active/mesh-replacement-device-loss-bugfix.md); execute mcdev runtime smoke + Vulkan validation layers + multi-GPU matrix before declaring mesh terrain generally usable. |
| Non-terrain domain replacement    | Renderer     | Planned  | Covered by [non-terrain domain contracts](active/non-terrain-domain-contracts.md); terrain path is GPU work-queue, other domains need explicit contracts before replacement. |
| RT/PT acceleration data           | Renderer     | Planned  | Covered by [RT PT acceleration data](active/rt-pt-acceleration-data.md); keep optional readiness separate from startup requirements and terrain replacement stability. |
| Optional C2ME-OCL integration     | Worldgen     | Planned  | Covered by [26.2 renderer extension](active/26-2-vulkan-renderer-extension.md); use C2ME + C2ME OpenCL as an optional, opt-in GPU-authoritative worldgen path with dependency detection and compatibility warnings. |
| Build-time SPIR-V pipeline        | Build        | Done     | `compileTerrainSpirv` remains best-effort for development builds, but `"-Pvim.releaseBuild=true"` now requires `glslangValidator` for SPIR-V generation and shader validation. |
