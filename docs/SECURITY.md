# Security

This is a client-side rendering mod. The primary risk is local stability rather
than remote data exposure, but the repository should still keep security
assumptions explicit.

## Assumptions

- The mod runs in the local Minecraft client process.
- Vulkan and LWJGL calls operate with the permissions of that local process.
- Runtime data under `run/` is local development state and should not be
  committed.
- No network service or credential store is introduced by the current codebase.

## Guardrails

- Do not add network access without documenting purpose, data flow, and failure
  behavior here.
- Do not log secrets, local account tokens, or full user filesystem paths unless
  they are already part of an explicit diagnostic workflow.
- Keep crash-prone native interop changes narrow and validate them with
  `.\gradlew.bat build` plus runtime testing on supported hardware.
