# Riggyz's Patches
A small Forge mod for that fixes some bugs that affect the survival player experience.

### Bugfixes
- **[Create#9511](https://github.com/Creators-of-Create/Create/issues/9511):** The schematicannon reads the wrong block entity when calculating item requirements, allowing item duplication in survival.
- **[Create#6902](https://github.com/Creators-of-Create/Create/issues/6902):** Large contraptions cause severe lag spikes due to a VoxelShape merging pass when recalculating physics shapes. Replaced with direct per-block AABB decomposition.
- **[VS2#418](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/418):** Items duplicate when assembling a ship containing modded block entities with item storage. Fixed by wiping all BlockEntity NBT state and explicitly removing block entities before the block state change. 

## Acknowledgments
If you only need Create bugfixes and don't need the other patches this mod provides, check out [create-6-0-8-backported-fixes](https://github.com/MrGazdag/create-6-0-8-backported-fixes) by MrGazdag.