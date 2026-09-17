# Megumi Shikigami Quick Selector — DESIGN SPEC

Approved design contract for GitHub issue #109. This document defines **what the selector must feel like and how it behaves for the player**. Implementation details belong to the implementation plan and may not silently change these decisions.

## Problem

Megumi can have enough shikigami that one-by-one scrolling becomes annoying in combat. The player starts memorising list positions and spending several presses just to reach the intended shikigami.

The selector must make two common actions fast:

1. move to the next usable shikigami without opening UI;
2. deliberately pick any specific shikigami from a visual selector.

This is a combat control, not a normal inventory-style GUI. The world keeps running while it is open.

## Core interaction

One selector key has two behaviours.

### Short press — quick cycle

- Press and release before the hold threshold: select the **next available shikigami**.
- The cycle follows the same fixed canonical order used by the visual strip.
- Unavailable entries are skipped.
- Selection is resolved on release, not immediately on key-down. Starting a hold must never accidentally advance the selection first.

### Hold — visual selector

- Hold threshold: approximately **200 ms**.
- Reaching the threshold opens the visual selector instead of performing the short-press cycle.
- Releasing the selector key after the strip has opened closes it and never triggers the short-press action.
- If the player opens the selector and releases it without making a valid selection, the previous active shikigami remains selected.
- There is no second cancel gesture: releasing the original selector key is the normal and only selector-close action defined by this spec.

## Layout

The selector is a **horizontal strip near the bottom-centre of the screen, above the hotbar**.

- Each shikigami gets a stable slot.
- Expected practical count is roughly **7–8 shikigami**, while the layout must remain valid up to about **10**.
- Every slot always shows both:
  - the shikigami's **3D model**;
  - its **name**.
- Slot order and slot positions are fixed. An unavailable shikigami stays in its normal position; the strip never compacts or shifts neighbouring entries around it.
- Fixed positions are intentional: repeated use should build spatial/muscle memory.

This design explicitly replaces the earlier radial-menu candidate from #109. It is not a radial, cross, grid, or dynamic list.

## Mouse and combat behaviour while open

Opening the strip switches the mouse from normal camera-look to selector interaction.

- The cursor appears at the **centre of the screen** when the selector opens.
- The world continues at full speed: no pause, slow-motion, or time manipulation.
- Camera-look is suspended while the cursor is controlling the selector.
- Left click belongs to the selector while it is open and must **not** perform the normal attack action.
- Other gameplay continues normally unless an existing game rule already prevents it; the selector must not introduce a general combat pause.

## Selection semantics

Selection is explicit and click-driven.

- Hover alone never changes the active shikigami.
- Releasing the selector key never confirms a hovered slot.
- **Left-clicking an available slot changes the active shikigami immediately.**
- The strip remains open after a successful click and stays open until the original selector key is released.
- Multiple selections are allowed during one hold. The player may click Nue, then Rabbits, then another shikigami without closing the strip; every valid click updates the current active selection.
- When the strip finally closes, the last successful selection remains active.

### Unavailable entries

Unavailable shikigami remain visible but cannot be selected.

A click on an unavailable entry:

- does not change the active shikigami;
- does not close the selector;
- gives short, restrained reject feedback.

The UI must distinguish the reason/state instead of reducing every unavailable case to one generic grey slot. Relevant states include:

- not yet unlocked / tamed;
- permanently destroyed;
- temporarily unavailable;
- on cooldown, where relevant;
- already summoned.

The exact iconography is a visual-design detail, but the player must be able to understand the state without opening another menu.

## Active selection vs summoned shikigami

Megumi may have **multiple shikigami summoned at the same time**.

The selector does not represent a single-summon limit and does not manage a separate summon slot. It chooses the player's **current active shikigami** for subsequent shikigami-specific actions or commands.

Therefore:

- already-summoned shikigami remain selectable;
- "summoned" is a visible state marker, not a disabled state by itself;
- selecting one shikigami does not imply despawning any other summoned shikigami;
- the set of currently summoned shikigami and the one current active shikigami are separate concepts.

## Visual language

The selector should feel dense, tactile, fast, and premium rather than like a default Minecraft screen.

### Accent direction

Primary accent direction: **deep dark red with a slightly glossy, caramel-like warmth**.

- dark rather than neon;
- rich rather than flat pure burgundy;
- subtle glossy/highlight treatment is welcome;
- exact RGB values and material treatment are intentionally left for visual tuning.

### State hierarchy

