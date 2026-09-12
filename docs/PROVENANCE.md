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

## Sorcery Age shikigami assets (Nue / Toad / Rabbit Escape / Max Elephant)

- Imported on the `feat/megumi-shikigami` branch from the owner-provided extract
  `sorcery_age_ten_shadows_4_models.zip` (contents pinned to upstream `wood-m-corp/sorcery-age` at
  commit `40a60272b95a6d408a91963ee26ea297ed8fd200`; every one of the 14 source files is verified
  byte-identical to that commit by Git blob SHA-1, see the manifest inside the archive at
  `sorcery_age_shikigami_assets/references/source_manifest.txt`).
- The Sorcery Age author has personally given the jujutsumod developer permission to use these
  assets (project owner statement, same class as the Mythic Mounts entry above). The permission
  evidence is not bundled in the repository; preserve its scope in a durable form or replace the
  assets before a public release.
- Runtime paths under this import, per type (geo identifier `geometry.megumi_<x>`, clips re-keyed
  to `animation.megumi_<x>.*`, full upstream clip set kept):
  `assets/jujutsumod/geckolib/models/megumi_nue.geo.json`,
  `assets/jujutsumod/geckolib/animations/megumi_nue.animation.json`,
  `assets/jujutsumod/textures/entity/megumi_nue.png`,
  `assets/jujutsumod/geckolib/models/megumi_toad.geo.json`,
  `assets/jujutsumod/geckolib/animations/megumi_toad.animation.json`,
  `assets/jujutsumod/textures/entity/megumi_toad.png`,
  `assets/jujutsumod/geckolib/models/megumi_rabbit.geo.json`,
  `assets/jujutsumod/geckolib/animations/megumi_rabbit.animation.json`,
  `assets/jujutsumod/textures/entity/megumi_rabbit.png`,
  `assets/jujutsumod/geckolib/models/megumi_max_elephant.geo.json`,
  `assets/jujutsumod/geckolib/animations/megumi_max_elephant.animation.json`,
  `assets/jujutsumod/textures/entity/megumi_max_elephant.png`.
- Deliberately NOT shipped: `toad_tongue.png` and `toad_wings.png` from the archive's
  `toad/textures/` set — neither is referenced by the imported toad geo, so they stay out of the
  runtime tree. The toad tongue strike is VFX-only (recorded limit, see KNOWN_ISSUES).
- No Sorcery Age sounds were imported (no per-shikigami sounds were found upstream); every
  shikigami reuses vanilla sound placeholders. No Sorcery Age code, mechanics, or data files are
  bundled — the summon logic, brains, and behavior are project code. Do not expand the set
  casually.

## Sons of Sins cursed-spirit assets (lesser / common / greater tiers)

- Imported 2026-09-12 for the cursed-spirits feature (`lesser_cursed_spirit`,
  `cursed_spirit`, `greater_cursed_spirit`): nine body textures and 34 OGG voice lines
  extracted from the Sons of Sins distribution `sons_of_sins-2.2.1d-neoforge-1.21.1.jar`
  (package `net.mcreator.sonsofsins`); no Sons of Sins code, models, animations, mechanics,
  or data files are bundled — the entity, brains, rigs, and behavior are project code.
- The Sons of Sins author has personally given the jujutsumod developer permission to use
  and adapt these assets (project owner statement, 2026-09-12). The permission evidence is
  not bundled in the repository; preserve its scope in a durable form or replace the assets
  before a public release.
- Runtime paths under this import (9 textures + 34 sounds):
  `assets/jujutsumod/textures/entity/cursed/cursed_prowler.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_floating_curse.png` (source `curse.png`),
  `assets/jujutsumod/textures/entity/cursed/cursed_gulber.png` (source `gubler.png` —
  the upstream `gubler` typo names the source file only; the shipped name uses the
  canonical `gulber` spelling and the file bytes are copied verbatim),
  `assets/jujutsumod/textures/entity/cursed/cursed_kelvin.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_butcher.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_guzzler.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_blud.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_walking_bed.png`,
  `assets/jujutsumod/textures/entity/cursed/cursed_wistiver.png`,
  `assets/jujutsumod/sounds/cursed/cursed_prowler_{ambient,hurt,death,scream}.ogg`,
  `assets/jujutsumod/sounds/cursed/cursed_floating_curse_{ambient,hurt,death,scream}.ogg`
  (sources `curse_ambient/hurt/death/scream.ogg`),
  `assets/jujutsumod/sounds/cursed/cursed_gulber_{ambient,hurt,death}.ogg`
  (sources `wight_ambient_1/hurt_1/death_1.ogg`; no scream upstream),
  `assets/jujutsumod/sounds/cursed/cursed_kelvin_{ambient,hurt,death,scream}.ogg`,
  `assets/jujutsumod/sounds/cursed/cursed_butcher_{ambient,hurt,death,scream}.ogg`,
  `assets/jujutsumod/sounds/cursed/cursed_guzzler_{ambient,hurt,death}.ogg`
  (hurt source `guzzler_hurt_1.ogg`; no scream upstream),
  `assets/jujutsumod/sounds/cursed/cursed_blud_{ambient,hurt,death,scream}.ogg`,
  `assets/jujutsumod/sounds/cursed/cursed_walking_bed_{ambient,hurt,death,scream}.ogg`,
  `assets/jujutsumod/sounds/cursed/cursed_wistiver_{ambient,hurt,death,scream}.ogg`
  (scream source `wistiver_scream.ogg`).
  All copies are byte-identical to the extracted sources (no audio/image re-encoding);
  `sounds.json` keys `cursed.<variant>_<channel>` and both lang files carry the three
  entity names plus all 34 subtitles.
