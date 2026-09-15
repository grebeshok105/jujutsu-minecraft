# Run: fix-perception (БЛОК A)

- Repo/worktree: `D:/WorkFlow/jm-wt-perception`, branch `fix/review-perception`
- BASE: `61ef797ffe193547c51e848dbcfff4639b6fa66a`
- Spec: `audit/review-3pr/REVIEW-SUMMARY.md` + `reviewer-pr89-perception.md` + `reviewer-pr89-grade-spawn.md` + gh issue #90 (comment N1–N5)
- Env: Windows bash; gradle ALWAYS `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...`
- Ownership ceiling: `cursedspirit/perception/*`, `CursedSpiritEntity.java`, `mixin/CursedSpirit*`, `client/mixin/Fear*`, `client/curse/fear/*`, `client/render/cursedspirit/CurseRenderGate`, `CursedSpiritRenderer`, perception GameTests. NOT: `CursedSpiritAttackGoal.java`, ability/*, effects/*, Megumi*, Shelter*, Spawn*, ClientCharacterSelectionManager.

## Slices

| Slice | Agent | Brief | Artifact | Status |
|---|---|---|---|---|
| A — fear look inversion (critical) | worker | brief-a-fear.md | worker-a-fear.md | done (e1f62fc) |
| B — server entity+gates | worker | brief-b-server.md | worker-b-server.md | done (b234051, e504483) |
| C — render leak (shadow + F3+B hitbox) | worker | brief-c-render.md | worker-c-render.md | done (e1f62fc) |
| D — tests/oracles + red-mutant proofs | tester | brief-d-tests.md | tester-d-tests.md | dispatched |
| E — integrated review | reviewer | brief-e-review.md | reviewer-e.md | pending |
| F — qualityGate + PR + report | self | — | verdict.md, fix-perception.md | pending |

## Tasks ↔ slices map

1. FearMouseMixin → LocalPlayer.turn ⇒ A
2. Sound sinks (step/fall/swim/thorns) ⇒ B
3. Projectile owner chain + anti-deflect ⇒ B
4. Shadow/hitbox render leak ⇒ C
5. perceives vs canInteract seam ⇒ B
6. Entity misc (day-roll, RollSeed, brain.tick alive, @Override, LookAtPlayerGoal) ⇒ B
7. Tests + red mutants ⇒ workers write own; tester D verifies + fills gaps
Not fixed by design: #90 п.8 «урон не-видящему vs AC» — owner decision, report only.
| A fear look-inversion | worker | done | worker-a-fear.md | retargeted to LocalPlayer.turn args; 6 tests green; e1f62fc |
| render leak shadow+hitbox | worker | done | worker-c-render.md | isInvisible+displayFireAnimation flags via CurseRenderGate.hiddenFromView, no mixin |
| server perception B1-B4 | worker | done | worker-b-server.md | sound sink funnel, owner-chain gates, interacts seam, entity misc + oracles |
