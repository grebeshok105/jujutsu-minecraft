# Nobara Straw Doll Technique — canon research for jujutsumod

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-10  
Scope: canon behavior, evidentiary appearance, ProjectJJK reference behavior, and a minimal Minecraft gameplay loop. No architecture or implementation design.  
Spoilers: manga through **Chapter 267**.

## Evidence rules

- **VERIFIED** — directly shown or stated in the cited manga chapter / anime episode, or literal in the cited ProjectJJK decompile/resource.
- **INFERRED** — a conservative conclusion from multiple verified scenes; safe as design guidance, not as a literal canon rule.
- **UNKNOWN** — no explicit canon rule was found; do not present it as canon.
- The Jujutsu Kaisen Wiki/Fandom API was used only to navigate to exact chapter, page, episode, and frame identifiers. The claims below cite the manga/anime themselves as the primary work.
- ProjectJJK is a **reference implementation**, not a canon source.

Primary access points:

- [VIZ — official Jujutsu Kaisen chapter index](https://www.viz.com/shonenjump/chapters/jujutsu-kaisen)
- [MANGA Plus — official Shueisha title page](https://mangaplus.shueisha.co.jp/titles/100034)
- [Official anime episode portal](https://jujutsukaisen.jp/episodes/)
- [Crunchyroll — licensed anime series page](https://www.crunchyroll.com/series/GRDV0019R/jujutsu-kaisen)

## Executive conclusion

The straw doll is a **ritual proxy/effigy**, not a summon, pet, turret, or persistent autonomous fighter. Nobara normally combines a meaningful trace of the target with the effigy, then drives a cursed-energy nail through the proxy with her hammer so the effect manifests on the linked target.

The physical doll is **not universally mandatory**. In canon scenes the directly linked body itself acts as the effigy: Nobara's Decay-infected body and Mahito's double. Chapter 267 additionally shows Sukuna's finger as the direct cursed-object link, but does not need to prove whether a doll is outside the panel. Therefore the canon invariant is the **link + effigy/proxy + nail-driven curse**, not “a doll block must always exist.”

For a canon-forward first Minecraft slice, Resonance should require a real target remnant or exceptional linked proxy. Existing nail marks are appropriate for Hairpin and may help acquire a remnant, but a mark-only remote hit is not the full canonical Straw Doll ritual.

## Claim table

| Claim | Canon status | Primary source | Confidence |
|---|---|---|---|
| Straw Doll Technique uses a hammer, cursed-energy nails, and a straw effigy. | VERIFIED | Manga Ch. 5; anime Ep. 3 | High |
| The doll is a proxy that transfers an attack to a linked target; it is not an autonomous creature. | VERIFIED | Ch. 5; Ch. 41; Ep. 3; Ep. 17 | High |
| A severed body part can establish the link. | VERIFIED | Curse's severed arm in Ch. 5 / Ep. 3; Eso's severed arm in Ch. 62 / Ep. 24 | High |
| A non-biological trace can establish a link to an object. | VERIFIED | One bristle from Momo's broom in Ch. 41 / Ep. 17 | High |
| A weak/object trace need not damage the person: the broom bristle disrupts Momo's control of the broom. | VERIFIED | Ch. 41 pp. 10-12; Ep. 17 | High |
| The straw doll is not required when the linked body itself can serve as the effigy. | VERIFIED | Nobara's own Decay-linked body in Ch. 61; Mahito's double in Ch. 123 | High |
| Nails are both projectiles/conduits and the puncturing instrument used to activate Resonance. | VERIFIED | Ch. 5, 41, 61-62, 123, 267 | High |
| The hammer launches nails in normal combat and drives the ritual nail into the effigy/link. | VERIFIED | Ch. 5; Ch. 41; Ch. 62; Ch. 267 | High |
| Hairpin is a separate extension: it detonates cursed energy from already placed/embedded nails and does not require the doll. | VERIFIED | Ch. 41 pp. 7-8; Ch. 61; Ch. 123; Ep. 17, 24, 43 | High |
| Resonance effectiveness varies with the target, the body part/link value, and the relative power/connection; canon gives no fixed damage formula. | VERIFIED for variability; UNKNOWN for formula | Ch. 61 pp. 1-3; Ep. 24 | High / formula unknown |
| Resonance works remotely and does not require line of sight once a valid link exists. | VERIFIED | Fleeing curse in Ch. 5; fleeing Eso in Ch. 62; separate-location Sukuna strike in Ch. 267 | High |
| Canon establishes a numeric maximum range or cross-dimension/offline targeting. | UNKNOWN | No numeric cap or cross-world rule found | Low |
| Resonance generically deals “soul damage” or ignores all armor/defense. | UNKNOWN / overgeneralization | Ch. 123 works on Mahito because his double and original share the same soul; no universal armor rule is stated | High that the generic claim is unsupported |
| Resonance can hit multiple enemies whenever one cast has several targets. | UNKNOWN as a general rule | Ch. 61 reaches Eso and Kechizu through their special shared Decay connection; this does not prove generic multi-target behavior | High |
| Sukuna's indestructible finger can be used without destroying it by sacrificing destructive effect through a binding vow. | VERIFIED | Ch. 267 pp. 9-12 | High |
| Nails are finite ammunition and Nobara can run out. | VERIFIED | Ch. 7; anime Ep. 4 | High |
| Every Resonance consumes the doll or target fragment. | UNKNOWN | Canon scenes do not establish a universal consumption rule | Low |
| Canon specifies cooldown, cursed-energy number, wind-up duration, drop chance, link expiry, or cleanse item. | UNKNOWN | No universal numbers found in the primary scenes | High that these remain unspecified |

## What each component actually does

### Straw doll / effigy

- A compact human-shaped ritual proxy used to represent the target.
- It gives Nobara a safe physical object through which to drive the nail when the real target is elsewhere.
- It is an **interface for the curse**, not the source of the damage by itself.
- It is shown being handled/carried, not established as a placed world block, trap, inventory crafting station, or persistent summon.

### Target part or trace

The material establishes the meaningful connection:

- severed curse arm — living target link (Ch. 5);
- broom bristle — link to the broom/control, not direct bodily damage to Momo (Ch. 41);
- Decay/blood connection already inside Nobara — her own body becomes the proxy back to Eso and Kechizu (Ch. 61);
- Eso's severed arm — strong remote living-target link (Ch. 62);
- Mahito's double — the double's body is an effigy for the shared soul (Ch. 123);
- Sukuna's final finger — cursed-object link, enabled without destroying the object by a binding vow; the scene establishes the finger as the direct link but does not need to establish whether a doll is outside the panel (Ch. 267).

This supports a **link-quality** concept, but canon does not provide numeric tiers. “Blood always works,” “any owned item links to its owner,” and “an armor piece carries the owner's UUID” are unsupported extensions.

### Nail

- Carries Nobara's cursed energy.
- Acts as projectile ammunition in ordinary combat.
- Acts as the puncture/conduit that imposes the effigy's injury on the linked target during Resonance.
- Acts as the local anchor for Hairpin's later detonation.
- Is expendable ammunition in practice; canon explicitly shows Nobara running out of nails.

### Hammer

- Strikes and launches nails as ranged attacks.
- Drives the nail into the effigy/linked material for Resonance.
- Is reusable equipment; no canon scene shows it being consumed.
- A persistent placed doll that the player attacks like a block is a Minecraft adaptation, not an observed canon requirement.

## Known canon scenes

| Scene | What it proves | Source |
|---|---|---|
| First Tokyo mission: Yuji severs a curse's arm; Nobara uses the arm with the straw doll to exorcise the fleeing curse. | Classic doll + body-part proxy; remote finish. | Manga Ch. 5 pp. 10-13; anime Ep. 3 |
| Momo fight: Nobara takes one broom bristle and uses Resonance to drop/disrupt the broom. | Inanimate trace can link to an object; effect follows what the trace actually represents. | Ch. 41 pp. 10-12; Ep. 17 |
| Death Paintings: while infected by Decay, Nobara nails her own arm and sends Resonance back through the active connection to Eso and Kechizu. | Doll is not universal; an existing curse connection can make the user's body the effigy; shared links are special-case multi-target. | Ch. 61 pp. 1-3; Ep. 24 |
| Eso flees with a hostage; Nobara uses his severed arm to stop him remotely. | Strong body-part link, no line of sight required. | Ch. 62 pp. 9-12; Ep. 24 |
| Mahito double: Nobara nails the double's forehead and the effect reaches the original/shared soul. | A living proxy can itself be the effigy; “soul damage” is connection-specific, not a universal damage tag. | Ch. 123 pp. 16-22; Ep. 43 |
| Sukuna's last finger: Nobara strikes the cursed object at a separate location and disrupts Sukuna at the decisive moment. | Very long remote use; cursed object as link; binding vow works around the object's indestructibility. | Ch. 267 pp. 9-19 |

## Limitations and counterplay

### Canon-backed

- **Deny the link.** Without a meaningful fragment, trace, or exceptional active connection, Resonance has no demonstrated target path.
- **Link quality matters.** A broom bristle disrupts the broom; a severed body part can transmit severe bodily harm. Canon does not justify treating every trace as equal.
- **Relative power matters.** Ch. 61 explicitly frames effectiveness as variable rather than guaranteed damage.
- **Finite nails matter.** Running Nobara out of ammunition is a demonstrated combat limitation.
- **Special connections stay special.** Decay's shared link, Mahito's shared soul, and Sukuna's cursed finger should not become generic rules for every mob.

### Conservative gameplay inferences

- A readable hammer wind-up can give opponents an interrupt window. The ritual is visibly physical, but canon does not state a universal cast time.
- A finite server range/dimension boundary is a reasonable multiplayer compromise. It must be documented as balance, not canon.
- Consuming the target fragment after a successful cast is a clean anti-abuse rule, but it is not proven canon.
- A placed/exposed doll that can be broken is possible counterplay, but placement itself is not shown as a required canon step.

### Unsupported counterplay to avoid presenting as canon

- golden apple or potion “cleansing”;
- automatic link removal on armor change;
- fixed ten-minute immunity;
- guaranteed doll destruction/recoil damage;
- universal armor bypass, percent-max-health damage, or instant kill;
- hitting unloaded/offline players or targets in another dimension.

## VERIFIED / INFERRED / UNKNOWN summary

### VERIFIED

- Doll = ritual proxy, not summon.
- Body parts and meaningful traces can create a link.
- Doll can be bypassed when the linked body/material itself is the effigy.
- Hammer + nail physically enact the curse.
- Remote/no-LOS transmission is real.
- Result depends on connection/context; it is not one fixed damage formula.
- Hairpin and Resonance are mechanically distinct.

### INFERRED

- A “link quality” gameplay stat is faithful if kept qualitative and scene-driven.
- For a normal Minecraft living target, a real remnant is a more canonical Resonance gate than an ordinary nail mark.
- The doll should appear as a short-lived held ritual prop unless a placed-doll mechanic is intentionally chosen for counterplay.
- A fragment-consumption rule is useful balance, not lore.

### UNKNOWN

- Exact range, cooldown, CE cost, damage, armor interaction, cast time, and link lifetime.
- Whether the doll or fragment is always consumed.
- Whether blood alone always counts, independent of an active curse connection.
- Whether any personal possession can link to its owner.
- Whether Resonance can target offline/unloaded/cross-dimensional entities.
- Whether an opponent can cleanse or sever the link by a standard method.

## Canon-provable doll appearance

The clearest evidence is manga **Chapter 5** and anime **Episode 3**:

- small enough for Nobara to carry and manipulate by hand/under her jacket;
- simple human silhouette made from bundled straw;
- short cylindrical head with no clearly visible face;
- vertical bundled torso;
- separate straight arm bundles forming a cross-like silhouette;
- two split/tapering leg bundles;
- dark cord/bands around major bundle joints and the waist;
- rough, uneven straw ends;
- anime palette: muted yellow-green/olive straw with dark green-black bindings.

Not canonically fixed: exact measurements, polygon count, knot count, facial marks, UV layout, animation timing, precise shade values, or whether every replacement doll is visually identical.

## Original-inspired asset boundary

The runtime model and texture should be created from scratch. Preserve only the broad, canon-readable vocabulary:

- bundled straw;
- dark ritual bindings;
- compact humanoid silhouette;
- rough handmade asymmetry;
- clear nail-impact area in the torso.

Make the implementation recognizably original by changing proportions, limb angles, knot layout, straw breakup, surface markings, texture palette, idle/spawn motion, and impact animation. Do **not** trace a manga panel, copy an anime frame/texture, or reuse ProjectJJK's geometry/texture.

This is a practical IP-risk reduction, not legal advice. The Jujutsu Kaisen character/technique remains copyrighted, and ProjectJJK's extracted `fabric.mod.json:14` explicitly declares **All Rights Reserved**.

## ProjectJJK reference behavior — not canon

Decompile root: `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled\`  
Extract root: `C:\Users\KOMP1\Downloads\projectjjk_extract\`

| ProjectJJK behavior | Source | Status |
|---|---|---|
| Resonance is registered as damage `20`, cooldown `20s`, cost `100`, suppression `6s`. | `net/hadences/game/system/ability/AbilityRegistry.java:172` | VERIFIED ProjectJJK |
| `resonant_remains` uses a `5%` body-part chance, `10s` cooldown, cost `35`. | `AbilityRegistry.java:177`; `.../ResonantRemains.java:27-60` | VERIFIED ProjectJJK |
| Damage by a player with the passive rolls the chance and spawns a `BodyPartEntity` carrying the victim owner's UUID. | `net/hadences/mixin/LivingEntityMixin.java:343-361` | VERIFIED ProjectJJK |
| `BodyPartEntity` stores owner UUID, defaults to `600` life ticks, and despawns if expired or owner is absent. | `net/hadences/entity/custom/other/BodyPartEntity.java:65-111` | VERIFIED ProjectJJK |
| Resonance only raycasts `3.0` blocks with width `0.2` for a nearby `BodyPartEntity`. | `.../straw_doll_technique/Resonance.java:60-61,81-88` | VERIFIED ProjectJJK |
| It spawns a `DollEntity` at the part, starts spawn animation, waits `750ms`, then impacts `50ms` later. | `Resonance.java:89-95,104-147` | VERIFIED ProjectJJK |
| On impact it destroys the body part, searches the owner only within `30` blocks, deals `nail_damage`, and applies `SUPPRESSED` for 120 ticks. | `Resonance.java:118-145` | VERIFIED ProjectJJK |
| The doll has triggerable `spawn` and looping `impact` animations. | `net/hadences/entity/custom/other/DollEntity.java:46-93` | VERIFIED ProjectJJK |
| The ProjectJJK doll is a custom 32×32 box model with head/body/four limbs, plus ~0.58s spawn and ~0.54s impact resources. | `assets/projectjjk/geo/doll.geo.json:1-73`; `assets/projectjjk/animations/doll.animation.json:1-128` | VERIFIED ProjectJJK |

These are useful pacing references, but the random 5% drop, 30-block lookup, fixed 20 damage, six-second suppression, exact animations, and model are **ProjectJJK design choices**. They are not evidence of canon.

## Minimal canon-forward gameplay loop for the current mod

This is gameplay translation only; it intentionally avoids architecture.

1. **Earn a link.** A clearly defined successful Nobara hit/condition produces a target remnant. The acquisition rule is a Minecraft adaptation; requiring a meaningful remnant is canon-forward.
2. **Bind the effigy.** Use the remnant with the straw doll in one short interaction. Show target identity and link quality. The explicit “combine” interaction is an adaptation, not a shown crafting ritual.
3. **Telegraph the ritual.** Nobara visibly holds/raises the doll and prepares hammer + nail. The opponent receives a readable warning and may interrupt the cast.
4. **Strike.** The hammer drives one nail into the doll. Server-authoritative Resonance hits the linked, loaded target without line of sight.
5. **Resolve by link quality.** For the first slice, accept only a genuine body remnant for living-target damage. Object traces such as a broom bristle should affect the linked object/control, not automatically injure its owner.
6. **Consume limited setup.** Consume one nail and, for multiplayer safety, the remnant; retain hammer and doll. Mark remnant consumption as a balance rule.
7. **Reset.** Clear the link. To cast again, Nobara must earn another remnant.

Recommended first-slice counterplay: deny remnant acquisition, recover/destroy the exposed remnant if the chosen presentation allows it, interrupt the hammer wind-up, or leave the supported server range/dimension. Do not add a magical cleanse item without a separate design decision.

### Relationship to the current jujutsumod loop

The current runtime already has embedded nail marks, a temporary target link, and a shift-hammer remote strike. That is a useful ProjectJJK-inspired prototype, but it omits the canon-defining **physical effigy/remnant ritual**. A canon-forward Straw Doll pass should keep marks for Hairpin/target pressure and require a remnant + doll for full Resonance instead of treating an ordinary mark as the whole link.

## Local research consulted

Obsidian:

- `grok-projectjjk-codex/00-MOC.md`
- `grok-projectjjk-codex/01-meta/Citation-standard.md`
- `grok-projectjjk-codex/03-abilities/Straw-Doll.md`
- `grok-projectjjk-codex/05-reference/Claim-Source-Index.md`
- `grok-projectjjk-codex/05-reference/One-to-one-checklist.md`
- `jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- `jujutsumod-codebase-codex/03-systems/Target-marks-and-resonance.md`
- `jujutsumod-codebase-codex/03-systems/Nobara-explicit-actions.md`

Secondary navigation only:

- [Straw Doll Technique](https://jujutsu-kaisen.fandom.com/wiki/Straw_Doll_Technique)
- [Resonance](https://jujutsu-kaisen.fandom.com/wiki/Resonance)
- chapter pages for 5, 41, 61, 62, 123, and 267 on the same wiki, used to locate exact primary scenes.