- Deliberately NOT shipped: `wistiver_screamer.ogg` (unused spare), the per-variant
  `*_ghost.png` hit-flash overlays, `*_glowing.png`/`walking_bed_eye.png` emissive dots,
  and every other creature's or prop's assets (devourer, grub, nibbler, ditched, organs,
  hats, remnant chest, `invisible.png`, `betrayed_devourer.png`, target-lock states).
  Do not expand the set casually.
- Shipped-file manifest (sha256, verified 2026-09-12):
  `8d1d29fb9d86985c4bf5d690cc91ac280e9f855e4903d54db9c2ba499f7fda8f  assets/jujutsumod/textures/entity/cursed/cursed_blud.png`,
  `cee4b5ef8feddbadc39dbfcdacfedac94bf274be49e034f9c5e45f5d609358a1  assets/jujutsumod/textures/entity/cursed/cursed_butcher.png`,
  `8bdea1548f7dfd5247981580ae9b29ad764a397dbfb23202be7a7e4717a6f39a  assets/jujutsumod/textures/entity/cursed/cursed_floating_curse.png`,
  `e875105ff1f4130c209c62c0984324b731a6b9d374602c9fbf2e7c763aee5f19  assets/jujutsumod/textures/entity/cursed/cursed_gulber.png`,
  `a865983634ebc2dc666ef693684246e8b76196e0e593174f8fb00bd3cce835d4  assets/jujutsumod/textures/entity/cursed/cursed_guzzler.png`,
  `c22393cdd95ba878f1531a2fc4ec190fcab0bb22dc9f6ee82c839b2ec8b90829  assets/jujutsumod/textures/entity/cursed/cursed_kelvin.png`,
  `5542cfccb7589b8e94a8d7745a00c158ba7c3c6bf71259305e19dde63c2049f1  assets/jujutsumod/textures/entity/cursed/cursed_prowler.png`,
  `ed7c4a1976f3f135fa5e4a3e470a3f42d12acffbc296c1a2cb20627b9419e3dd  assets/jujutsumod/textures/entity/cursed/cursed_walking_bed.png`,
  `7374f2c9a3932588494f0e4c61344021e2ba7f0aeb2bf523903d0b00af36c52f  assets/jujutsumod/textures/entity/cursed/cursed_wistiver.png`,
  `db02cbd9fcc7027ddb360ff4e48b2dababea55ae7a58ac19bf52d612b0270eff  assets/jujutsumod/sounds/cursed/cursed_blud_ambient.ogg`,
  `0b63e6a6a07cee0531da3a83f6b2b8e5a015772dfeb2413f2e6e84c882699739  assets/jujutsumod/sounds/cursed/cursed_blud_death.ogg`,
  `abef8032aa324583226bac3d8178ea30cd41a14c7711591099b2c897e9771ec4  assets/jujutsumod/sounds/cursed/cursed_blud_hurt.ogg`,
  `db698a010b3e21a4d715bb049f3632bdb5274702d77d2939e8983f4a67bc8da6  assets/jujutsumod/sounds/cursed/cursed_blud_scream.ogg`,
  `d5b66c042998ebf184498b07341f4881b21fdd51812252bfc085379cc35e523b  assets/jujutsumod/sounds/cursed/cursed_butcher_ambient.ogg`,
  `fef723c45054bbd42a0802a9ca4cac7b0aa7d1953741d7f4c92049b113dc860d  assets/jujutsumod/sounds/cursed/cursed_butcher_death.ogg`,
  `2486f1035ddf3990bf5fb350e69d5cc0ba4bb120a6a483f520214c73de8542a9  assets/jujutsumod/sounds/cursed/cursed_butcher_hurt.ogg`,
  `aefe037680198fb8bfba0032e66c91446fd4adaaa9e26687b428150af9a500ed  assets/jujutsumod/sounds/cursed/cursed_butcher_scream.ogg`,
  `f18b44c035c86f1694306039de713208e48e8560e8240f06cdf036420302108b  assets/jujutsumod/sounds/cursed/cursed_floating_curse_ambient.ogg`,
  `1b0fc44d72ec45ecb9f3c6ede20494c90f570d0644fa4fa00fd08932d23013f5  assets/jujutsumod/sounds/cursed/cursed_floating_curse_death.ogg`,
  `210ed4b03b0ec20025fddae361238f73b74b05b5dc4cd8603330b4b674e578e0  assets/jujutsumod/sounds/cursed/cursed_floating_curse_hurt.ogg`,
  `4bc345f18212f166e6e1323a885b4c0be38601009fccaf755b258c660d3eb51a  assets/jujutsumod/sounds/cursed/cursed_floating_curse_scream.ogg`,
  `e5624442cdeea8cf4bae4149e5c370a0e68b58ef67e81b3a2b103ac7fecd625f  assets/jujutsumod/sounds/cursed/cursed_gulber_ambient.ogg`,
  `47d88069982ca01023c65895a02b62151c0c1493aab5cc51598e59f489e53156  assets/jujutsumod/sounds/cursed/cursed_gulber_death.ogg`,
  `34cb5b9c11a8b15cbd4d025f9e5c626e38cea902427ab8cc363487bf3edd0c4a  assets/jujutsumod/sounds/cursed/cursed_gulber_hurt.ogg`,
  `0b940597cb64309e0424394996e92a26ab56bb5429acdbef17ccc20397491396  assets/jujutsumod/sounds/cursed/cursed_guzzler_ambient.ogg`,
  `2817ac86fab11ced39443f3ad6d8a0d5a7ac8fbe8f5988c46903f7c1cfdf  assets/jujutsumod/sounds/cursed/cursed_guzzler_death.ogg`,
  `78e4683ce5802b7affc1d9210a6d2e704d79f13f3df7c474b5b73617f4392123  assets/jujutsumod/sounds/cursed/cursed_guzzler_hurt.ogg`,
  `21a13e72527d0cc0edd2bf34fb3a0e07a75ccacb0656b41d46835876d5359723  assets/jujutsumod/sounds/cursed/cursed_kelvin_ambient.ogg`,
  `4c26a275e9985451bb9222c6cc5054153ebc15654f9368be1ef6c0459386c585  assets/jujutsumod/sounds/cursed/cursed_kelvin_death.ogg`,
  `298098716825ba580e0a745c7e2960756fdedbe431513b90ee7eca8ae6ff8caa  assets/jujutsumod/sounds/cursed/cursed_kelvin_hurt.ogg`,
  `86a40e90b978d8bbad0970a4e7d604a4c2209ce91d091ad100f49a973bf20b87  assets/jujutsumod/sounds/cursed/cursed_kelvin_scream.ogg`,
  `4b8b0b64c30b9dc56a7affe67887c9b2406e3cd5c49cc0696f6936cf09c5d96c  assets/jujutsumod/sounds/cursed/cursed_prowler_ambient.ogg`,
  `202c928d0e872512f4eba59cfdb59bd13149a47682bc53925a72981947493b65  assets/jujutsumod/sounds/cursed/cursed_prowler_death.ogg`,
  `b0f0984bac79d03a1d5438049c0d775581cd9c8fe3ad86aa227e15ba15089d8a  assets/jujutsumod/sounds/cursed/cursed_prowler_hurt.ogg`,
  `c6e1fe658273fc4ce530836e95fe6d8871071c3b61505d50351e71182bf1e8dd  assets/jujutsumod/sounds/cursed/cursed_prowler_scream.ogg`,
  `6b3789dc3a3ec8804ea7402dd1a530d4a10e7fc34b37b92b6ffb28a11ee03f30  assets/jujutsumod/sounds/cursed/cursed_walking_bed_ambient.ogg`,
  `25cb4642813f11ff4f45e7328ae859fc0398ddca7fb042181977342b08f45172  assets/jujutsumod/sounds/cursed/cursed_walking_bed_death.ogg`,
  `994d7f618dbbc8781d33dd4b524e6b8eb5ebc71d32ca7d7dd5036a086795c131  assets/jujutsumod/sounds/cursed/cursed_walking_bed_hurt.ogg`,
  `b871359fa4c48efb5ec16c3da0dd2a36732adb27de576aa6370588367b7e2b8d  assets/jujutsumod/sounds/cursed/cursed_walking_bed_scream.ogg`,
  `002e69bc33fb7638e7555d03d46cad1deaa2318ba3dcb2325dcf786fe7b59be4  assets/jujutsumod/sounds/cursed/cursed_wistiver_ambient.ogg`,
  `a1e6db184158593d5bfe97b64d1fa377576b7f1641dab2ca1cee35d52746238c  assets/jujutsumod/sounds/cursed/cursed_wistiver_death.ogg`,
  `34f262a7523f38dbdbe5963f585d216e61a3380aabed6faba1c9fd5714e3d225  assets/jujutsumod/sounds/cursed/cursed_wistiver_hurt.ogg`,
  `8352535a99f03e1319b15aa6f782de69fef6007e2983eb5658b0dc92af1329fb  assets/jujutsumod/sounds/cursed/cursed_wistiver_scream.ogg`.
