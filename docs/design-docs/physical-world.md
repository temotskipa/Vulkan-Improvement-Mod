# Physical World Simulation

## Summary

The mod's aim, in the owner's words: "extend Mojang's renderer to use the latest
and greatest capabilities of the Vulkan API to full effect (and possibly even
change game logic to match, e.g. use RT lighting to calculate mob spawning)".

This document records the scope agreed for the game-logic half of that aim
(2026-11-08 and 2026-11-09). Light, heat, sound, wind and weather become physical
quantities computed by ray casting through the block grid, and every rule that
reads light, and several that do not today, is rewritten against them. The
renderer draws the same quantities the rules use. Nothing of it is built yet;
each step gets an execution plan of its own (`docs/PLANS.md`).

It supersedes the boundaries in "RT-Derived Gameplay Lighting" of
[gpu-driven-worldgen-rendering.md](gpu-driven-worldgen-rendering.md) where the
two differ.

## Principles

- Realism first. Rules are rewritten from physical behaviour, not tuned to play
  like vanilla; farms, spawn-proofing and survival change where physics says so.
- Realism stays passive. It changes what the world does on its own (what
  spawns, grows, melts, burns or floods), not what players may do: breeding by
  feeding, for example, works whenever it does today.
- One model, evaluated the same way everywhere. Gameplay quantities come from a
  deterministic model: fixed ray sets, no random sampling and no accumulation
  over frames. The client's stochastic effects never feed gameplay.
- The server decides. Gameplay runs on the server; the client computes the same
  model to draw it, and the renderer builds on the gameplay light rather than
  the other way round.
- Rules read physical values. Minecraft's 0-15 light levels survive only as a
  compatibility layer derived from the physical light.
- Data-driven. Material properties, species sensitivities and rule parameters
  have defaults and can be overridden by datapacks.
- Every subsystem can be switched per world.
- Extend Minecraft. Rules are changed at Minecraft's own seams (for monster
  spawning, `Monster.isDarkEnoughToSpawn`, the only check `checkMonsterSpawnRules`
  makes), not by a parallel game.

## The Shared Core

### Geometry

Gameplay rays walk the block grid. Each 16x16x16 section is described per block
as empty, full and opaque, attenuating (glass, leaves, water, ice), or of a
complex shape. A ray skips sections with nothing in them, steps through a
section block by block (a DDA walk), passes or stops at full blocks at once,
and tests the boxes of a complex block only inside that block.

- Complex blocks present the boxes of their render model: the cuboids of the
  block model in the enforced resource pack, rotated elements as oriented
  boxes. Cutouts such as leaves let light through in proportion to their
  texture's coverage.
- Entities block light by their bounding boxes, block entities by their block's
  shape.
- The same loop runs on the CPU and in GPU compute. On the GPU, ray queries
  (`VK_KHR_ray_query`) are used for speed where present: skipping empty space
  and testing boxes. No gameplay ray needs triangle ray tracing.

### Materials

- Optics come from LabPBR: albedo, emission and how light passes through or
  scatters in a block, in RGB plus an ultraviolet channel. Sunlight carries
  ultraviolet; glass and water absorb it.
- The server reads them from a resource pack it enforces, layered over
  Minecraft's own client assets. A dedicated server has no client assets, so
  it downloads the client jar of its Minecraft version from Mojang on first
  start, checks its hash, and reads textures and block models from it.
- Blocks without PBR data get a fallback record from their vanilla textures,
  and emitters without PBR emission take their intensity from Minecraft's light
  emission level.
- Thermal, acoustic and flammability properties are derived from what Minecraft
  already knows about a block (sound type, flammability, map colour and the
  like), with datapack overrides keyed by block or tag.

### Quantities

Light is coloured irradiance in real magnitudes: noon sun about 100,000 lux,
full moon about 0.2 lux, a torch tens of lux nearby. It comes from:

- the sun, on Minecraft's overhead east-west path, so shadows move during the
  day; the world is flat and lit by parallel sunlight, so there is no latitude,
  and there are no seasons: day length stays Minecraft's;
