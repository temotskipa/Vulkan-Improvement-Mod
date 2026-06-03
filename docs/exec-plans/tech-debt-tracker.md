# Technical Debt Tracker

Track cleanup work that should be handled in small follow-up changes.

| Item                              | Area         | Status   | Notes                                                                                                                             |
|-----------------------------------|--------------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| Document GPU validation matrix    | Reliability  | Partial  | Template and first build-only entry added in RELIABILITY.md; fill rows after runtime smoke on each GPU class.                      |
| Active plan maintenance           | Documentation| Done     | Refactor plan condensed and moved to `docs/exec-plans/completed/vulkan-1-4-renderer-refactor.md`.                                  |
| Runtime matrix coverage           | Validation   | Open     | Execute `runClient` + Vulkan validation layers + multi-GPU smoke matrix before declaring the mesh terrain path generally usable. |
