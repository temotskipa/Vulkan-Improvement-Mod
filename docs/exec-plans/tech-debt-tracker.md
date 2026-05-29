# Technical Debt Tracker

Track cleanup work that should be handled in small follow-up changes.

| Item                              | Area         | Status   | Notes                                                                                                                             |
|-----------------------------------|--------------|----------|-----------------------------------------------------------------------------------------------------------------------------------|
| Document GPU validation matrix    | Reliability  | Open     | Record tested GPU, driver, Vulkan version, flags, and result in RELIABILITY.md.                                                   |
| Active plan maintenance           | Documentation| Open     | The 1.4 renderer refactor plan is now a long historical narrative; condense or move to completed/ and spawn focused successor plans after foundation phase. |
| Runtime matrix coverage           | Validation   | Open     | Execute `runClient` + Vulkan validation layers + multi-GPU smoke matrix before declaring the mesh terrain path generally usable. |
