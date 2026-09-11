# Imported Asset Provenance Notes

Status: CURRENT PROVENANCE NOTE

- Imported content is limited to assets, data, and manifests; no ProjectJJK Java class files or dependency jars are bundled.
- Upstream ProjectJJK materials are All Rights Reserved and are not relicensed as CC0 by this repository.
- The ProjectJJK author has given the jujutsumod developer permission to use the current models/assets as temporary private-development placeholders.
- The placeholders are intended to be replaced with original assets when feasible.
- Do not expand the imported set casually. Before public redistribution, preserve the permission scope in a durable form or replace the remaining imported assets.
- Current implementation truth lives in the repository code, current docs, and the permission/replacement policy in this file.

## Megumi player assets

- The Megumi Blockbench model, animation set, and 128 x 128 model texture were delivered locally by the project owner as project-specific work. Authorship and redistribution evidence are not bundled in the repository.
- The separate 64 x 64 skin used for first-person hands and the roster was also supplied locally by the project owner; its original source and license were not independently verified.
- Treat both inputs as private-development assets. Record their redistribution permission or replace them before a public release.

## Mythic Mounts Dire Wolf assets (Divine Dogs visual)

- Imported 2026-09-11 to replace the Divine Dogs' vanilla-wolf presentation with the Dire Wolf look and voice: the GeckoLib geometry, the animation set, two of the four Dire Wolf textures, five OGG sounds, and the filtered sound-variant declaration. The owner-provided archive extracts `mythic-mounts-1.20.1-7.4.jar`; no Mythic Mounts code, mechanics, mounts, taming, inventory, armor textures, or remaining variants are bundled.
- The Mythic Mounts author has personally given the jujutsumod developer permission to use these assets (project owner statement, 2026-09-11). The permission evidence is not bundled in the repository; preserve its scope in a durable form or replace the assets before a public release.
- Runtime paths under this import: `assets/jujutsumod/geckolib/models/megumi_divine_dog.geo.json`, `assets/jujutsumod/geckolib/animations/megumi_divine_dog.animation.json`, `assets/jujutsumod/textures/entity/megumi_divine_dog_white.png`, `assets/jujutsumod/textures/entity/megumi_divine_dog_black.png`, `assets/jujutsumod/sounds/megumi/dog_ambient1..4.ogg`, `assets/jujutsumod/sounds/megumi/dog_growl.ogg`, and `data/jujutsumod/wolf_sound_variant/dire_wolf.json`.
- Do not expand the set casually. The Megumi summon logic, dog AI, and behavior are project code and are untouched by this import.
