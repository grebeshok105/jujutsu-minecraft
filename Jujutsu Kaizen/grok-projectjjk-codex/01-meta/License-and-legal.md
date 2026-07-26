# License & Legal

← [[00-MOC]]

## ProjectJJK

- License field in `fabric.mod.json`: **All Rights Reserved**
- Author: hadences
- Implication: no free reuse of code, assets, or decompiled sources in commercial/open projects without explicit permission

## Nested libraries (their licenses, from nested fabric.mod.json)

| Lib | License (metadata) |
|---|---|
| BossBarLib | AGPL-3.0 |
| GeckoLib | MIT |
| MidnightLib | MIT |
| Satin | LGPL-3.0-or-later |
| SmartBrainLib | MPL-2.0 |
| Specter | ARR-style (author lib; embedded as `net.lib.Specter`) |

## Rules for this vault

1. Notes are **research / design reference**, not a redistribution of ProjectJJK.
2. Do **not** paste full decompiled class bodies into jujutsumod.
3. Reimplement behavior from public Fabric 1.21.8 APIs.
4. Do **not** copy ARR assets (textures/sounds/geo) without permission.
5. Comparison assets already in jujutsumod should follow your project's existing import notes.

## Safe uses

- Understanding combat loops for original design
- Balance number reference (then redesign)
- Architecture patterns (hold thresholds, CE, grades)

## Unsafe uses

- Dropping decompiled `.java` into your repo
- Shipping ProjectJJK models/sounds as-is
- Depending on ProjectJJK jij stack on 1.21.8 without port work

See also: [[06-for-jujutsumod/Porting-notes]]

---

tags: #projectjjk #legal
