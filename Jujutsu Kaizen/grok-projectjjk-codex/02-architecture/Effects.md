# Effects (Status)

← [[00-MOC]] · registry `ModEffects` · tick `EffectManagerTick`

| id | Field | Category | Notes |
|---|---|---|---|
| `stun` | STUN | harmful | always applies |
| `guard` | GUARD | harmful | uses StunEffect + move speed ×−0.15 |
| `infinity` | INFINITY | beneficial | repels projectiles/entities; CE drain; class must be limitless |
| `six_eyes` | SIX_EYES | beneficial | class limitless; 50% CE refund on spend |
| `zenith_focus` | ZENITH_FOCUS | beneficial | shell; logic may be event-side |
| `sixth_sense` | SIXTH_SENSE | beneficial | shell; chance application elsewhere ⚠ |
| `reverse_cursed_technique` | RCT | beneficial | move ×−0.18; 30 CE/tick + regen/resist |
| `phantom_applause` | PHANTOM_APPLAUSE | harmful | ~25%/s: blindness 30t; non-players + stun 30t |
| `overtime` | OVERTIME | beneficial | shell; abilities branch on presence |
| `suppressed` | SUPPRESSED | harmful | **blocks INNATE casts** |
| `wing_king` | WING_KING | beneficial | enables wing render set; S2C SetWingKingRenderPacket |

Textures: `assets/projectjjk/textures/mob_effect/` (~11 png).

## Continuous costs

See [[Cursed-energy]] — RCT 30/tick, Infinity 10/sec.

## Combat relevance

- **SUPPRESSED** = anti-technique (Resonance applies it for 6s)
- **INFINITY** skips some speech damage (DontMove etc.)
- **OVERTIME** changes Collapse/SwiftStrike cast paths + CD reduction

---

tags: #projectjjk #effects
