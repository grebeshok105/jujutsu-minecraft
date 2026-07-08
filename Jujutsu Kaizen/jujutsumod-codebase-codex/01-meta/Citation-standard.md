# Citation Standard

← [[00-MOC]] · [[Sources-and-method]]

## Goal

Каждый actionable claim проверяется за ≤30 секунд по ile:line.

## Format

`
**Source:** .worktrees/nobara-cinematic-slice/src/.../File.java:LINE — факт / symbol
**Resource:** src/main/resources/... (если asset)
**Status:** VERIFIED | INFERRED | UNKNOWN
`

Опционально: Symbol: ClassName.methodName если lines могут сдвинуться.

## Status

| Status | Значение | Можно 1:1? |
|---|---|---|
| **VERIFIED** | Буквально в коде/ресурсе | Да, после re-read |
| **INFERRED** | Цепочка вызовов без одного литерала | Перепроверь path |
| **UNKNOWN** | Не найдено / runtime only | Не имплементировать как факт |

## Paths

| Root | Path |
|---|---|
| Full product (default cite) | D:/WorkFlow/Jujutsu Minecraft/.worktrees/nobara-cinematic-slice/ |
| Thin checkout | D:/WorkFlow/Jujutsu Minecraft/ (может отставать) |
| Repo-relative cite prefix | .worktrees/nobara-cinematic-slice/ |

## Required anchors

| Claim type | Must cite |
|---|---|
| identity/version | abric.mod.json / gradle.properties |
| register order | JujutsuMod.onInitialize |
| item/entity id | registry class field line |
| balance number | ProjectJjkNobaraProfile constant |
| network payload | record class + JujutsuNetworking.registerPayloads |
| client-only | src/client/... path |
| test command | uild.gradle task |

## Master index

[[05-reference/Claim-Source-Index]]

## Before changing behavior

1. Open cited file:line
2. Confirm method still matches
3. Check [[05-reference/ProjectJJK-parity-map]] if parity-related
4. Only then edit code (отдельная задача — не эта база)

---
tags: #jujutsumod #citation
