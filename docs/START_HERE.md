# Start here

Picking this project up on a new machine, or handing it to an agent that has never seen it. Ten minutes, in order.

## 1. Get the code

There is nothing to unpack. Everything lives in git, and `main` is the only branch — anything else you may find locally is a duplicate and is explained in SESSION.md.

```bash
git clone https://github.com/grebeshok105/jujutsu-minecraft.git
cd jujutsu-minecraft
```

You need **JDK 21** and nothing else. Gradle arrives through the wrapper; Minecraft, Fabric and GeckoLib are downloaded on the first build. That first build takes several minutes and is mostly download.

## 2. Prove the checkout works

One command owns the word "verified":

```bash
./gradlew qualityGate
```

On Windows use `gradlew.bat`. It compiles both source sets, runs every test, audits the documentation and audits the test configuration itself. If it is green, the checkout is sound. If it is red, read the failure before doing anything else — it is designed to say what broke and why.

To produce the mod jar at `build/libs/jujutsumod-1.0.0.jar`:

```bash
./gradlew assemble
```

## 3. What to read, in this order

| Read | For |
|---|---|
| **AGENTS.md** | The rules. Product direction, the vessel seam, technical constraints, the verification policy. This is the contract — read it before touching anything. |
| **SESSION.md** | Where the work actually is. What landed recently, what is unverified, what is deliberately left alone. Read it second, every time. |
| **docs/KNOWN_ISSUES.md** | Accepted tradeoffs, open debt, and the limits of the build-time gate. Read before "fixing" something that was decided on purpose. |
| **docs/BUILDING_IN_SANDBOX.md** | The full command recipe and the in-game smoke checklist. |
| **Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md** | The architecture index. Follow its links for any subsystem. |

Authority order when two documents disagree: **current code and passing tests first**, then AGENTS.md, then SESSION.md, then the Codex. A document that contradicts the code is a bug in the document.

## 4. Where things are

```
src/main/java        server and shared code — a dedicated server loads this
src/client/java      rendering, HUD, keybinds, menus, particles
src/test/java        tests; the architecture rules are under jujutsu/mod/architecture
src/main/resources   models, textures, sounds, lang files, mixin config
Jujutsu Kaizen/…     the versioned Codex: architecture and system notes
docs/                current-only project documentation
tools/audit_docs.py  the documentation audit, run inside qualityGate
.claude/skills/      repo-local agent skills, versioned with the code they describe
```

The source sets are split. `src/client` may use `src/main`; the reverse is a compile error, on purpose.

## 5. Adding a character

Use the **`add-vessel`** skill in `.claude/skills/add-vessel/`. It owns the whole procedure — design, scaffold, abilities, presentation, tests, documentation — plus the readiness checklist and the list of things a vessel must never do. It is versioned alongside the architecture it describes, so it is authoritative over any workflow described elsewhere.

## 6. Two rules that are easy to trip over

**Every new check ships with proof it can fail.** Break the thing the check guards, record the mutation and the failure message in the commit body, then restore. A check only ever seen green may be checking nothing. This is enforced socially, not mechanically, and it is the reason the test suite is worth trusting.

**Run the gate after staging, not before.** The documentation audit reads the git index, so `./gradlew qualityGate` before `git add` answers a question about a tree that is about to change. Prefer explicit paths over `git add -A`; a blanket add once swept forty untracked scratch files into a commit and reddened CI.

## 7. What a green build does not prove

Nothing in the suite constructs a `ServerLevel`. The gate proves the shape of the code, the boundaries between characters, and the pure logic reachable without a world. It proves nothing about how anything feels, renders or behaves in game. That still requires launching the client and working through the checklist in docs/BUILDING_IN_SANDBOX.md.
