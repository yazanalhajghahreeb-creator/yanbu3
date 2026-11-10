# Minecraft Mob Kill Rewards – Fabric 1.21.11

**Repeatable rewards on every kill credited to the player** (melee or their projectiles). Item rewards drop at the mob's position; permanent abilities are unlocked per player and persist across death and rejoin.

## Rewards
- Zombie: permanent Strength II (auto-refreshes after death/login/dimension changes).
- Skeleton: named bow with Power V and Infinity; 64 normal arrows; Infinity uses no arrows as long as you keep one normal arrow in inventory.
- Creeper: 5 instant-use TNT charges displayed using the real 3D TNT block model. Right-click toward a block within 30 blocks to explode there. Block destruction follows vanilla TNT/world settings.
- Enderman: 4 **Ender Pearls** dropped.
- Spider: automatically unlocks wall climbing when killed. Hold W while facing and touching a wall (even from the ground); release W or hold Shift to stop. Client-side movement plus server-side assist; ability syncs when joining a world.
- Witch: permanent Regeneration I and clears/prevents Poison effect via each-tick cleanse.
- Blaze: flight immediately enabled on kill; Flight Toggle item drops on the ground and right-click toggles flight (no OP or command necessary); double-tap Jump to start flying. Flight preference persists on death/rejoin.
- Ghast: Ghast Cannon item with a unique pixel-art cannon texture (not a Ghast Tear). Right-click to launch a real Ghast fireball (1.5s cooldown).
- Piglin: **32 gold ingots** to barter.
- Warden: Sonic Focus item. Right-click while aiming at a hostile mob within 32 blocks to deal lethal sonic damage. Players, animals and neutral creatures are never targeted. 4-second cooldown.

## Installation
1. Install the **Minecraft Java 1.21.11** Fabric Loader.
2. Install Fabric API compatible with 1.21.11 (tested against **0.141.1+1.21.11**).
3. Copy dist/mob-rewards-1.21.11-1.0.1.jar (latest branch build) to your .minecraft/mods folder.
4. Launch Minecraft with the Fabric 1.21.11 profile. For multiplayer, the mod and Fabric API must be on the server and clients.

## Commands
- /mobpowers status: inspect your unlocked rewards.
- /mobpowers flight: toggle your Blaze flight permission without OP.
- /mobpowers restore: replaces missing **reusable** flight/cannon/sonic items when you've unlocked them; never refills TNT/gold/arrows/Eyes of Ender.

## Persistence and limitations
- World save file: mobrewards-progress.json at the server world root; keyed by player UUID; stores ability unlocks and flight preference (NOT a one-time reward limit). Back it up with your world.
- Grants are server-authoritative, rewards earned on kills credited to a player (melee or their projectiles), not pet, environmental or other player kills.
- Permanent effects are implemented by short potion durations refreshed indefinitely rather than an infinite effect instance. Regeneration is level I.
- Poison immunity uses a server-side effect-application event to reject Poison, with every-tick removal as a backup; third-party mod effects may use other mechanisms.
- Spider ascent uses synchronized client movement prediction plus server-assisted motion. Both client and server need the mod; a server configured to block all nonstandard upward movement may still require allowing modded player motion.
- Consumables drop each time the matching mob is killed; utility items also drop each time. /mobpowers restore is an optional recovery command for missing reusable tools.
- TNT attacks a targeted block/point instantly instead of physically flying as a projectile; Ghast cannon fires a vanilla LargeFireball projectile.
- Single-target Sonic Focus requires a hostile mob within 32 blocks and unobstructed view, and applies lethal sonic damage. Mobs immune to damage via another mod may survive.
- Existing player progress is not transferred between **different worlds**; each world stores its own data.
- Tested via CI compile only unless independently tested in a game instance.

## Build
Run with Java 21 and Gradle 9.2.1:
    gradle --no-daemon build
The distributable is build/libs/mob-rewards-1.21.11-1.0.1.jar (not the -sources or -dev jar).

GitHub Actions compiles and commits the distributable into dist/ when the build passes. No administrative in-game commands are used for abilities.
