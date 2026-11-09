# Minecraft Mob Kill Rewards – Fabric 1.21.11

One-time rewards on **direct melee killing blows** for each player (separately).

## Rewards
- Zombie: permanent Strength II (auto-refreshes after death/login/dimension changes).
- Skeleton: named bow with Power V and Infinity; 64 normal arrows; Infinity uses no arrows as long as you keep one normal arrow in inventory.
- Creeper: 5 instant-use TNT charges. Right-click toward a block within 30 blocks to explode there. Block destruction follows vanilla TNT/world settings.
- Enderman: 4 Eyes of Ender.
- Spider: climb walls by walking toward them; release/sneak to stop.
- Witch: permanent Regeneration I and clears/prevents Poison effect via each-tick cleanse.
- Blaze: flight unlock. Right-click Flight Toggle or type /mobpowers flight (no admin permissions), then double-tap Jump. Turns itself back on if it was toggled on after death or rejoin.
- Ghast: Ghast Cannon item. Right-click to launch a real Ghast fireball (1.5s cooldown).
- Piglin: **32 gold ingots** to barter.
- Warden: Sonic Focus item. Right-click while aiming at a hostile mob within 32 blocks to deal lethal sonic damage. Players, animals and neutral creatures are never targeted. 4-second cooldown.

## Installation
1. Install the **Minecraft Java 1.21.11** Fabric Loader.
2. Install Fabric API compatible with 1.21.11 (tested against **0.141.1+1.21.11**).
3. Copy dist/mob-rewards-1.21.11-1.0.0.jar to your .minecraft/mods folder.
4. Launch Minecraft with the Fabric 1.21.11 profile. For multiplayer, the mod and Fabric API must be on the server and clients.

## Commands
- /mobpowers status: inspect your unlocked rewards.
- /mobpowers flight: toggle your Blaze flight permission without OP.
- /mobpowers restore: replaces missing **reusable** flight/cannon/sonic items when you've unlocked them; never refills TNT/gold/arrows/Eyes of Ender.

## Persistence and limitations
- World save file: mobrewards-progress.json at the server world root; keyed by player UUID; write on each new unlock and flight toggle. Back it up with your world.
- Grants are server-authoritative, rewards earned only for direct melee player damage, not arrow, pet, environmental or indirect explosions.
- Permanent effects are implemented by short potion durations refreshed indefinitely rather than an infinite effect instance. Regeneration is level I.
- Poison immunity is implemented by removal every server tick; some third-party mods applying poison outside normal timing may briefly show its UI.
- Spider ascent is server-authoritative; high latency may make it appear less smooth. Wall contact and horizontal movement are required; ascent is suppressed while sneaking.
- No automatic restoration of consumables on loss/death; utility items may be reissued by /mobpowers restore.
- TNT attacks a targeted block/point instantly instead of physically flying as a projectile; Ghast cannon fires a vanilla LargeFireball projectile.
- Single-target Sonic Focus requires a hostile mob within 32 blocks and unobstructed view, and applies lethal sonic damage. Mobs immune to damage via another mod may survive.
- Existing player progress is not transferred between **different worlds**; each world stores its own data.
- Tested via CI compile only unless independently tested in a game instance.

## Build
Run with Java 21 and Gradle 9.2.1:
    gradle --no-daemon build
The distributable is build/libs/mob-rewards-1.21.11-1.0.0.jar (not the -sources or -dev jar).

GitHub Actions compiles and commits the distributable into dist/ when the build passes. No administrative in-game commands are used for abilities.