- the sky dome and the moon, scaled by time of day, weather and moon phase;
- emitting blocks, held and worn items, burning and glowing entities,
  projectiles and dropped items;
- light bounced off blocks by their albedo.

Clouds from the weather fields below cast shadows.

Heat is temperature from ambient air (biome climate, time of day, weather),
radiation (absorbed sunlight by albedo, hot emitters), conduction through
blocks by their conductivity and heat capacity, and convection, simulated near
players.

Sound travels by rays absorbed and reflected by block materials.

Weather and wind are deterministic fields of the world seed, position, time and
biome climate. Anyone can evaluate them anywhere and at any time without
simulation or syncing; commands such as `/weather` lay overrides on top. The
price is that player-made heat cannot raise weather of its own.

### Evaluation

- Rules ask for quantities at the positions they care about (spawn candidates,
  plants, entities). Nothing computes whole fields: Minecraft's spawner samples
  a few positions per chunk per tick.
- Light splits by how it changes: emitters are cached until blocks near them
  change; sky visibility is cached and scaled by time and weather; sun
  visibility is one ray toward the sun when a rule asks.
- The server uses a GPU when it has one (any Vulkan compute GPU, headless, on
  Linux and Windows; the mod bundles the Vulkan bindings Minecraft's server jar
  lacks) and the CPU otherwise. Results may arrive a tick late, read back
  without stalling the server thread.
- In singleplayer the integrated server shares the client's Vulkan device and
  its structures, so the screen and the rules see one evaluation.
- With a GPU present, the GPU's results are the reference; the CPU path only
  needs to come close. Tests compare the two.
- Server owners configure the work budget and how quality falls off under load
  (fewer rays, staler caches, slower updates far from players). The design
  target is a medium server: up to about 50 players at 20 ticks per second.

## Gameplay

Rules use rates and doses, not thresholds: how likely a mob is to spawn rises
smoothly as light falls, growth follows what a plant received over time, and
burning follows an accumulated dose. Each mob, plant and sensor has its own
spectral sensitivity in data, with defaults from biology.