Different states need different visual language instead of competing red outlines everywhere.

- **Current active shikigami** — stable dark-red accent frame/background treatment.
- **Hovered slot** — a brighter response plus a small model emphasis/scale response.
- **Already summoned** — its own small, readable state marker.
- **Unavailable** — dimmed/desaturated presentation plus a small marker explaining the state/reason.

A slot can carry more than one meaningful state at once. The hierarchy must stay readable when, for example, the active shikigami is also already summoned.

## Motion

Micro-animation is mandatory, but it must never make the selector slower to use.

- The selector should appear essentially immediately after the ~200 ms hold threshold.
- Opening/closing presentation should live around **100–150 ms maximum** and must not delay input readiness.
- Hover and selection feedback should be short, interruptible, and responsive rather than playing long canned sequences.
- No decorative transition may force the player to wait before clicking another shikigami.
- Repeated rapid selections during one hold must still look clean.

The desired feel is a polished physical control being pressed and moved through, not a menu performing a showcase animation.

## Audio feedback

Sound is part of the control feel, not decoration.

The selector needs distinct restrained feedback for at least:

- open;
- hover / movement between entries;
- successful selection;
- rejected unavailable selection.

The sounds must be short, pleasant, soft enough for repeated combat use, and have a tactile sense of impact. Avoid generic, overused UI beeps/clicks and anything sharp or repetitive enough to become annoying after many selections.

Exact assets are deferred to implementation/tuning, but the four feedback roles are part of the design contract.

## Input state summary

| State / input | Result |
|---|---|
| selector key down | start hold timer; do not change selection yet |
| release before ~200 ms | select next available shikigami in fixed canonical order |
| reach ~200 ms while held | open strip; short-press cycle is cancelled for this press |
| selector opens | cursor centres; mouse controls selector; camera-look pauses |
| LMB on available slot | active shikigami changes immediately; strip remains open |
| LMB on unavailable slot | no selection change; restrained reject feedback; strip remains open |
| another valid LMB during same hold | active selection changes again |
| release selector key while strip is open | close strip; keep last valid selection |
| release after opening with no valid click | close strip; preserve pre-open active selection |

## Non-goals

- No radial selector.
- No cross-shaped selector.
- No grid selector.
- No dynamic reordering or compaction of slots.
- No selection-on-hover.
- No confirmation-on-release.
- No pause or slow-motion while choosing.
- No separate UI for assigning multiple summon slots; simultaneous summons exist, while this selector only chooses the current active shikigami.
- No exact sound files, final icon art, exact colour constants, animation curves, or model framing values in this design spec; those are implementation/tuning details as long as they preserve the required feel and behaviour.

## Acceptance criteria

The design is satisfied when all of the following are true in play:

1. A quick press advances exactly once to the next available shikigami and never opens the strip.
2. Holding for roughly 200 ms opens the strip without first advancing the current selection.
3. Opening the selector never pauses or slows the game.
4. The selector appears as a bottom-centred horizontal strip above the hotbar and remains readable with approximately 7–8 normal entries and up to about 10.
5. Every shikigami keeps a stable slot position and always presents both its 3D model and name.
6. The cursor starts in the centre; camera-look is suspended while selecting; LMB cannot leak through into an attack.
7. Hovering provides feedback but never changes selection by itself.
8. Clicking an available shikigami changes the active selection immediately while leaving the strip open.
9. Several valid selections can be made during one hold, with the last successful click becoming the active shikigami.
10. Releasing the selector key closes the strip. If no valid click happened, the previous active selection is preserved.
11. Unavailable shikigami remain visible in their fixed positions, communicate why they are unavailable, reject selection, and do not close the strip.
12. Already-summoned shikigami remain selectable and are visually marked as summoned.
13. Multiple simultaneously summoned shikigami and the single current active shikigami remain separate concepts throughout the UX.
14. Current, hovered, summoned, and unavailable states stay visually distinguishable even when states overlap.
15. The dark-red / warm caramel-gloss accent direction is recognisable without becoming bright or neon.
16. Opening/closing and micro-feedback remain fast enough that animation never blocks rapid combat selection; the main entrance/exit presentation stays around 100–150 ms maximum.
17. Open, hover, confirm, and reject each have restrained tactile feedback that remains pleasant under repeated use.

## Design intent in one sentence

**A quick press handles routine cycling; a ~200 ms hold opens a fast bottom strip where Megumi can visually click any shikigami, including one already summoned, without stopping combat or losing stable spatial memory.**
