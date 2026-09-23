# Nobara player-model clip provenance (Blockbench MCP)

Source of truth: `nobara_player.bbmodel` (13-bone canonical rig: root→body→head/arms→elbows→hands; root→legs→knees).
Runtime exports: `assets/jujutsumod/geckolib/models/character_skin/nobara.geo.json` + `assets/jujutsumod/geckolib/animations/projectjjk/npc.animation.json`.
Every clip below was authored/edited through the Blockbench MCP tools on 2026-09-23 and exported from the bbmodel — no hand-edited JSON, no script-generated keyframes (design spec §28).

| Clip | Length | Loop | Notes |
|---|---|---|---|
| player_model.idle | 2.0s | loop | breathing idle |
| player_model.idle2 | 2.0s | loop | idle variant |
| player_model.walk | 1.0s | loop | |
| player_model.walk2 | 1.0s | loop | |
| player_model.run | 0.8s | loop | |
| player_model.attack1 | 0.6s | once | generic swing |
| player_model.attack2 | 0.6s | once | |
| player_model.attack3 | 0.6s | once | |
| player_model.snap | 0.4s | once | first-person snap |
| player_model.spell1 | 0.8s | once | legacy cast |
| player_model.spell3 | 0.8s | once | legacy cast |
| player_model.hammer_horizontal | 0.7s | once | anticipation→swing→contact→recoil |
| player_model.hammer_overhead | 0.9s | once | heavy overhead drive |
| player_model.hammer_nail_launch | 0.7s | once | launch prepared nails |
| player_model.hammer_embedded_drive | 0.8s | once | deepen embedded nail (covers "deepen" cue) |
| player_model.hammer_doll_strike | 0.9s | once | doll strike |
| player_model.self_resonance | 1.2s | once | self-resonance curse link |
| player_model.black_flash | 0.8s | once | |
| player_model.nail_prepare | 0.8s | once | nail preparation (CASTER_ACTION 2) |
| player_model.nail_trap_place | 0.8s | once | trap placement (CASTER_ACTION 3) |
| player_model.hairpin_activate | 0.6s | once | hairpin activation (CASTER_ACTION 1) |
| player_model.mega_nail_setup | 1.0s | once | mega setup (CASTER_ACTION 5) |
| player_model.mega_nail_charge | 0.8s | once | recipe-side MEGA_NAIL_CHARGE |
| player_model.mega_nail_release | 0.6s | once | recipe-side MEGA_NAIL_STRIKE |
| player_model.remnant_extract | 1.0s | once | extraction (CASTER_ACTION 6) |
| player_model.resonance_ritual | 2.0s | once | full ritual; contact keyframe at 1.2s (CASTER_ACTION 7) |

Dropped legacy clips (no producer in rework): one_two, spell2, spell4, spell5, swipe1.
Momentum transition clip intentionally omitted (spec §29 "if useful" — judged not useful; window reads through timing, not a pose).
