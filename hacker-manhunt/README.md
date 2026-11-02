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
