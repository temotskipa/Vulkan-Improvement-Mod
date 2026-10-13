# Technical Debt Tracker

Track cleanup work that should be handled in small follow-up changes.

| Item                              | Area         | Status   | Notes                                                                                                                             |
|-----------------------------------|--------------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| Document GPU validation matrix    | Reliability  | Partial  | Template and build entries in RELIABILITY.md; 26.2 stable row added 2026-06-23; fill GPU/driver rows after runtime smoke.         |
| Active plan maintenance           | Documentation| Done     | Finished plans are in `completed/`; the active README lists what is open and the recent completions. |
| Runtime matrix coverage           | Validation   | Partial  | The 26.4 runtime matrix passed on one GPU; see the [26.4 snapshot port](active/minecraft-26-4-snapshot-port.md) and the [mesh replacement device-loss bugfix](active/mesh-replacement-device-loss-bugfix.md). Still to do before mesh terrain is called generally usable: a run with the Vulkan validation layers, and other GPUs and drivers. |
| Non-terrain domain replacement    | Renderer     | Planned  | The contracts are done: [non-terrain domain contracts](completed/non-terrain-domain-contracts.md). Replacing a domain needs a plan of its own first. |
| RT/PT acceleration structures     | Renderer     | Planned  | The data contracts and the capability gate are done: [RT PT acceleration data](completed/rt-pt-acceleration-data.md). Allocation needs a plan of its own that starts from that plan's validation section. |
| Optional accelerated-worldgen provider integration | Worldgen | Planned | Diagnostics-only detection is done: [Accelerated Worldgen Provider Integration](completed/accelerated-worldgen-provider-integration.md). Provider-owned world generation needs a plan of its own. |
| Build-time SPIR-V pipeline        | Build        | Done     | `compileTerrainSpirv` remains best-effort for development builds, but `"-Pvim.releaseBuild=true"` now requires `glslangValidator` for SPIR-V generation and shader validation. |
