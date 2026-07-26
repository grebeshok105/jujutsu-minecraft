# Mixins

← [[00-MOC]]

Config: `projectjjk.mixins.json`  
Package: `net.hadences.mixin`  
Java 21 · refmap `projectjjk-refmap.json`

## Common (server+shared)

| Mixin | Likely purpose (from name + related systems) |
|---|---|
| GlowingColorMixin | outline color override |
| ItemEntityMixin | item entity hooks |
| LivingEntityMixin | living damage/status |
| ModEntityDataSaverMixin | persistent player/entity data |
| NewWorldMixin | world creation hooks |
| PlayerEntityMixin | player combat/data |
| ServerPlayerEntityMixin | server player |
| SummonCommandMixin | summon command |
| XPDataMixin | XP system |

## Client

| Mixin | Likely purpose |
|---|---|
| AbilityScrollMixin | ability hotbar scroll |
| AbilityUseMixin | ability use input |
| ClientPlayerEntityMixin | client player |
| EnableGlowingMobMixin | force outline on marked UUIDs |
| FirstPersonRendererMixin | FP animations |
| HeldItemRendererMixin | held item / snap anim |
| KeyBindingMixin | keybinds |
| KeyboardMixin | keyboard |
| LivingEntityRendererMixin | render living |
| custom_healthbar.CustomHealthBarMixin | custom HP bar |
| dimension_transition.ProgressScreenMixin | dim transition |
| dimension_transition.TerrainLoadScreenMixin | terrain load |

## Glowing / mark system

Related data: `GlowingData` + packets SynchronizeGlowingData / Color.  
Client: `EnableGlowingMobMixin` + `GlowingColorMixin`.  
Used for visual tags (e.g. after nail hit language) — **not Specter**, ProjectJJK own mixins.

## Note for jujutsumod

Your AGENTS.md: avoid mixins unless Fabric API cannot solve it. ProjectJJK is mixin-heavy for input, outline, FP anim, healthbar. Prefer public API + narrow mixins only when proven necessary.

---

tags: #projectjjk #mixins
