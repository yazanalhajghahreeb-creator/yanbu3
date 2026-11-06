# Burned World Beta 5.1 + Mobs (v0.5.0-alpha)

**Experimental Fabric 1.21.4.** Directly patched from the user-uploaded `BurnedWorld_Inferno_Fabric_1.21.4_v0.3.0-beta5_1_REGISTRY_HOTFIX(1).jar`.

## Preserved
- The original Beta 5.1 Nether-style Overworld terrain, biomes' surface shape, trees, lava oceans, shallow ore setup, and structure-generation files.
- All dimension, noise settings, placed features, and configured features remain byte-for-byte identical. 
- Only the custom biomes' **monster spawn lists** were changed to include Spider and exclude Wither Skeleton in the open world.

## Changes
- The user-supplied flattened UV art was installed with nearest-neighbor resizing, without repainting, for Blaze, Creeper, Cow, Spider, Zombie, Sheep body and wool, and Pig.
- Vanilla models remain; original complex custom model geometry (horns, dragons with multiple heads, etc.) is not added.
- Controlled land spawning on Nether floor blocks, excluding Wither Skeleton (Fortress-only intended).
- Event-based retaliatory effects: Sheep burn, Pig recoil, Zombie fire on hit, Skeleton poisoned arrows, Wither Skeleton poison.
- Spider flame trail/projectile approximation (particles + invisible moving marker).
- Cow drops non-edible charcoal as black meat placeholder, Pig drops porkchop. Creeper keeps normal blast.

## Testing
1. Use **one** Burned World JAR only plus Fabric API for 1.21.4.
2. Create a **new TEST world** (Normal).
3. Use `/function burnedworld:testing/show_mobs` with cheats to visually inspect ten mob types. The Wither Skeleton summoned by this test function is not part of normal open-world spawning.
4. Check natural and fallback mob appearance on Nether ground. If something fails, attach the new `latest.log`.

**Verification performed:** ZIP integrity, JSON syntax, unique archive paths, survival of original compiled mod class, biome spawn data checks, and byte-for-byte equality for original dimension, noise, configured/placed-feature files.

**Not tested in a running Minecraft client.** Changes to advancements or tick functions may require a further game-tested hotfix. Custom dragon and End portal placement are deferred.
