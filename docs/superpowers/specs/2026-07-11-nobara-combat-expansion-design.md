# Nobara Combat Expansion Design

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

## Intent

Deepen the existing ProjectJJK Nobara vertical slice without replacing its canonical runtime or VFX Core. Nails become durable gameplay objects, hammer attacks become a real aggressive melee loop, Resonance gains weight without potion-effect shortcuts, and Black Flash/curse links establish small reusable contracts for later characters.

The amplified doll impact briefly lowers the authoritative server tick rate to `6 TPS` for four server ticks, then restores the exact prior rate. This is intentionally a global multiplayer beat because Minecraft has no entity-local authoritative clock. Repeated impacts extend the window, shutdown restores the rate, and an external tick-rate change made during the beat is never overwritten. The doll no longer uses the vanilla anvil sound; its impact stack is cursed implosion, deep explosion, Black Flash crack, and a long low whoosh, paired with layered explosion emitters, flashes, and camera impulses at both the doll and target.

## Nail ownership and anchors

Every placed nail remains a `ProjectJjkNailEntity` owned by a player UUID. Its anchor is one of `NONE`, `ENTITY`, `BLOCK`, or `RUNTIME_OBJECT`. Entity anchors persist a target UUID plus cached entity id and body-local hit transform. Block anchors persist dimension, block position, state signature, local hit point, and face. Runtime-object anchors persist a resolver type id and stable object id.

Resolution distinguishes loaded, temporarily unavailable, confirmed removed, and invalid. Missing chunks/entities never imply death. A dormant nail keeps its last position and identity until its UUID resolves again. Confirmed death, despawn/final removal, explicit runtime-object removal, incompatible block replacement, or Hairpin consumption removes it.

## Combat flow

Nail preparation spawns one server entity per 10 ticks, capped at eight. Contextual LMB selects a prepared nail launch, embedded-nail drive, or alternating horizontal/overhead hammer attack. A second LMB inside a server-owned Black Flash window is cursed-energy timing input; success amplifies only that impact and never triggers Hairpin.

Enlarge and Boom enumerate concrete owned nails. Each nail produces its own cooldown-bypassing Hairpin damage event and VFX cue. The balance profile initially uses Enlarge `4`, Boom `3`, doll Resonance `28`, self resonance `6/18`, and Black Flash `1.75x`.

## Curse links

`CurseLinkRegistry` stores explicit source-owned technique links. Status effects cannot create links. Unload/disconnect does not remove a link; its source technique explicitly removes it. Shift+R activates the only link immediately. Multiple links require explicit selection from a compact source/technique menu, followed by Shift+R confirmation; stale selections are rejected on the server.

## Presentation and authority

The server owns action validation, timing, damage, stagger, links, anchors, and removal. Clients receive semantic action/VFX cues carrying server time and render through existing director channels. Persistent nails stay in `ProjectJjkNailRenderer`. GeckoLib first/third-person animations share timeline constants with gameplay and preserve the existing held-item attachment chain.

## Verification

Pure tests cover anchors, rebinding, lifecycle, selection, timing, link ambiguity, and balance. Structural assertions guard the VFX Core path and forbidden potion debuffs. Full `check` and runtime build run after major stages. One global review occurs after implementation; confirmed findings are fixed once, followed by final verification and JAR hash comparison. Gameplay feel remains manual QA.
