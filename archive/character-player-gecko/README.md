# Archived Character Player Geo Stack

These files are the former visible Blockbench/GeckoLib player-model path. They are retained for
reference and rollback, but this directory is outside both Gradle source sets and is never packaged
into the mod jar.

The live player presentation now uses the vanilla `PlayerModel` and the character skins under
`src/main/resources/assets/jujutsumod/textures/entity/character/`. GeckoLib remains live as the
animation runtime: the invisible rigs under `geckolib/models/character_skin/` and the existing
animation JSON drive the skin animation bridge.

See `manifest.txt` for the original path of every archived file.
