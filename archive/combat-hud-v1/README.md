# archive/combat-hud-v1 — combat HUD (archived 2026-09-09)

Status: ARCHIVED 2026-09-09 — removed from the active build by request.

What was archived: the in-world combat HUD shown in the 2026-08-21 build (jar
5d95a0b): the bottom-center ability strip above the hotbar, the target panel
right of the target (health / grade / nails cards), and the target-name
label above the target. Everything is disabled in the active game; nothing
was redesigned or deleted for good.

## Layout

    src/client/java/jujutsu/mod/client/hud/AbilityHud.java
        The bottom-center ability strip (one 20px SDF cell per technique slot,
        key labels, cooldown overlay, drag via GLFW polling). Registered from
        JujutsuModClient as the "ability_hud" VfxDirector contribution; the
        tick hook (END_CLIENT_TICK -> tickDrag) lived in its static block.

    src/client/java/jujutsu/mod/client/character/nobara/
        NobaraTargetHud.java      one VfxDirector contribution per frame
        NobaraTargetLayout.java   pure block geometry, HP-segment math, rank text
        NobaraTargetAnim.java     pure appear/pop/HP-chase easing (UiEase)
        NobaraEspState.java       per-2-tick client scan of embedded nails (register())
        NobaraEspRanks.java       deterministic grade classification (Special/1/2/3/Civilian)

    src/test/java/jujutsu/mod/client/character/nobara/
        NobaraTargetLayoutTest.java   geometry contract (also covers NobaraTargetAnim)
        NobaraEspStateTest.java       aggregation contract
        NobaraEspRanksTest.java       classification boundaries

    snapshot-glass-5d95a0b-2026-08-21/
        The three files as they were in the jar the game ran on 2026-08-21
        (glass-card look: name pill above the target head + glassy cards).
        Kept verbatim from commit 5d95a0b so the exact old look can be
        resurrected without git archaeology. The other three classes were
        identical in that build, so this snapshot only holds the three diffs.
        Note: restoring the glass look may need reconciliation with the
        current SDF/MSDF pipelines.

## What stayed in the active build

- Abilities and their input (R / S+R / B / S+B / LMB etc.), keybinds,
  cooldown suppression (ClientAbilityCooldowns) — untouched.
- VfxDirector + its single HUD element (megumi_divine_dogs_cooldown,
  megumi_shadow_dive_veil, todo_stone_status, todo_pair_status remain).
- hudSlots() / maxCooldownTicks() on CharacterClientDefinition and the
  per-vessel implementations (the data seam stays; only the strip renderer
  was archived).
- WorldToScreen / UiEase / SdfRenderer / MsdfFonts shared helpers.
- Asset textures (hud icons, nail_icon.png) and the esp.jujutsumod.rank.*
  lang keys: inert data left in place deliberately for a clean restore.

## Registration points removed (restore = put back)

1. src/client/java/jujutsu/mod/client/JujutsuModClient.java
   - import jujutsu.mod.client.hud.AbilityHud;
   - VfxDirector.registerHudContribution(JujutsuMod.id("ability_hud"), AbilityHud::render);
2. src/client/java/jujutsu/mod/client/character/nobara/NobaraClientDefinition.java
   - NobaraEspState.register();
   - VfxDirector.registerHudContribution(JujutsuMod.id("nobara_target_hud"), NobaraTargetHud::render);
   (the crash-avoidance comment above the latter — method reference defers
   the class init until the first frame — must come back with it)

## How to restore

1. git mv the files back from this folder into src/client/java and
   src/test/java at their original package paths.
2. Re-add the two registration lines listed above.
3. Run ./gradlew qualityGate; then update the metric rows in
   Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md (Client Java files +6,
   Test Java files +3) and revert the doc edits that this archival made.
4. Rebuild the jar and deploy it to the game instance.
