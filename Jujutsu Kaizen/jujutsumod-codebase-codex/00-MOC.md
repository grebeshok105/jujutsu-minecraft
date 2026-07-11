# jujutsumod Codebase Codex — Map of Content

> База знаний **нашего** мода jujutsumod (Fabric 1.21.8)  
> Собрано/обновлено: **Grok/Codex** · 2026-07-10 · quality target **10/10**
> Эталон формата: Obsidian note `grok-projectjjk-codex/00-MOC.md` (vault source, not committed on this branch).
> **Каждый важный факт →** [[05-reference/Claim-Source-Index]]

## Source of Truth (важно)

| Срез | Java files | Роль |
|---|---:|---|
| Checkout (ветка docs/agents) | partial | часто **без** full Nobara kit |
| **Worktree** `.worktrees/nobara-cinematic-slice` · branch `codex/nobara-cinematic-slice` | product slice | **актуальная ProjectJJK Nobara+UI+VFX реализация** |

Все `file:line` в этой базе цитируют **cinematic worktree**, если не сказано иначе.

## С чего начать

1. [[01-meta/Version-and-identity]]
2. [[01-meta/Citation-standard]]
3. [[01-meta/Sources-and-method]]
4. [[05-reference/Claim-Source-Index]]
5. [[03-systems/Nobara-overview]]
6. [[03-systems/Nobara-combat-expansion]]
7. [[03-systems/Combat-timing-and-black-flash]]
8. [[03-systems/Curse-links]]
9. [[03-systems/Straw-Doll-resonance]]
10. [[04-client-vfx/VFX-core]]
11. [[05-reference/ProjectJJK-parity-map]]

## 01 · Meta

- [[01-meta/Version-and-identity]]
- [[01-meta/Sources-and-method]]
- [[01-meta/Citation-standard]]
- [[01-meta/Uncertainties]]
- [[01-meta/Codegraph-status]]

## 02 · Architecture

- [[02-architecture/Entrypoints-and-lifecycle]]
- [[02-architecture/Registries]]
- [[02-architecture/Networking]]
- [[02-architecture/Client-server-boundaries]]
- [[02-architecture/Assets-and-resources]]

## 03 · Systems (gameplay)

- [[03-systems/Character-selection]]
- [[03-systems/Nobara-overview]]
- [[03-systems/Nobara-runtime-flow]]
- [[03-systems/Nail-entity-lifecycle]]
- [[03-systems/Target-marks-and-resonance]]
- [[03-systems/Straw-Doll-resonance]]
- [[03-systems/Nobara-combat-expansion]]
- [[03-systems/Combat-timing-and-black-flash]]
- [[03-systems/Curse-links]]

## 04 · Client / VFX

- [[04-client-vfx/VFX-core]] — **start here for transient effect authoring**
- [[04-client-vfx/Nail-rendering]]
- [[04-client-vfx/Hairpin-effects]]
- [[04-client-vfx/GUI-character-select]]

## 05 · Reference

- [[05-reference/Claim-Source-Index]]
- [[05-reference/Public-api-surface]]
- [[05-reference/Test-and-build-commands]]
- [[05-reference/ProjectJJK-parity-map]]
- [[05-reference/One-to-one-checklist]]

## 06 · Maintenance

- [[06-maintenance/How-to-add-next-character]]
- [[06-maintenance/Risks-and-tech-debt]]

## Быстрые факты

| Факт | Значение | Status |
|---|---|---|
| mod id | jujutsumod | VERIFIED |
| MC | 1.21.8 | VERIFIED |
| Java | 21 | VERIFIED |
| Default kit | ProjectJJK-style nail, hammer, animated straw doll | VERIFIED |
| Resonance gate | target-bound remnant + doll + nail + hammer ritual | VERIFIED |
| Resonance remnant identity | persistent/network-synced `resonance_target` item component | VERIFIED |
| Canonical Nobara runtime | `character/nobara/projectjjk` only | VERIFIED |
| Legacy Nobara runtime | removed | VERIFIED |
| Transient VFX path | `VfxCuePayload → VfxDirector → Java recipe` | VERIFIED |
| Cursed energy resource | **нет** в текущем kit (убрали) | VERIFIED |
| Client mixins | 6 (skin, camera/game renderer, Nobara player/living renderer, FP snap) | VERIFIED |
| Network payloads | 7 custom typed payloads | VERIFIED |
| Entity types | 1 (`projectjjk_nail`) | VERIFIED |
| Target mark visual | vanilla `MobEffects.GLOWING`, cyan scoreboard team | VERIFIED |
| Nobara VFX recipes | 14 stable cue IDs through one director | VERIFIED |
| Optional post-process | director-owned vanilla blur; session-safe fallback on failure | VERIFIED |

## Связь с ProjectJJK Vault

- Research index: `Jujutsu Kaizen/grok-projectjjk-codex/`
- Porting: [[05-reference/ProjectJJK-parity-map]] + [[05-reference/One-to-one-checklist]]
- Не копировать decompiled ProjectJJK код байт-в-байт; переносить verified behavior/contracts.

## Recent Updates

- [[03-systems/Straw-Doll-resonance]] — canonical remnant/effigy/nail ritual, server validation, balance adaptations, and original animated doll asset, 2026-07-10.
- [[04-client-vfx/VFX-core]] — reusable Fabric-native cue/director/recipe library; Nobara is the first reference consumer; old integer payload/static VFX paths removed, 2026-07-10.
- [[04-client-vfx/VFX-core]] — target-local Resonance slow/zoom/nausea polish remains inside director-owned channels; no server-time mutation, 2026-07-11.
- [[06-maintenance/Risks-and-tech-debt]] — VFX ownership, quality/culling, blur fallback, and migration risks updated, 2026-07-10.
- [[03-systems/Nobara-combat-expansion]] — persistent anchors, per-nail Hairpin, hammer routing, Black Flash, curse links, and self resonance documented against the 2026-07-11 combat expansion.

---
tags: #jujutsumod #moc #knowledge-base
