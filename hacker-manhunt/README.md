# Hacker vs 3 Hunters — prototype (Fabric 1.21.1)

**This is source code, NOT a compiled or tested .jar.** The project has not been built in CI yet. Mod features below may need API fixes and gameplay testing.

## Gameplay
- Set roles with `/manhunt hacker <player>`, `/manhunt hunter <player>`.
- `/manhunt reset` clears run state; `/manhunt interval <seconds>` sets stage length (default 300).
- Hacker gets Creative-style flight on role assignment (while remaining in survival).
- Hunters get a tracking compass; when they hold it, an actionbar displays hacker distance and coordinates. (The compass needle is not yet synchronized.)
- **First hunter killed by hacker starts timer once**; subsequent kills do not reset it.
- Stage 1 (5 minutes after first kill): hacker's entity glow scan + nearby ore coordinates; hunters leather armor and wood equipment.
- Stage 2 (10 minutes): speed; hunters iron armor/tools.
- Stage 3 (15 minutes): `/manhunt strike` attacks a nearby hunter within 5 blocks, ignoring walls; hunters diamond gear.
- Stage 4 (20 minutes): `/manhunt blast` launches TNT in look direction; hunters retain diamond gear.
- Stage 5 (25 minutes): hunters netherite gear.

## Caveats / requested features pending
- This prototype's X-ray is **not true see-through-block rendering** for ores. It announces nearest ore location and glows nearby living entities. True graphical X-ray needs a client renderer.
- Special power is a custom explosive projectile skill, **not an integration with Loopy Powers**.
- Armor and tools are overwritten on stage transitions, not continuously restored.
- Settings and roles are held in memory and reset on server restart.
- Permissions: all role/setup commands require operator permission level 2; strike/blast require the hacker role.
- This project requires Minecraft 1.21.1, Fabric Loader, Fabric API and Java 21. It has not been compiled or verified; fix API mismatches if needed.

## Build
Install JDK 21 and Gradle 8.12, then from this folder run `gradle build`. Check `build/libs/` for the produced .jar. Modrinth's existing mods aren't automatically bundled.


## Experimental update (2026-11-06)
- `/manhunt aim` has been removed. At the third stage, the hacker receives a passive attack-speed modifier instead; there is no ability command or custom cooldown for this modifier. Minecraft combat still has vanilla damage mechanics.
- Tracking compasses are already updated before the first kill. Their target now moves in increments of two blocks to reduce needle jitter; this cannot guarantee an absolutely stationary needle when the player moves. A dedicated client-side compass renderer is needed for smooth/precise tracking.
- Nearby ores now receive glowing invisible armor-stand markers with labels (diamond, iron, coal, emerald, gold, redstone, lapis). **Known limitation:** these entities are currently visible to everyone in the same world, not only the hacker. The feature is experimental and not the intended hacker-only X-ray.
- The new changes have NOT been validated by a successful build or multiplayer playtest. Do not treat the previous JAR as updated.


## 2026-11-06 client-only X-ray / compass update (source revision; not yet playtested)

**Install this mod on the server and on each of the four clients.** Minecraft Java Fabric 1.21.1 + Fabric API is required.

- Hunters' vanilla-world view stays normal: server-side global glow and world-spawned ore armor stands were removed.
- Only the designated hacker receives X-ray enablement through a server-to-client packet after Stage 1 is unlocked (5 minutes after hacker's first hunter kill).
- Hacker-side X-ray renderer hides normal solid block models, renders ore models without face culling, and adds local glowing outlines to living entities. It is an experimental fullscreen ore visualization rather than world-visible markers.
- After the hacker loses X-ray, the client reloads chunk rendering.
- The client-side compass hand patch prevents the frequent lower/raise re-equip animation when compass tracking data changes. Direction tracking starts before the first kill and follows the hacker's position.
- The rapid-attack ability is automatically given **only to the hacker** from Stage 3; hunters retain normal combat speed.
- Full multiplayer testing and a successful GitHub Actions build are still required. Previous JAR files do NOT include these changes.

Known limitations: X-ray visually removes ordinary block models on the hacker client while active, which may have visual/performance side effects. It needs in-game testing alongside resource packs/shaders. Ores render with original textures. No claims of tested compatibility.
