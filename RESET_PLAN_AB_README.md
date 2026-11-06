# Burned World Reset Plan A+B — v0.4.0-alpha (Fabric 1.21.4)

**Experimental mod. Test with a NEW world. Requires Fabric Loader and Fabric API for 1.21.4.**

- Overworld-style terrain: plains, large basins, hills and distinct crimson/warped woodland zones.
- Nether-style ground, lava instead of oceans/rivers, custom lava patches.
- No custom cave carvers, cave noise largely bypassed. Ores targeted about 3 blocks below terrain.
- 10 mob appearances as **UV texture adaptations of the user-provided perspective PNG references**. The original render images are **not vanilla UV skins**, and the custom 3D shapes shown in them are **not yet implemented**. Vanilla mob models stay intact.
- Wither Skeleton excluded from the Overworld natural spawners; Fortress natural spawning remains subject to vanilla rules.
- Special combat abilities, lava-walking armor, custom End-portal placement and two-headed dragon are **not implemented in this reset stage**.

Static verification: JAR ZIP integrity and all JSON data were checked. **Not tested inside a running Minecraft client.**

Test function: `/function burnedworld:testing/show_mobs`

Source builder: [build_burnedworld_reset_ab.py](./build_burnedworld_reset_ab.py)
