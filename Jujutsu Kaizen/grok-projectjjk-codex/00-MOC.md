# ProjectJJK Codex — Map of Content

> База знаний ProjectJJK `1.2.0-1.21.1-fabric-beta`  
> Собрано: **Grok** · 2026-07-08 · **v2 citations 10/10 pass**  
> Метод: CFR decompile + jar extract + 3× agents + mixin re-verify  
> Vault: `Jujutsu Kaizen/grok-projectjjk-codex`  
> **Каждый важный факт →** [[05-reference/Claim-Source-Index]]

## С чего начать

1. [[01-meta/Version-identity|Версия и identity мода]]
2. [[01-meta/Sources-and-method|Источники и как проверяли]]
3. [[05-reference/Cheat-sheet-RU|Читерка простым языком (RU)]]
4. [[03-abilities/_index|Каталог способностей]]
5. [[06-for-jujutsumod/Porting-notes|Что брать в jujutsumod]]

## 01 · Meta

- [[01-meta/Version-identity]]
- [[01-meta/Sources-and-method]]
- [[01-meta/Citation-standard]]
- [[01-meta/License-and-legal]]
- [[01-meta/Uncertainties]]

## 02 · Architecture

- [[02-architecture/Ability-system]]
- [[02-architecture/Cursed-energy]]
- [[02-architecture/Cooldown-and-stun]]
- [[02-architecture/Rank-and-progression]]
- [[02-architecture/Networking]]
- [[02-architecture/Effects]]
- [[02-architecture/Damage-types]]
- [[02-architecture/Libraries]]
- [[02-architecture/Mixins]]
- [[02-architecture/Entities-and-items]]

## 03 · Abilities

- [[03-abilities/_index]]
- [[03-abilities/Passives]]
- [[03-abilities/Learnable]]
- [[03-abilities/Straw-Doll]]
- [[03-abilities/Limitless]]
- [[03-abilities/Boogie-Woogie]]
- [[03-abilities/Cursed-Speech]]
- [[03-abilities/Ratio]]
- [[03-abilities/Blood-Manipulation]]

## 04 · Classes & Grades

- [[04-classes/Class-selection]]
- [[04-classes/Grade-unlock-tables]]

## 05 · Reference tables

- [[05-reference/Claim-Source-Index]] ← **главный индекс якорей**
- [[05-reference/Full-registry-table]]
- [[05-reference/Hold-thresholds]]
- [[05-reference/Dependencies]]
- [[05-reference/Cheat-sheet-RU]]
- [[05-reference/One-to-one-checklist]]

## 06 · For jujutsumod

- [[06-for-jujutsumod/Specter-verdict]]
- [[06-for-jujutsumod/Porting-notes]]

## Быстрые факты

| Факт | Значение |
|---|---|
| Ability id в registry | **51** (`AbilityRegistry.java:142-192`) |
| Playable innate classes | **6** |
| Stub classes | Ten Shadows, Black Bird, Construction (+ Inverse) |
| Passives | 5 — runtime mostly in **mixins** (see Claim-Source-Index) |
| Specter обязателен для logic? | **Нет** |
| MC target | **1.21.1** Fabric · Java **21** |
| License | **All Rights Reserved** |
| Quality bar | citations + 1:1 checklist + honest caveats |

## Связь с jujutsumod

Локальный проект: `D:\WorkFlow\Jujutsu Minecraft`  
Технический dump (старый): `docs/research/projectjjk/`  
Эта папка — **Obsidian knowledge base** для дизайна и port.

---

tags: #projectjjk #moc #grok #knowledge-base
