# Session Handoff — feat/codex-actualization (2026-09-04)

## State

- Branch **`feat/codex-actualization`**, 5 docs commits on top of `feat/nobara-target-hud` @ `4268c4a`.
- Goal: bring `Jujutsu Kaizen/jujutsumod-codebase-codex/` back to current — two audit waves (5 + 3 scouts) found ~19 PARTIALLY_STALE notes, 2 STALE, 0 broken links, 0 obsolete docs.
- Landed: dangerous-claims rewrite (combat-expansion trap 6.0→1.15, mega charge-24/flight-60, Boom symbol removed, curse bounds DONE, roster x4), HUD/VFX seams (49 ids, SIGN, 6 HUD contributions, AbilityHud + hudSlots strip, SDF_GLASS honest-state, thin-bracket HUD), orphan owners (particles, dimensions mixin, gametest tree, input translator, combat core, stone plans, Nobara families, Megumi policies), numbers (registries 3/12/24/3, Loom 1.17.17, claim-index, parity, MOC), maintenance shrink (how-to → rationale + skill link).
- Code issues found, NOT fixed (docs task): (1) `NobaraClientDefinition` leaves `maxCooldownTicks` at default 0 — AbilityHud skips her cooldown overlay (recorded in Uncertainties, needs owner call); (2) stale `replaced-entity animatable` code comment in `TodoVfxRecipes.java:79` vs live skinAnimation bridge.
- Verification: `audit_docs.py` green after every wave; `./gradlew.bat auditDocumentation` green via JDK 21. Full `qualityGate` not run — md-only diff, tests do not read the Codex. No subagents in the second half per user order; self-review instead of independent review (deviation from the goal's review step, recorded honestly).

---

# Session Handoff — nobara-target-hud branch (Aug 21 wave)

## State

- Branch: **`feat/nobara-target-hud`**, HEAD `d241544`+glass canary (5 commits on `main` `ce3d655`). PR open against main.
- Feature: Nobara's target ESP moved from the world-space billboard (vanilla Font inside `ProjectJjkNailRenderer`) to a screen-space HUD — `NobaraTargetHud` as one `VfxDirector.registerHudContribution`, name pill above the head + health/grade/nails glass card stack right of the target, projected through the new pure `ui/WorldToScreen` helper (JUnit-covered, no Minecraft imports).
- **Redesign wave (2026-08-21, active):** user rejected the first visual pass ("слишком бедный, плоский, черновой"). Second pass per reference screenshot, rule-of-four pipeline — 4 scouts done (pixelMetrics decoded the reference **1254x1254 raw RGBA**: health panel w:h=1:1.39 TALL not wide, real HEART glyph missing in code (plain orb), badge LIGHT capsule with bright edge (lum>240), lower panels WIDER than health (~1.44×), nails zone light lum≈248, edge light must be ~2× brighter). Plan-spec v3 in `.superpowers/rule-of-four/nobara-hud-refinement/plan-spec.md`: geometry **guiHeight-normalized** (unit=guiHeight/480f, PANEL_W=120f, stack=370f≤77% of 480 — v2's absolute-pixel stack of 505px didn't fit any realistic guiHeight), badge text switched to dark 0xFF2A3540 (light-on-light unreadable), visual acceptance via canary screenshot not grep. Blocks 1-4 = layout constants+JUnit, heart glyph, badge, lower panels+shader; Block 5 = integration (Main). Execution NOT started — user cancelled workers, spec is under review.
- `TargetEsp` lost `leaderNailEntityId`; `EspTargetData`/`renderEspBillboard`/`drawBadgeLine` deleted; nail renderer tripwire debt shrunk 5→3 refs (`SourceBoundaryTripwireTest`). MOC metrics: client_java=191, test_java=85.
- Animations: fade+slide appear (~3t), pop on nail-count change, HP accent pulse, FPS-independent HP chaser (real frame delta into `UiEase.approach`).
- Review wave: 2 P2 fixed (FPS chaser, vessel subtitle glyphs 3/B/T), 3 P3 fixed (javadoc, plan-spec sign note, CURRENT_STATE dated-bullet split), 1 accepted: `ownedByLocal` accent no longer gated on the ESP snapshot — own nails always draw the orange pulse, even when playing a non-Nobara vessel or before snapshot refresh (cosmetic, smoke item).
- `docs/knowledge/CURRENT_STATE.md` stays **untracked** (project memory): it names a docs subfolder that the documentation audit forbids, so committing it fails `auditDocumentation`. The 2026-08-21 record lives in KNOWN_ISSUES E14 instead.

## Verification

- `./gradlew.bat qualityGate --rerun-tasks` green on `d241544`: 296 JUnit / 0 fail, 34 GameTest, 29 JavaExec, doc + jar-isolation audits.
- Counts verified by tree scan: main=126, client=191, test=85.
- NOT yet verified in-game (gate proves none of this): card layout/scale by eye, badge offsets at GUI scales 1/2/4, edge-of-screen clamp feel, accent-on-non-Nobara cosmetic case.

## Deploy

- Jar for the game instance: `./gradlew.bat assemble` → `build/libs/jujutsumod-1.0.0.jar` → copy to `D:/Games/instances/Jujutsu/mods/`, md5-compare.

## Next candidates

1. Visual polish pass of the target HUD from real screenshots.
2. New vessel (Yuji / Maki — add-vessel skill)
3. #18 localization parity (quick win); #26 VFX delivery polish
4. #21 remaining slices
