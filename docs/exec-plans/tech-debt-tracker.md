# Technical Debt Tracker

Track cleanup work that should be handled in small follow-up changes.

| Item                           | Area         | Status | Notes                                                                                                               |
|--------------------------------|--------------|--------|---------------------------------------------------------------------------------------------------------------------|
| Add mixin config verification  | Build checks | Open   | Verify every class in `vulkanimprovement.client.mixins.json` exists and every client mixin is listed when intended. |
| Add dependency-boundary check  | Architecture | Open   | Fail builds if `client.vulkan` imports `mixin.client`.                                                              |
| Add focused pure-Java tests    | Testing      | Open   | Start with helper logic that does not require Minecraft runtime boot.                                               |
| Document GPU validation matrix | Reliability  | Open   | Record tested GPU, driver, Vulkan version, flags, and result.                                                       |
