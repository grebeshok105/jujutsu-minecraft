# AGENTS.md — Jujutsu Minecraft

Fabric mod (Minecraft 1.21.8, Java 21, mod id `jujutsumod`) inspired by the Jujutsu Kaisen idea space: anime ability fantasy, natively embedded in Minecraft.

## 1. What we are building

A full jujutsu mod whose kits **feel** like the anime — mechanics, timings, interactions, animations, VFX — while staying readable and genuinely playable inside Minecraft. Recognizability over literal copying: never sacrifice gameplay to mirror a scene. A kit that is faithful but unplayable is a failed kit.

The vertical slices (Nobara, Todo, Megumi) already proved the architecture holds. The project is now in **systemic development**: starting small, every feature must strengthen the whole — reuse existing mechanisms, extend shared systems, never grow a disconnected island.

Playable vessels today: see the product snapshot in `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` (code wins if they differ).

## 2. Ability slots

Each vessel owns a small set of high-impact actions. Today that is **three base technique slots, each with a Shift variant — six base key combinations per vessel** (currently R, B, V plus Shift), plus contextual slots a design may claim (technique-weapon left click, double right click, hold/release gestures — see `src/main/java/jujutsu/mod/character/CharacterAbility.java`).

The current key mapping is a default, not architecture: slot ids are append-only wire format, and a vessel design that needs more actions or binds must be able to get them without breaking the seam. Never hard-code the current keys as an eternal rule.

## 3. How user and agent work together

The user supplies the **DESIGN SPEC** — WHAT and WHY: behavior, feel, constraints, edge cases, interactions, acceptance criteria.

The agent owns the technical side — HOW. Before executing any ready spec, a full **implementation plan** must exist. No plan → the agent researches the repo, reads the relevant skills/docs/code, and writes the plan itself. A plan never silently changes the user's design decisions.

A complete spec means no re-brainstorming: settled design decisions are not reopened without cause.

When the user asks to invent something large from scratch — system, vessel, mechanic, VFX direction, UI — with no spec yet, the **brainstorming gate** applies: research, 2–3 approaches, trade-offs, decision alignment, then a final DESIGN SPEC. The implementation plan is the next stage after that.

## 4. Autonomy

The agent works end to end without micromanagement: understand the goal → research the repo → read the relevant project skills → read current docs and context → use available MCP and dev tools → write the implementation plan if missing → implement → write or update tests → run automated checks → launch the game when runtime is touched → verify in game → fix findings → re-verify → self-review → run independent reviews → bring the task to DONE.

Anything answerable through code, docs, git, skills, MCP, LSP, or codegraph is resolved independently. Ordinary technical actions already permitted by this contract need no permission asked. Trivial technical choices never stop execution.

Escalate only real design/product blockers, or forks with fundamentally different behavior or meaning that existing design context cannot resolve.

## 5. Architecture quality

The implementation must fully work, fit the existing project logically, create no duplicate systems, take no shortcut that degrades structure, live in the right place, use existing abstractions where reasonable, and stay extensible. Never pick the shortest path when it litters duplication, one-off crutches, or future cost.

## 6. Hard rules

- Server-authoritative gameplay; rendering, HUD, particles, camera, keybinds, and menus live client-side (`src/client`); nothing client-only in `src/main` — a dedicated server loads it.
- Public Fabric and Minecraft APIs only; no `impl` internals, no deprecated APIs without reason.
- Reuse shared mechanisms before building new ones; one concern, one system.
- No Mixins unless a normal Fabric API or event cannot solve it; each mixin narrow, scoped, recorded in the design.
- Preserve the architectural seams — above all the vessel seam: shared code asks the vessel, never which character the player is. Details live in the Codex and the skill, not here.

## 7. Skills

A matching project skill is a work procedure, not a suggestion: read it and follow it. Vessel work goes through **`add-vessel`** (`.claude/skills/add-vessel/`); its procedure is authoritative and is not duplicated here.

A stable workflow repeated twice or more becomes a project skill on its own, no asking needed. No skill per micro-action: one-off repetitions stay inline.

## 8. GRACE

Project-local only (`.grace-tools/`); a global install must never become a project dependency. `grace-*` skills are read directly from `.claude/skills/grace-*/SKILL.md`; project state under `.grace/` only via `grace-init` with an explicit decision.

## 9. Tools

Prefer the lightest tool that answers. All querying is pre-authorized — on failure, say so once and continue with the repo.

| Tool | For |
|---|---|
| codegraph (`.codegraph/`) | structure, callers, dependencies |
| LSP / symbol search | definitions, references, renames, diagnostics |
| knowledge-rag (`.omp/RULES.md`), VaultMCP when connected | project knowledge; never above current code |
| MCP dev-lane (`mc-world`/`mc-client`, `mcp-lane-launch` skill) | launching and driving the live game |
| git / gh | branches, history, PRs — remote and target come from repo context |
| gradle (`qualityGate`, focused tasks) | compilation, tests, doc audit |

## 10. Verification

Compilation proves nothing. Relevant automated tests must pass, and `qualityGate` must pass fully:

```bash
./gradlew qualityGate
```

New behavioral or architecturally significant logic gets tests; a bugfix gets a regression test where possible; mutation checks apply where meaningful — no theater tests written just to satisfy a rule. A check only ever seen green may be vacuous: prove new checks can fail.

Runtime-affecting changes (gameplay, input, rendering, entities, networking, VFX, UI) require **in-game verification by the agent itself**: launch the game, drive the MCP dev-lane, check acceptance criteria plus edge cases, fix findings, repeat. A green gate alone never finishes such a task.

Large or risky changes require **independent review through subagents** — at minimum architecture and implementation reviews where applicable. Reviewers hunt real problems (spec violations, structural damage, regressions, missing tests), they do not rubber-stamp. Findings are fixed or explicitly rejected with reasons.

## 11. Definition of Done

DONE only when every relevant item holds: DESIGN SPEC fully implemented; implementation plan executed; no known open items; tests written or updated; all relevant tests green; `qualityGate` green; mutation checks green where applicable; launch/runtime check green; in-game verification green where applicable; acceptance criteria checked; independent reviews done; findings fixed; docs and project context updated where the change outdated them; final self-review done; git state clean and complete.

## 12. Git workflow

Git is mandatory. Each self-contained task runs on its own branch/worktree per the current project workflow; changes split into small logical commits (English, conventional-style); each finished task ships as its own PR. PR title and body are in Russian, written for the user, not only developers: what was done, why, key decisions, how it was verified, which tests ran, whether in-game verification happened, known limits.

## 13. Versioning & GitHub releases

Version grows by step of release size: hotfix / small fix → +0.1, major update → +0.5, super release → +1.0.

Every release ships on GitHub with a status attached: alpha, beta or release. Release notes are written for players, not developers — in Russian (like PRs), beautiful and ad-like, emojis welcome, content-side only: what is new and why it is cool, 0% technical part.

## 14. Documentation

Docs stay current with the code: a change that outdated a document updates it in the same task. Before creating a new markdown file, find the existing place for the information. No sprawl, no temp facts in durable files — this file points at context, it does not store it.

Context map: `SESSION.md` (active branch and handoff), `docs/KNOWN_ISSUES.md` (debt and accepted trade-offs), `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` (architecture index), `docs/BUILDING_IN_SANDBOX.md` (commands and smoke checklist), `docs/START_HERE.md` (onboarding), `.omp/RULES.md` (knowledge base), `.claude/skills/` (procedures).

Authority when sources disagree: current code and passing tests → this file → `SESSION.md` → Codex → `KNOWN_ISSUES.md`.
