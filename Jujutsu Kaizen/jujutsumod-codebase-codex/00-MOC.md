# jujutsumod Codebase Codex — Map of Content

> База знаний **нашего** мода jujutsumod (Fabric 1.21.8)  
> Собрано: **Grok** · 2026-07-08 · quality target **10/10**  
> Эталон формата: [[../grok-projectjjk-codex/00-MOC|ProjectJJK Codex]]  
> **Каждый важный факт →** [[05-reference/Claim-Source-Index]]

## Source of truth (важно)

| Срез | Java files | Роль |
|---|---:|---|
| Checkout (ветка docs/agents) | ~28 | часто **без** full Nobara kit |
| **Worktree** .worktrees/nobara-cinematic-slice · branch codex/nobara-cinematic-slice | **77** | **полный product slice** Nobara+UI+VFX |

Все ile:line в этой базе цитируют **cinematic worktree**, если не сказано иначе.

## С чего начать

1. [[01-meta/Version-and-identity]]
2. [[01-meta/Citation-standard]]
3. [[01-meta/Sources-and-method]]
4. [[05-reference/Claim-Source-Index]]
5. [[03-systems/Nobara-overview]]
6. [[05-reference/ProjectJJK-parity-map]]

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

## 04 · Client / VFX

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
| Default kit | ProjectJJK-style items (nail/hammer) | VERIFIED |
| Cursed energy resource | **нет** в текущем kit (убрали) | VERIFIED |
| Client mixins | 3 (skin, camera, gamerenderer) | VERIFIED |
| Network payloads | 7 custom typed | VERIFIED |
| Entity types | 1 (projectjjk_nail) | VERIFIED |

## Связь с ProjectJJK vault

- Research index: Jujutsu Kaizen/grok-projectjjk-codex/
- Porting: [[05-reference/ProjectJJK-parity-map]] + [[05-reference/One-to-one-checklist]]
- Не копировать decompiled ProjectJJK код

---
tags: #jujutsumod #moc #knowledge-base
