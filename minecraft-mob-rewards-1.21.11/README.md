# Hensley Mode — Minecraft Java 1.21.11 (Fabric)

This is **Hensley Mode v1.1.0**. It keeps the existing mod ID `mobrewards` and the world-save file `mobrewards-progress.json` so earlier unlocks remain available. Do **not** install it alongside the old Mob Rewards JAR: both use the same mod ID.

## What changed in v1.1.0
- **Creeper TNT Charge:** forced `minecraft:item_model = minecraft:tnt`, the identical item model used by the vanilla TNT block, on new and legacy item stacks. Its 5-charge explosive behavior is unchanged.
- **Ghast Cannon:** distinct custom 32 × 32 pixel-art cannon item texture; `minecraft:item_model = mobrewards:ghast_cannon` is explicitly set and repaired on older stacks. The special launcher is not an ordinary Ghast Tear.
- **Spider Climb:** no item and no command. Killing a Spider or Cave Spider unlocks the saved ability for that player. Press and hold **W** against a solid wall. The client detects the block ahead and applies upward motion before movement plus a small collision-checked step afterward. Hold **Shift** or release W to stop. The unlock packet re-syncs periodically and when joining.
- A client-facing action-bar notification confirms when Spider Climb state is synced.

## Rewards
- Zombie: permanent Strength II.
- Skeleton: Power V + Infinity bow and 64 arrows drop.
- Creeper: 5 TNT Charges drop per kill.
- Enderman: 4 Ender Pearls drop.
- Spider / Cave Spider: persistent auto-climb unlock.
- Witch: Regeneration I, protection against Poison effect.
- Blaze: flight enabled on kill, plus Flight Toggle item.
- Ghast: custom Ghast Cannon.
- Piglin: 32 gold ingots per kill.
- Warden: Sonic Focus aimed hostile-only sonic attack with cooldown.

Rewards repeat on credited kills, while ability unlocks remain stored per player in the world save.

## Installation
1. **Remove ALL previous** `mob-rewards*.jar` or `Hensley*.jar` from your `.minecraft/mods` directory. Never run old and new builds together.
2. Use **Fabric Loader for Minecraft Java 1.21.11** and compatible **Fabric API**.
3. Install **`Hensley-Mode-1.21.11-1.1.0.jar`** into the `mods` folder. For multiplayer, install the same build and Fabric API on **both the client and server**.
4. Launch, and check that the Fabric Mods menu shows **Hensley Mode 1.1.0**. If it still shows Mob Kill Rewards or 1.0.1, the older build is being loaded.
5. For Spider, kill a Spider (or reuse an existing Spider unlock), walk against a solid vertical wall while holding W without Shift. The action bar should show the Hensley Mode sync message when unlocked.

## Commands (optional)
- `/mobpowers status` shows unlocked abilities.
- `/mobpowers flight` toggles Blaze flight permission.
- `/mobpowers restore` reissues missing reusable ability items; consumables only drop on mob kills.

## Technical limitations and verification
- The build pipeline compiles with Minecraft 1.21.11 Mojang mappings, Fabric Loader 0.18.1, and Fabric API 0.141.1+1.21.11 on Java 21.
- JAR checks verify the displayed mod name/version, actual item-model definitions, custom texture file (and PNG dimensions), and bundled client/server climb classes. A successful build is **not** an in-game integration test; actual climbing and visuals should still be confirmed in-game.
- Very aggressive multiplayer movement anti-cheats may reject upward movement: first test with a local Fabric world.
- The world save `mobrewards-progress.json` is unchanged in format; do not remove it unless resetting all powers is intentional.
- The Ghast Cannon remains a vanilla Ghast-fireball launcher. TNT charge detonation respects vanilla explosion settings.
- All previous reward functionality remains enabled; two new display-model bindings specifically prevent item model fallback.

## Build
```bash
gradle --no-daemon build
```
Output: `build/libs/Hensley-Mode-1.21.11-1.1.0.jar`. The GitHub Actions workflow publishes the same JAR into `dist/`.
