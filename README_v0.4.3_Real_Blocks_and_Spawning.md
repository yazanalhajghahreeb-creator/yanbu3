# Burned World v0.4.3 – real Nether blocks, mob spawns

**Minecraft Java 1.21.4 / Fabric. Experimental, not playtested inside Minecraft.**

- World terrain remains Overworld-shaped, with **actual** blocks Netherrack, Crimson Nylium, Warped Nylium, Soul Soil, Basalt and Blackstone, not reskinned grass.
- Lava seas and original crimson/warped huge-fungus trees preserved from v0.4.2.
- Fallback spawning for cow, pig, sheep, creeper, spider, zombie, skeleton, enderman and blaze. Every ~20 seconds each player tests up to four safe positions per species, requiring a real solid Nether floor and two empty air blocks. Avoids extra summons if the species is already within ~70 blocks.
- Wither Skeleton is not in the fallback; natural fortress spawning remains distinct.
- User-provided UV mob skins retained byte-for-byte; vanilla model geometry still used.
- No dragon powers or custom End portal placement yet.

**Known limits:** This is a static JAR and JSON validation, not a tested in-game build. Create a new test world and leave only this version of BurnedWorld in mods alongside Fabric API.