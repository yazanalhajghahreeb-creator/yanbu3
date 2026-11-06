# Burned World – Plan A+B v0.4.1 User UV

Fabric for Minecraft Java 1.21.4. Experimental.

Base: `BurnedWorld_Reset_PlanAB_Fabric_1.21.4_v0.4.0-alpha.jar` (same world-gen and mod class files).

Textures derived directly from user-supplied UV pictures by nearest-neighbor scaling into Minecraft UV texture dimensions. No recoloring or invented details.

Included: Blaze, Creeper, Cow, Spider, Zombie, Sheep body + wool, Pig.

**Limits:** Source UV drawings do not necessarily match default vanilla model UV coordinate placement. This package does not include new 3D mob geometry. The skeleton image was a perspective image, not a vanilla UV layout; dragon UV is for a different two-headed model. Thus those were NOT applied to vanilla models. Existing source textures for Skeleton, Enderman, etc., stay unchanged.

**Testing:** Only a static ZIP/JAR integrity and entry check was performed; needs a real Minecraft visual test. New world for Plan A+B terrain. Keep only this one Burned World mod JAR alongside Fabric API.

Do not claim that the two-headed dragon or exact custom model geometry is implemented.