# Riggyz's Patches
A small Forge mod that fixes bugs affecting the survival player experience.

![CurseForge Downloads](https://img.shields.io/curseforge/dt/1473735?logo=curseforge&logoColor=%23F16436&link=https%3A%2F%2Fwww.curseforge.com%2Fminecraft%2Fmc-mods%2Friggyzs-patches)

### Create Bugfixes
- **[Create#9511](https://github.com/Creators-of-Create/Create/issues/9511):** The schematicannon reads the wrong block entity when calculating item requirements, allowing item duplication in survival.
- **[Create#6902](https://github.com/Creators-of-Create/Create/issues/6902):** Large contraptions cause severe lag spikes due to a VoxelShape merging pass when recalculating physics shapes. Replaced with direct per-block AABB decomposition.
- **[Create#9501](https://github.com/Creators-of-Create/Create/issues/9501):** Bound cardboard block recipe requires literal `minecraft:string` item instead of the `forge:string` tag, breaking modpacks that replace vanilla string.
- **[Create#7882](https://github.com/Creators-of-Create/Create/issues/7882):** Portable Storage Interface intermittently fails to transfer items because neighbors (funnels, hoppers) aren't notified to re-query capabilities after a swap.
- **[Create#9720](https://github.com/Creators-of-Create/Create/issues/9720):** Server crash when disassembling a train via CC:Tweaked's `station.disassemble()` Lua call while the DEPARTURE event fires with a null train reference.
- **[Create PR#9481](https://github.com/Creators-of-Create/Create/pull/9481):** Gantry contraptions occasionally stall or skip blocks at large world coordinates due to float-precision loss in boundary checks.
- **[Create#9675](https://github.com/Creators-of-Create/Create/issues/9675):** Copycat blocks don't render emissive textures (e.g. magma, glowstone) from the copied material.
- **[Create#9414](https://github.com/Creators-of-Create/Create/issues/9414):** Train map overlay in Xaero's World Map is incorrectly scaled on HiDPI displays due to a missing interface scale factor.

### Valkyrien Skies 2 Bugfixes
- **[VS2#418](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/418):** Items duplicate when assembling a ship containing modded block entities with item storage. Fixed by wiping all BlockEntity NBT state and explicitly removing block entities before the block state change.

## Acknowledgments
If you only need Create bugfixes and don't need the other patches this mod provides, check out [create-6-0-8-backported-fixes](https://github.com/MrGazdag/create-6-0-8-backported-fixes) by MrGazdag.