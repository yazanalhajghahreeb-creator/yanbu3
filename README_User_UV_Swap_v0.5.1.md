# Burned World Beta5.1 — User UV Swap (0.5.1 preview)

Based on the **user-tested Beta5.1 + Mobs JAR**. All world-generation, block, tree, mob-spawning, combat, and compiled class files are **unchanged**.

This revision replaces the following Minecraft entity textures with nearest-neighbor UV remappings from the **user's uploaded art**, not generated or freshly painted:
- Zombie
- Skeleton
- Creeper
- Pig
- Cow
- Sheep and wool
- Enderman (the approved tall red-and-flame figure)

Spider and Blaze are unchanged as they were already displaying correctly for the user.

The user-provided special **two-headed dragon** artwork needs new 3D model geometry and was **not** applied to the vanilla dragon model.

**Known limitation:** These are manually remapped from flattened presentation UV nets; alignment on Minecraft's default entity geometry must still be checked inside the game. The original source images are preserved in the ChatGPT conversation, not embedded in this JAR. No claim of exact 3D model replication is made.

This JAR is experimental and has **not been playtested** in Minecraft. Its internal mod version retains 0.5.0-alpha since only resource skins were changed.

To test: remove other Burned World JARs from `mods`, install this one alongside Fabric API for Minecraft 1.21.4, then test with `/function burnedworld:testing/show_mobs` in a test world.

No biome or block changes were made, pending clarification of the user's second request.
