# Networking

← [[00-MOC]] · [[Ability-system]]

Fabric 1.21 custom payloads.  
Packages: `net.hadences.network.packets.{C2S,S2C}` + handlers.

## C2S (client → server)

- AbilityHudNextPointerPacket
- AbilityHudPrevPointerPacket
- AbilityHudSetPointerPacket
- AbilityPopupMenuOverlayNextPointerPacket
- AbilityPopupMenuOverlayPrevPointerPacket
- AbilityUsePacket
- GetInnateClassesPacket
- OnAcceptQuestPacket
- OnRerollQuestPacket
- RemoveAbilitySlotPacket
- RightClickAbilityTriggerPacket
- SendInnateClassesPacket *(folder C2S; also used as S2C type)*
- SetAbilitySlotPacket
- SetCombatModePacket
- SetInnateClassPacket
- TriggerOnHoldFunctionPacket
- TriggerOnReleaseFunctionPacket

## S2C (server → client)

**Ability / CE / combat**

- ClearClientCooldownsPacket
- ForceCancelAbilityHoldFunctionPacket
- RefreshAbilityInventoryScreenPacket
- SetAbilityCooldownPacket
- SynchronizeAbilityHudPointerPacket
- SynchronizeAbilityInventoryPacket
- SynchronizeAbilityPopupMenuOverlayPointerPacket
- SynchronizeAbilitySlotsPacket
- SynchronizeBloodControlPacket
- SynchronizeCombatModePacket
- SynchronizeCounterPacket
- SynchronizeCursedEnergyPacket
- SynchronizeMaxCursedEnergyPacket
- SynchronizeCEBaseRegenerationRatePacket
- SynchronizeCERegenerationTimeoutPacket
- SynchronizeCERestRegenerationRatePacket
- SynchronizeDamageMultiplierPacket
- SynchronizeInnateClassPacket
- SynchronizeOPStatePacket
- SynchronizeOverlayStatePacket
- SynchronizeRankPacket
- SynchronizeStunPacket
- SynchronizeXPPacket
- SynchronizeRerollCountPacket
- SynchronizeQuestListPacket
- TriggerAbilityOnReleasePacket
- UpdateCombatHUDPacket

**Glowing / outline**

- SynchronizeGlowingDataPacket
- SynchronizeGlowingColorPacket

**VFX / presentation**

- SetCircleRaySatinShaderPacket
- SetColorSatinShaderPacket
- SetSatinShaderPacket
- SetFPAnimationPacket
- SetImpactEffectPacket
- SetFadeScreenPacket
- SpawnColorFlashEffectPacket
- SetWingKingRenderPacket

**UI screens**

- ShowClassSelectionScreenPacket
- ShowScreenPacket
- ShowSorcererScreenPacket
- SetPopupMenuPacket
- SetCurrentPopupMenuTypePacket

## Cast path packets

```
Client hold/release
  → TriggerOnHoldFunctionPacket / TriggerOnReleaseFunctionPacket
  → AbilityUsePacket (heldDuration ms)
Server AbilityUseC2SPacketHandler
  → Ability.onCast
  → SetAbilityCooldownPacket + CE sync packets
  → TriggerAbilityOnReleasePacket broadcast
```

## Design takeaway

ProjectJJK is **network-heavy**: CE, slots, glowing, satin shaders, FP anim, blood control, rank — each has dedicated payloads.  
jujutsumod already uses typed custom payloads for Hairpin; grow carefully, don't copy this entire surface.

---

tags: #projectjjk #networking
