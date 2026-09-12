# Review Block 1 report — assets, resources, provenance (ReviewBlock1)

Scope: `src/main/resources/assets/jujutsumod/{textures/entity/cursed/**,sounds/cursed/**,sounds.json,lang/{en_us,ru_ru}.json}`,
`docs/PROVENANCE.md`, `docs/THIRD_PARTY_NOTICES.md`,
`src/test/java/jujutsu/mod/cursedspirit/CursedSpiritResourceContractTest.java`.
Requirements: R2 (asset half), R22 (paths), R23 (data half), R28 (provenance half).
Branch `feat/cursed-spirits`. Read-only review; no project files edited (this report is the only write).
Note: assignment text cites commit `fe0a07`; the actual provenance commit is `fe60a07` (verified via `git log`).

## Evidence log (all re-verified, not trusted)

- E1 — Commits match plan: `git log --oneline main..HEAD` shows `9289491 feat(cursed-spirits): import
  Sons of Sins textures, sounds, lang` (47 files: 9 png + 34 ogg + sounds.json + 2 lang + contract test)
  and `fe60a07 feat(cursed-spirits): record Sons of Sins asset provenance` (2 docs files). No other
  Block-1 files touched by later commits (`git diff main...HEAD --stat` shows Block-1 paths only in those two).
- E2 — Exact roster, no extras: `ls textures/entity/cursed/` = 9 files
  (`cursed_{prowler,floating_curse,gulber,kelvin,butcher,guzzler,blud,walking_bed,wistiver}.png`);
  `ls sounds/cursed/` = 34 files (4x7 scream variants + 3x2 for gulber/guzzler, exactly the frozen
  34-channel table). `grep -iE 'screamer|ghost|glow|eye'` on both shipped dirs exits 1 (absent).
  Raw extract holds `wistiver_screamer.ogg` (present upstream, deliberately unshipped) plus
  `*_ghost.png` / `*_glowing.png` / `walking_bed_eye.png` / devourer/grub/nibbler/ditched/props —
  none of them shipped.
- E3 — Byte identity vs raw extract (python, full-file compare, all 43 pairs):
  9/9 textures MATCH (e.g. prowler 2423 B `5542cfccb758…`, walking_bed 4850 B `ed7c4a1976f3…`);
  34/34 sounds MATCH (e.g. floating_curse_ambient 40133 B, floating_curse_hurt 7423 B) with the
  frozen source renames (`curse_*`→`floating_curse_*`, `wight_*_1`→`gulber_*`, `guzzler_hurt_1`→guzzler_hurt,
  `gubler.png`→`cursed_gulber.png` bytes verbatim, `wistiver_scream` — not `screamer` — as scream source).
- E4 — `sounds.json`: `git show main:…sounds.json` = 26 keys, HEAD = 60 keys (+34, all `cursed.*`);
  removed = NONE, disturbed pre-existing = NONE (pure tail append, `git diff main...9289491` shows only
  `+` lines after the last pre-existing entry). All 34 entries are single-file arrays
  `jujutsumod:cursed/cursed_<key>` + `subtitle: subtitles.jujutsumod.cursed.<key>` (parsed JSON check).
  Trailing-newline shape preserved (absent before and after — matches repo convention).
- E5 — Lang: both files parse as JSON (195 keys each); full key-set diff en↔ru = NONE;
  3 entity keys each (`Lesser Cursed Spirit`/`Мелкое проклятие`, `Cursed Spirit`/`Проклятие`,
  `Greater Cursed Spirit`/`Великое проклятие`) + 34/34 cursed subtitles each, non-blank.
  No repo-wide en/ru key-parity test exists (only spot checks: `ProjectSanityTest.java:1163`,
  `MegumiDireWolfSoundContractTest.java:59-60`); parity was verified manually here plus by the contract test.
- E6 — Pixel/audio reality: IHDR parse → 8×`64x64` + `walking_bed` `128x128` (frozen sheet sizes);
  all 34 ogg start `OggS`, sizes 7423–40133 B (min floating_curse_hurt, max floating_curse_ambient), all > 1 KB.
- E7 — Contract test run (this review):
  `gradlew.bat test --tests "jujutsu.mod.cursedspirit.CursedSpiritResourceContractTest"` → BUILD SUCCESSFUL (9 s);
  `TEST-…CursedSpiritResourceContractTest.xml`: tests 5, failures 0, errors 0, skipped 0 — all 5 PASS.
- E8 — Test is real, not theatre (read every assert, `CursedSpiritResourceContractTest.java:65-143`):
  dims via `ImageIO.read` width/height (real pixel data); ogg via 4-byte magic + size (real bytes);
  sounds.json via exact subtitle + exact first-path + file-exists (red-proof (a) mutation would fail line 100);
  lang via presence + non-blank in BOTH files; distinctness via MD5 over full shipped file bytes with
  `MessageDigest.digest(bytes)` per-variant reset (correct; red-proof (b) failure message quoted in the
  worker report matches line 138-139 semantics). Worker's three red-proofs (a/b/c) cite exact failing
  asserts and messages consistent with the code I read; not re-run (read-only, would dirty the tree).
