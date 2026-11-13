# Yanbu3 Horror Heads (Forge 1.19.2)

**Prototype v0.1.0 (not yet tested in a running game).** Separate addon JAR for the user's `All the Creatures PTSD edition 1.0.0` modpack (Minecraft 1.19.2, Forge 43.5.0).

- Targets actual living combat mobs from recognized horror mod namespaces, excluding several common ambient/jumpscare-only names.
- Sets supported combat mobs to around 40 HP and base attack attribute to 6 (about double a zombie, where those attributes exist).
- Player attacks can directly damage tracked combat creatures, including some that ignore the normal damage pipeline.
- Drops a **named vanilla player head** with `HorrorCreatureId` NBT after a player defeats a tracked mob. Unique head textures and a 10-head objective tracker are **not yet included**.
- Does not redistribute the original horror mods.

**Important:** The JAR is a structural prototype compiled against API-shaped development stubs, **not tested with official ForgeGradle / in-game**; compatibility with each original custom entity is not guaranteed. Back up your world, use a fresh test instance, and report any crash logs before using it in a recording.

The three optional separate Forge 1.19.2 mods that were discussed (install on client and multiplayer server from their own official pages):
- [The One Who Watches](https://modrinth.com/mod/the-one-who-watches)
- [Siren Head: The Arrival](https://www.curseforge.com/minecraft/mc-mods/siren-head-the-arrival)
- [The Mimicer](https://modrinth.com/mod/the-mimicer)

These three are **not bundled** in the add-on JAR. The three mods' exact runtime compatibility with the pack has not been validated.
