# Create Patched

A Forge 1.20.1 mod that backports two critical bugfixes from [Create 6.0.9+](https://github.com/Creators-of-Create/Create) to the last Forge 1.20.1 release of Create (`0.5.1.f`).

## Bugfixes

### 1 — Schematicannon NBT / Item Duplication (Placard Issue)

**Problem:** The schematicannon in Create 0.5.1f does not respect NBT data on blocks tagged with
`SAFE_NBT` (e.g. Placards).  Because the `ItemRequirement` for such blocks ignores NBT, the
cannon matches *any* copy of the item rather than the specific NBT-bearing one, leading to item
duplication or incorrect item consumption in survival mode.

**Fix:** Upstream commit `b91757a` in Creators-of-Create/Create.  This mod's
`SchematicPrinterMixin` injects into `ItemRequirement.of()` and, when the block is tagged
`create:safe_nbt`, attaches the block-entity NBT to the required `ItemStack` so that the cannon
performs strict NBT matching.

### 2 — Train / Contraption Collision Lag (Station Lag)

**Problem:** Whenever a contraption's collision shape is rebuilt (e.g. a train entering a station),
Create 0.5.1f calls `Shapes.or()` for every block in the contraption.  This boolean shape-union
operation is O(n²) and causes multi-second lag spikes for large trains.

**Fix:** Upstream work by Jozufozu in Creators-of-Create/Create replaces the shape-union with a
flat `List<AABB>` that is iterated directly.  This mod's `ContraptionCollisionMixin` redirects
every `Shapes.or()` call inside `Contraption` to return the individual shape unchanged, eliminating
the quadratic rebuild cost.

## Requirements

| Dependency | Version |
|------------|---------|
| Minecraft  | 1.20.1  |
| Forge      | 47.3.0+ |
| Create     | 0.5.1.f |

## Building

```bash
./gradlew build
```

The output jar is placed in `build/libs/`.

## License

LGPL-3.0-or-later