| System              | What changes                                                                                                                                                                                       |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spawning            | Every light check of spawn rules (monsters, animals, bats, slimes, glow squid, patrols, spawners' custom ranges) reads light averaged over fixed points of the mob's bounding box at the spot.       |
| Growth              | Crops, stems, berries, bamboo, saplings, mushroom spread and grass and mycelium accumulate growth from the light and warmth they received, replacing random-tick growth.                            |
| Mob behaviour       | Undead burn by accumulated ultraviolet dose, so shade and glass protect them. Fleeing the sun, sun-avoiding paths, fox shelters and the light preference of walk targets read physical light.       |
| Mob perception      | How far a mob sees a target depends on how lit it is; mobs hear by ray-cast sound.                                                                                                                 |
| Sculk               | Sensors, shriekers and the warden detect vibrations travelling as sound, absorbed and reflected by materials, in place of the wool rule.                                                           |
| Redstone sensors    | The daylight detector measures physical sunlight (its signal stays 0-15), so shadows and clouds reach circuits.                                                                                    |
| Heat                | Melting and freezing of snow, ice, frosted ice and icicles, water freezing and thawing, and evaporation follow temperature. Fire and lava ignite and spread by heat, dryness and wind.              |
| Weather and water   | Rain and meltwater add water (puddles, rising rivers, floods); snow accumulates in layers in cold weather; wildfires spread and rain puts them out.                                                 |
| Wind                | Moves clouds and storms, and acts on fire and smoke, sound, and entities and projectiles.                                                                                                          |
| Animals             | Passive cycles from weather and biome: animals shelter in storms and cold snaps; coats and food vary by biome.                                                                                     |
| Sky checks          | Fishing, precipitation and similar sky-visibility checks follow physical sky visibility.                                                                                                          |
| Creatures' warmth   | Body temperature of players and mobs comes later, on the same heat model.                                                                                                                          |

- A chunk that loads after time away catches up exactly: blocks do not change
  while it is unloaded, and the sun, clouds and weather are deterministic, so
  the light and weather it missed can be integrated rather than guessed.
- Each dimension type defines its physics in data. Defaults: the Nether is hot
  with no sky, lit by lava and fire; the End is cold, with a faint fixed sky and
  no weather.
- World generation stays Minecraft's; climate varies by biome.

## Compatibility

- The physical model replaces light engine-wide. Code not rewritten, vanilla's
  or other mods', reads 0-15 levels derived from it. Minecraft's flood-fill
  light engine retires as far as that layer allows.
- Mods and datapacks can detect that the simulation is on and query the
  physical quantities directly.
- The mod is required on the server and on every client. Clients need hardware
  ray queries. Worlds and servers are not kept usable by vanilla.

## Rendering

- The renderer draws the gameplay light: it evaluates the same model with ray
  queries and adds detail on top (exact triangles and texels, reflections,
  further bounces), so what looks dark is what is dark to the rules.
- Light keeps its real magnitudes on screen; the renderer adapts exposure like
  an eye, so caves become visible over seconds and daylight is blinding when
  leaving one.
- The debug screen shows illuminance, temperature and sound level, and overlay
  views show spawnable areas, a heat map and sound. The client computes them
  with the same model, and the server sends corrections where its values
  differ.
- Ray-traced effects come in quality presets, each with its own frame-rate
  target, and an adaptive mode that holds a chosen rate.
- Clouds and weather are drawn from the same fields the server uses.

## Building On Other Mods

The mod takes in the code of three mods and is relicensed from the Unlicense to
GPL-3.0 with the first commit that does, keeping their copyright notices and
marking what is changed. Their code is compiled against Minecraft's classes and
works through mixins, so using them as plain libraries would not survive
Minecraft versions; it is ported with the rest of the mod instead.

| Mod                                                                       | Licence      | Use                                                        |
|---------------------------------------------------------------------------|--------------|------------------------------------------------------------|
| [Sound Physics Remastered](https://modrinth.com/mod/sound-physics-remastered) | GPL-3.0      | What players hear: occlusion, reverb and absorption.       |
| [Attract to Sound](https://modrinth.com/mod/attract-to-sound)             | GPL-3.0      | Mob perception: sight and hearing.                         |
| [Thermoo](https://modrinth.com/mod/thermoo)                               | LGPL-3.0     | The temperature API that heat and, later, body warmth use. |

## Open Points

- How many bounces gameplay light takes; one is the starting point.
- The formulas and default numbers of each rule, and the default spectral
  sensitivities of species.
- How late results may arrive before a rule notices.
- Retiring Minecraft's light engine and its light data in saves and network
  packets.
- Memory budgets on server and client, and how the shared structures relate to
  the mesh path's terrain pages.

## Proposed Order

1. The shared core on the CPU: materials (enforced pack over the client jar),
   the voxel walk, the light model and the 0-15 compatibility layer.
2. GPU evaluation: server compute, then the client's ray queries, with tests
   comparing them; the renderer's lighting grows from here.
3. Sun, sky, moon and time, then the weather and wind fields with clouds.
4. The rules: spawning, growth, mob behaviour, sensors and sky checks.
5. Heat: melting, freezing, fire and lava, water.
6. Sound: what players hear, sculk vibrations, then mob perception.
7. Wind on fire, smoke, sound, entities and projectiles.

## Sources

- The owner's chat with Gemini, "Ray Tracing for Minecraft Gameplay"
  (reviewed 2026-11-08), proposed the traversal: per-section bitmasks walked by
  DDA, ray queries clamped to a single block for complex shapes, section boxes
  for empty space, and one instanced structure per block shape. Its
  suggestion to let the integrated server read the renderer's stochastic
  irradiance cache is not taken, as it would make spawning depend on where the
  player had looked.
- Minecraft 26.4 code read with mcdev: the light checks of spawn rules, growth
  and melting blocks, `Mob.isSunBurnTick`, `FleeSunGoal`, fox shelter goals,
  `getPathfindingCostFromLightLevels`, and `CloudRenderer`.
