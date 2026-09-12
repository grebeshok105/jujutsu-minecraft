# Block 1 report — Asset import: textures, sounds, lang, provenance

## What was done

Imported the frozen per-variant asset table (plan Task 1) from the owner-provided
Sons of Sins extract at
`.superpowers/rule-of-four/cursed-spirits/assets/raw/assets/sons_of_sins/`:

- 9 textures copied + renamed (`PROWLER→prowler.png`, `FLOATING_CURSE→curse.png`,
  `GULBER→gubler.png` upstream typo, source bytes kept verbatim, `KELVIN→kelvin.png`,
  `BUTCHER→butcher.png`, `GUZZLER→guzzler.png`, `BLUD→blud.png`,
  `WALKING_BED→walking_bed.png`, `WISTIVER→wistiver.png`) into
  `src/main/resources/assets/jujutsumod/textures/entity/cursed/cursed_<variant>.png`.
- 34 sounds copied + renamed per the frozen channel table (`GULBER→wight_ambient_1/
  wight_hurt_1/wight_death_1`, no scream; `FLOATING_CURSE→curse_*`; `GUZZLER` hurt
  from `guzzler_hurt_1`, no scream; `WISTIVER` scream from `wistiver_scream`) into
  `src/main/resources/assets/jujutsumod/sounds/cursed/cursed_<variant>_<channel>.ogg`.
  All 43 copies verified byte-identical to sources (no re-encoding).
- Appended 34 `cursed.<variant>_<channel>` entries to `sounds.json` in the repo's
  existing shape (single-file `sounds` array + `subtitle`), append-only.
- Appended 3 entity names (`Lesser Cursed Spirit` / `Мелкое проклятие`,
  `Cursed Spirit` / `Проклятие`, `Greater Cursed Spirit` / `Великое проклятие`)
  + 34 subtitles to BOTH `en_us.json` and `ru_ru.json`, append-only, full parity.
- Created `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritResourceContractTest.java`
  (5 tests, frozen tables as literals).
- Appended the cursed-spirits provenance section (source jar
  `sons_of_sins-2.2.1d-neoforge-1.21.1.jar`, Sons of Sins author credit, permission
  statement 2026-09-12, shipped paths, exclusions, inline sha256 manifest of all
  43 files, do-not-expand rule) to `docs/PROVENANCE.md` + matching entry in
  `docs/THIRD_PARTY_NOTICES.md`.
- Commits on `feat/cursed-spirits` (no push): `9289491 feat(cursed-spirits):
  import Sons of Sins textures, sounds, lang`, `fe60a07 feat(cursed-spirits):
  record Sons of Sins asset provenance`.

## File list (counts by type)

- 9 × `.png` (new): `src/main/resources/assets/jujutsumod/textures/entity/cursed/`
- 34 × `.ogg` (new): `src/main/resources/assets/jujutsumod/sounds/cursed/`
- 1 × `sounds.json` (modified, +204 lines, append-only)
- 2 × lang JSON (modified, +37 keys each, append-only)
- 2 × docs (modified): `docs/PROVENANCE.md` (+89), `docs/THIRD_PARTY_NOTICES.md` (+16)
- 1 × test (new): `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritResourceContractTest.java`

## Acceptance (Факт lines)

- Факт: `export JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current && ./gradlew.bat test --tests "jujutsu.mod.cursedspirit.CursedSpiritResourceContractTest" --no-daemon --max-workers=1 --no-watch-fs` → `BUILD SUCCESSFUL in 10s`; report XML `tests: 5 failures: 0 errors: 0 skipped: 0` (5/5: textures/dims, ogg magic/size, sounds.json keys, lang keys, distinctness).
- Факт: raw PNG header probe (struct IHDR) → 8 variants `64x64`, `walking_bed` `128x128`, exactly the frozen sheet sizes; all 34 source ogg start with `OggS`, sizes 7423–40133 B (all > 1 KB); every frozen source file present, zero missing/ambiguous.
- Факт: `wistiver_screamer.ogg` exists in the raw extract and was NOT shipped; no `*_ghost.png`, `*_glowing.png`, `walking_bed_eye.png`, or other creature/prop assets shipped — `ls` of both target dirs shows only the 9 + 34 frozen files.
- Факт: `git status --short` after commits → only owned files new/modified (plus pre-existing untracked work dirs `.superpowers/`, `.tmp-javap*/`, `.factorypath`, untouched); `git diff --stat` pre-commit showed exactly the 5 owned modified files.

## Red-proof records (each observed failing, then restored to green)

- (a) sounds.json missing-path mutation (`cursed_gulber_ambient` →
  `jujutsumod:cursed/cursed_gulber_ambient_missing`) → `BUILD FAILED`; test XML
  `tests: 5 failures: 1`: `soundsJsonKeysCarrySubtitlesAndResolveToShippedFiles() ::
  org.opentest4j.AssertionFailedError: sound path mismatch: cursed.gulber_ambient
  ==> expected: <jujutsumod:cursed/cursed_gulber_ambient> but was:
  <jujutsumod:cursed/cursed_gulber_ambient_missing>`. Restored from backup; green.
- (b) `cursed_prowler.png` bytes copied over `cursed_gulber.png` → `BUILD FAILED`;
  test XML `tests: 5 failures: 1`: `textureSheetsArePairwiseNonIdentical() ::
  org.opentest4j.AssertionFailedError: duplicate texture sheets among:
  {..., gulber=85f6eedaf11e790d3706a95507317e22, ...,
  prowler=85f6eedaf11e790d3706a95507317e22} ==> expected: <8> but was: <9>`.
  Restored from backup (re-verified byte-identical to `gubler.png`); green.
- (c) scratch dimension-assert corruption (test literal for `blud` set to `32x32`)
  → `BUILD FAILED`; test XML `tests: 5 failures: 1`:
  `texturesExistDecodeAsPngAndMatchFrozenDimensions() ::
  org.opentest4j.AssertionFailedError: width mismatch:
  src\main\resources\assets\jujutsumod\textures\entity\cursed\cursed_blud.png
  ==> expected: <32> but was: <64>`. Restored to `64x64`; final run green 5/5.

## Risks / unresolved

- GULBER upstream typo: source file really is `gubler.png` (renderer-side typo in the
  pack confirmed by scout-3; bytes copied verbatim, shipped name uses canonical
  `gulber`). Block 3 must not look for a `gubler` id — variant id stays `gulber`.
  Nothing substituted; reported, not hidden.
- No sound channel was missing or ambiguous: all 34 frozen sources exist upstream
  (listed sizes above); no silent substitution was made anywhere.
- PNG pixel dims equal the LayerDefinition sheet sizes for all 9 variants, so the
  test pins pixel dims directly — no surprise.
- qualityGate NOT run (per dispatch rules — Main runs it at the barrier); only the
  focused contract test was executed.
- Scope-Growth: none triggered. No Java files touched; `JujutsuSounds` rows remain
  Block 2's work.
