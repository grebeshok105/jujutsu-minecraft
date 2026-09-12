# Worker dispatch — shared context block (paste into every block task)

## Common context

# Goal
Execute ONE block of the cursed-spirits implementation plan, rule-of-four Phase 2, in the Fabric 1.21.8 mod at `D:/WorkFlow/jujutsu-minecraft` (branch `feat/cursed-spirits`).

# Required reading before touching anything
- Your block in `.superpowers/rule-of-four/cursed-spirits/implementation-plan.md` (read the WHOLE plan: frozen contracts, ownership matrix, decision records — your block compiles against the frozen names).
- Evidence if you need it: `scout-1-report.md` (combat integration), `scout-2-report.md` (repo conventions + verified 1.21.8 signatures), `scout-3-report.md` (asset pack), `scout-4-report.md` (acceptance/infra), `analysis/rig_audit.py` (clip-vs-model bone audit for the 9 frozen variants).
- Port sources for client work: `.superpowers/rule-of-four/cursed-spirits/decompiled/net/mcreator/sonsofsins/**`.

# Hard rules
- Server-authoritative gameplay; nothing client-only in `src/main`; public MC/Fabric APIs only; no new mixins.
- Touch ONLY your ownership row in the plan's file-ownership matrix. Need a file outside it? Stop and report it as `unresolved` — do not edit.
- Scope-Growth stop rule: if you find you need a new component/dependency/design decision, or the blast radius grows, STOP, write a `Scope-Growth` section in your report, and return the question to Main.
- Balance/presentation numbers: only in the plan's frozen places (`CursedSpiritProfile`, `CursedSpiritVariant`); no magic constants in logic.
- Tests: new behavior ships with tests; every new check must be observed failing on a mutation (red-proof) before it is trusted. Record the mutation + failure in the report.
- Builds: always `export JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current` first; use `gradlew.bat ... --no-daemon --max-workers=1 --no-watch-fs`. Do NOT run full `qualityGate` (Main runs it at the barrier); run only your focused checks.
- Commits: small, English, conventional (`feat(cursed-spirits): ...`), on `feat/cursed-spirits`. Never push.

# Report contract (write to `.superpowers/rule-of-four/cursed-spirits/block-N-report.md`)
- What was done; files created/modified (paths); tests added (paths) + exact commands with observed results.
- For EVERY closed acceptance item: `Факт: <command/observation> → <result with numbers, file:line>`. PASS without evidence is not a pass.
- Red-proof records (mutation → observed failure → restored).
- Risks, unresolved items, Scope-Growth section if triggered.