- E9 — Provenance docs: `PROVENANCE.md` Sons-of-Sins section records source jar
  `sons_of_sins-2.2.1d-neoforge-1.21.1.jar`, owner permission statement 2026-09-12, shipped paths
  (incl. `gubler` typo note), exclusions (`wistiver_screamer.ogg`, ghost/glow/eye sheets, other
  creatures/props), and a do-not-expand rule; `THIRD_PARTY_NOTICES.md` carries the matching entry
  with the same exclusions + permission scope. BUT see F1: 2 of 43 manifest hashes are mistyped.

## Findings

### F1 (P2) — Two sha256 manifest lines in PROVENANCE.md do not match the shipped files
What breaks: the manifest is the audit evidence for "byte-identical, no re-encoding". Anyone verifying
it (`sha256sum` vs the doc) gets a mismatch on 2 of 43 files and cannot tell a typo from a tampered asset.
The asset bytes themselves are CORRECT (E3: both files byte-identical to the raw extract) — docs-only defect.
- `docs/PROVENANCE.md:128`: `cursed_guzzler_death.ogg` hash is 60 hex chars (truncated, drops `b8ac`):
  recorded `2817ac86…a0d5a7ac8fbe8f59…`, actual
  `2817ac86fab11ced39443f3ad6d8a0d5a7acb8ac8fbe8f5988c46903f7c1cfdf`.
- `docs/PROVENANCE.md:141`: `cursed_walking_bed_scream.ogg` hash is 64 chars but mistyped
  (first diff at index 12, tail differs): recorded
  `b871359fa4c48efb5ec16c3da0dd2a36732adb27de576aa6370588367b7e2b8d`, actual
  `b871359fa4c4efb5ec16c3da0dd2a36732adb27de576aa6370588367b7e2b8d1`.
Counter-proposal (docs-only, replace the two hash tokens with the actual values above; file bytes need no change):
```suggestion
  `2817ac86fab11ced39443f3ad6d8a0d5a7acb8ac8fbe8f5988c46903f7c1cfdf  assets/jujutsumod/sounds/cursed/cursed_guzzler_death.ogg`,
```
```suggestion
  `b871359fa4c4efb5ec16c3da0dd2a36732adb27de576aa6370588367b7e2b8d1  assets/jujutsumod/sounds/cursed/cursed_walking_bed_scream.ogg`,
```
Confidence: 1.0 (byte-level diff of doc token vs `sha256` of file, both directions checked).

### F2 (P3) — Contract test pins content but not the manifest or absence-of-extras
What breaks (minor): nothing today — E2/E3 cover it — but the exact defect class F1 (wrong recorded hash)
and a hypothetical stray file in either shipped dir both pass the current 5 asserts: the test never reads
`PROVENANCE.md` hashes and never asserts the dirs contain ONLY the 9+34 files (`TEXTURE_SIZES`/`SOUND_KEYS`
drive existence checks, not directory listings). A future mistype or stray keeps the suite green.
Counter-proposal (hardening, optional): add one assert listing each dir and comparing the filename sets to
the frozen literals, and one assert recomputing sha256 of the 43 files against manifest literals.
Confidence: 0.7 (gap is real; whether to spend the test budget here is a taste call — the plan froze the
test shape, so this is a suggestion, not a demand).

## Requirement verdicts

- R2 (asset half: distinct model+texture presentation data) → PASS. E2 (exact 9 names) + E3 (9/9 byte-identical)
  + E6 (frozen dims) + E7/E8 (pairwise-non-identical MD5 + decode asserts green 5/5). Visual eyeball
  distinctness is live-lane work owned by Block 5 (accepted limit, not this block).
- R22 (correct texture per variant, no missing-texture magenta) → PASS. E2+E3+E6: every frozen variant has
  its file, bytes verbatim upstream, dims equal the LayerDefinition sheet sizes; no extras, no ghost/glow
  substitutes. In-world magenta check is Main's live probe (out of scope for files review).
- R23 (data half: sounds fire-able, placeholders recorded) → PASS. E3 (34/34 byte-identical, OggS, >1 KB) +
  E4 (34 keys, subtitles, resolvable paths, append-only) + E5 (34 subtitles ×2 langs). Coherence/manual-listen
  half is Block 5's manual lane by plan.
- R28 (provenance half) → FAIL (narrowly, docs-only). Permission statement, shipped-path list, exclusion list
  (`wistiver_screamer`, ghost/glow/eye, other creatures/props) and do-not-expand rule are all present and
  accurate (E9, E2) — but F1 makes 2 of 43 manifest hashes factually wrong, so the provenance record as
  shipped does not verify. Fix = two hash tokens (counter-proposals above); no asset or code change needed.

## Scope verdict: GO (with one docs-only fix required before merge)

Runtime assets are exactly the frozen table (9+34, byte-identical, dims/magic/sizes verified), `sounds.json`
is a clean append (+34, zero disturbed), both lang files are valid with full 195/195 key parity, and the
contract test is genuine (5/5 green, asserts read real bytes, red-proof messages consistent with the code).
The single blocker-class item is F1 (two mistyped manifest hashes) — a two-token docs fix with zero runtime
impact; F2 is optional hardening. No manual in-game checks fall on Block 1: sound coherence, animation feel,
variant eyeball distinctness, and hitbox visual fit are Block 5/Main live-lane items per the accepted limits.
