# Yanbu3 Horror Heads v0.2.0 — Forge 1.19.2

Previous v0.1.0 caused a NoSuchFieldError: level at world loading. It was compiled against hand-made Java API stubs instead of being reobfuscated using ForgeGradle.

This source version uses official ForgeGradle 5.1 + Minecraft 1.19.2 Forge 43.5.0 mappings and reobfJar. To build, run `gradle build` with Gradle 7.6.3 and JDK 17.

## Intended features
- Only recognizes hostile living creatures from listed horror mod namespaces.
- Excludes passive/spectator/jumpscare-only entities by behavior and entity IDs where possible.
- At least 40 health and 6 base attack for recognized creatures (does not nerf stronger existing mobs).
- Allows player attacks and awards one labeled player head on a player kill.
- Does NOT bundle third-party horror mods or new horror creatures. The three requested extra horror mods need separate installation after checking compatibility.

## Cautions
- A successful compilation does not prove runtime compatibility with every third-party entity. Test in a copied world with the exact pack.
- Heads currently share the vanilla player-head model with different labels/NBT; there are no bespoke 3D head models or collection progress counter.
- Remove the old v0.1.0 JAR before adding v0.2.0.
