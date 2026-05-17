# Product Specs

## Current Scope

Vulkan Improvement Mod is a client-side Fabric mod that targets modern Vulkan
terrain-rendering paths in Minecraft.

## User-Facing Behavior

- The mod initializes only in the client environment.
- The Vulkan backend must satisfy the mod's required API, extension, and feature
  set.
- Video settings expose mod-controlled renderer options through client mixins.
- Unsupported Vulkan capability combinations should fail clearly or fall back
  according to the active renderer mode.

## Out Of Scope Until Documented

- Server-side gameplay behavior.
- Networking features.
- Non-Vulkan renderer rewrites beyond compatibility handling.
- Public configuration UI beyond the current video-settings integration.
