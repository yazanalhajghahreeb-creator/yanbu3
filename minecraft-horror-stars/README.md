# Yanbu3 Horror Stars v1.0.0 (Forge 1.19.2)

Custom Minecraft challenge add-on for `All the Creatures PTSD edition 1.0.0.mrpack` (Minecraft 1.19.2, Forge 43.5.0).

- 20 configured normal horror mob entity IDs, excluding the Lich and major bosses.
- Player kills trigger 20 distinct colored **Horror Star** Nether Star variants (CustomModelData: 91001-91020).
- Stars appear at the killer's feet. Each type counts only after pickup.
- The **TeamStars** scoreboard counts unique creature types across players; win goal 10.
- One-second scan sets selected entities' max health to 45 and base attack to 6 where supported.
- Uses **Forge `lowcodefml`** datapack/resourcepack approach, no compiled Java mappings. This avoids the v0.1.0 `NoSuchFieldError: level` bug.

**Runtime not yet tested:** structural JAR validation passed; mod-specific invulnerability and forced despawning are not overridden, and full compatibility with the uploaded pack is not guaranteed.

**Install**: First remove prior `Yanbu3-Horror-Heads-Forge-1.19.2-v0.1.0.jar`; put the new stars JAR in the instance `mods` folder; test on a copy of a world. Open the `/scoreboard` sidebar or run `/function horrorstars:help`. The `/function horrorstars:reset` command starts a new challenge.

For a simple test, run `/summon graveyard:ghoul ~ ~ ~5` and defeat the ghoul; pick up its colored star to advance the shared counter.

The actual JAR is currently provided in the ChatGPT conversation as a download. **This GitHub folder is documentation only, not a JAR binary upload or tested release.**
