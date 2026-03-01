# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - TBD
### Added
- Mixin plugin system for conditional mixin loading per mod
- Separate mixin config for Create (`riggyz_patches.create.mixins.json`)
- Separate mixin config for VS2 (`riggyz_patches.vs2.mixins.json`)
- Create is now an optional dependency
- VS2 as an optional dependency

### Fixed
- [Create#6902](https://github.com/Creators-of-Create/Create/issues/6902): Contraption collision lag from VoxelShape merging
- [VS2#418](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/issues/418): Item duplication when assembling ships with active block entity inventories (e.g. Create mixers, copycats, modded storage). Backport of [VS2 PR #1688](https://github.com/ValkyrienSkies/Valkyrien-Skies-2/pull/1688).

### Changed
- Changed the readme to relfect the new name and purpose of the mod

## [0.1.0] - TBD
### Fixed
- [Create#9511](https://github.com/Creators-of-Create/Create/issues/9511): Schematicannon duplication bug

[unreleased]: https://github.com/riggyz/riggyz-patches/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/riggyz/riggyz-patches/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/riggyz/riggyz-patches/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/riggyz/riggyz-patches/releases/tag/v0.1.0