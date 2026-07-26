# Bundled Libraries

← [[00-MOC]] · verdict: [[06-for-jujutsumod/Specter-verdict]]

## Declared jar-in-jar (`fabric.mod.json`)

| File | Present as nested jar? | Role |
|---|---|---|
| specter-v0.1.0.jar | **Missing under META-INF/jars** — code lives as `net.lib.Specter` embedded | particles/render helpers |
| BossBarLib-v2.0.2.jar | yes | custom boss bars |
| geckolib-fabric-1.21.1-4.6.6.jar | yes | Geo entities / animations |
| midnightlib-1.6.3-fabric.jar | yes | MidnightConfig |
| satin-2.0.0.jar | yes | post shaders / screen FX |
| SmartBrainLib-fabric-1.21.1-1.16.1.jar | yes | NPC/spirit AI brains |

Also present: `net.lib.SimpleText`, `net.lib.ParticleUtils`.

## Verified code references

| Lib | Packages | Used for |
|---|---|---|
| **Satin** | `org.ladysnake.satin.api.*` | `SatinUtil`, client shader callbacks |
| **GeckoLib** | `software.bernie.geckolib.*` | Nail, Doll, spirits, VFX planes, geo items |
| **Specter** | `net.lib.Specter.*` | ParticleBehaviorRegistry, combat particles, client shader init |
| **BossBarLib** | `net.hadences.common.CustomBossBar*` | finger bearer / Choso bars |
| **MidnightLib** | `eu.midnightdust.lib.config.MidnightConfig` | ModConfig |
| **SmartBrainLib** | `net.tslat.smartbrainlib.*` + wrappers | NPC movesets (incl. Nobara NPC) |

## Satin effects used by abilities (examples)

| Ability | Satin |
|---|---|
| Black Flash | BLACK_AND_WHITE 4, SCREEN_SHAKE 10 |
| Hairpin Enlarge | SHAKE 8, B&W 4 |
| Hairpin Explosion | SHAKE 5/10 |
| Resonance | B&W 4, SHAKE 10 |
| Purple / masteries | shake, color, circle ray… |
| Cursed speech | SHAKE 10 |
| Piercing Blood max | SHAKE 60 |

## Do you need them for ability logic?

| Feature | Needs Specter? | Needs GeckoLib? | Needs Satin? | Needs SBL? |
|---|---|---|---|---|
| CE / CD / hotbar | no | no | no | no |
| Nail damage / hairpin math | no | no | no | no |
| 3D nail/doll models | no | **yes** (their path) | no | no |
| Black Flash screen flash | no | no | **yes** (their path) | no |
| Cursed spirit AI | no | model | no | **yes** |
| Config UI | no | no | no | no (Midnight) |

**Bottom line:** ability **gameplay** is pure server Java + Fabric networking. Libraries are presentation / AI / config layers.

---

tags: #projectjjk #libraries
