# VFX Core Authoring Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make VFX Core the repository-mandated and machine-guarded path for every transient combat effect while documenting how the module scales to future characters.

**Architecture:** Keep the existing runtime unchanged. Add a short binding contract to `AGENTS.md`, extend `ProjectSanityTest` with narrow ownership/registration guards, and synchronize the versioned codebase codex. Persistent state visuals remain in real renderers, and a central recipe bootstrap remains deferred until the second character consumer exists.

**Tech Stack:** Java 21, Fabric 1.21.8, Gradle JavaExec assertion tests, Markdown project/codex documentation, Git.

## Global Constraints

- Work only in `D:/WorkFlow/Jujutsu Minecraft/.worktrees/nobara-cinematic-slice` on `codex/nobara-cinematic-slice`; do not edit the dirty main checkout.
- Follow `docs/superpowers/specs/2026-07-10-vfx-core-authoring-contract-design.md` exactly.
- Mandatory transient flow: server-confirmed action -> `VfxCue` -> typed S2C transport -> `VfxDirector` -> `<Character>VfxRecipes` -> director-owned channels.
- Persistent visuals whose lifetime follows a real entity or gameplay state remain in real renderers.
- Do not add dependencies, runtime VFX behavior, shaders, a DSL, preview tooling, reflection registration, or a one-character bootstrap.
- Do not use Computer Use or UI automation. This change does not claim gameplay QA.
- Use TDD for `ProjectSanityTest`: observe RED before changing `AGENTS.md`, then GREEN.
- Keep each meaningful deliverable in a small conventional English commit.

---

### Task 1: Make the VFX Core contract binding and machine-guarded

**Files:**
- Modify: `src/test/java/jujutsu/mod/ProjectSanityTest.java:321-337,552-563`
- Modify: `AGENTS.md` after `## Technical Rules`

**Interfaces:**
- Consumes: existing `ProjectSanityTest` source-tree scanning, `VfxDirector.initialize()`, `NobaraVfxRecipes.register()`, and `JujutsuClientNetworking.registerReceivers()`.
- Produces: `assertVfxCoreAuthoringContractAndOwnership(String clientEntrypoint)`, the mandatory `AGENTS.md` contract, and a structural guard for central receiver/callback ownership and recipe registration.

- [ ] **Step 1: Add the failing architectural assertion**

In `assertVfxDirectorOwnsClientLifecycle()`, add the helper call immediately after the existing client-entrypoint initialization assertion:

```java
		assertVfxCoreAuthoringContractAndOwnership(clientEntrypoint);
```

Remove the now-unnecessary blank line between that method and `assertVfxCoreProvidesReusableChannels()` so existing downstream code citations do not shift.

Add this helper immediately before the class-closing brace, after `assertNoForbiddenImports()`:

```java
	private static void assertVfxCoreAuthoringContractAndOwnership(String clientEntrypoint) throws IOException {
		String agents = Files.readString(ROOT.resolve("AGENTS.md"));
		assert agents.contains("## Mandatory VFX Core Contract") : "AGENTS.md must define the mandatory VFX Core contract";
		assert agents.contains("server-confirmed action -> `VfxCue` -> typed S2C transport -> `VfxDirector` -> `<Character>VfxRecipes` -> director-owned channels")
			: "AGENTS.md must state the complete transient VFX path";
		assert agents.contains("Persistent, stateful visuals") : "AGENTS.md must preserve real renderers for persistent visuals";
		assert agents.contains("Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md")
			: "AGENTS.md must link the detailed VFX Core authoring note";

		Path networkingPath = CLIENT_JAVA.resolve("jujutsu/mod/client/network/JujutsuClientNetworking.java");
		Path directorPath = CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxDirector.java");
		StringBuilder clientSources = new StringBuilder();
		try (Stream<Path> files = Files.walk(CLIENT_JAVA)) {
			for (Path javaFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(javaFile);
				clientSources.append(source).append('\n');
				String normalizedPath = javaFile.toString().replace('\\', '/');
				boolean effectPath = normalizedPath.contains("/client/vfx/") || normalizedPath.contains("/client/fx/");
				if (source.contains("ClientPlayNetworking.registerGlobalReceiver")) {
					assert javaFile.equals(networkingPath) : "Client packet receivers must remain centralized in JujutsuClientNetworking: " + javaFile;
				}
				if (effectPath && (source.contains("WorldRenderEvents.") || source.contains("HudElementRegistry."))) {
					assert javaFile.equals(directorPath) : "Transient world/HUD callbacks must remain centralized in VfxDirector: " + javaFile;
				}
			}
		}

		int directorIndex = clientEntrypoint.indexOf("VfxDirector.initialize()");
		Matcher recipeBootstrap = Pattern.compile("\\b\\w+VfxRecipes\\.(?:register|registerAll)\\(\\)").matcher(clientEntrypoint);
		assert recipeBootstrap.find() : "Client startup must register VFX recipes";
		int recipesIndex = recipeBootstrap.start();
		int receiversIndex = clientEntrypoint.indexOf("JujutsuClientNetworking.registerReceivers()");
		assert directorIndex >= 0 : "Client startup must initialize VfxDirector";
		assert recipesIndex > directorIndex : "Client startup must register recipes after VfxDirector initialization";
		assert receiversIndex > recipesIndex : "Client startup must register recipes before VFX cue receivers";

		Path recipesRoot = CLIENT_JAVA.resolve("jujutsu/mod/client/vfx");
		try (Stream<Path> files = Files.walk(recipesRoot)) {
			for (Path recipeFile : files.filter(path -> path.getFileName().toString().endsWith("VfxRecipes.java")).toList()) {
				String className = recipeFile.getFileName().toString().replace(".java", "");
				assert clientSources.indexOf(className + ".register()") >= 0 : "Unregistered character VFX recipes: " + className;
			}
		}
	}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat testProjectSanity --no-daemon
```

Expected: FAIL with `AGENTS.md must define the mandatory VFX Core contract`. Ownership and registration checks must not be weakened to make the test pass.

- [ ] **Step 3: Add the minimal binding contract to `AGENTS.md`**

Insert this section immediately after `## Technical Rules` and its current bullet list:

```markdown
## Mandatory VFX Core Contract

- Read `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md` before designing, implementing, or reviewing combat visuals.
- Every transient combat effect must use: server-confirmed action -> `VfxCue` -> typed S2C transport -> `VfxDirector` -> `<Character>VfxRecipes` -> director-owned channels.
- Keep gameplay authority on the server. A cue and its recipe may render only; they must never apply damage, marks, cooldowns, targeting, entity spawning, or other gameplay state.
- For each character, keep stable IDs in `<Character>VfxIds` and client scene composition in `<Character>VfxRecipes`. Use `VfxContext` and the director-owned world, particle, sound, HUD, camera/FOV, and first-person channels.
- Persistent, stateful visuals whose lifetime follows a real entity or gameplay state stay in the real entity/state renderer. They may share stable helpers such as `VfxPalette`; do not force them into a transient timeline.
- Do not add per-effect packet receivers, global render/HUD callbacks, HUD or camera singletons, parallel lifecycle managers, or effect-specific mixins as a shortcut around VFX Core.
- If an effect cannot be expressed through existing channels, stop and update the approved design before adding one narrow director-owned channel. Add shared primitives only when two characters need them or when they enforce a global policy such as lifecycle, timing, quality, culling, or budgets.
- When the second character with transient VFX is added, introduce one explicit `JujutsuVfxRecipes.registerAll()` bootstrap that calls each character recipe class. Do not use reflection, classpath scanning, JSON/DSL registration, or a giant effect switch.
- Every new effect requires ID/recipe registration coverage, quality and distance policy, codex updates, `ProjectSanityTest`, `check`, build, and manual visual QA. Compilation alone does not prove effect quality or multiplayer observation.
```

- [ ] **Step 4: Run focused GREEN verification**

Run:

```powershell
.\gradlew.bat testProjectSanity --no-daemon
```

Expected: `ProjectSanityTest passed` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Review and commit the contract guard**

Run:

```powershell
git diff --check
git diff -- AGENTS.md src/test/java/jujutsu/mod/ProjectSanityTest.java
git status --short
```

Expected: only the two intended files are modified and `git diff --check` prints no errors.

Commit:

```powershell
git add -- AGENTS.md src/test/java/jujutsu/mod/ProjectSanityTest.java
git commit -m "test(vfx): enforce core authoring path"
```

---

### Task 2: Synchronize the versioned VFX authoring documentation

**Files:**
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md`

**Interfaces:**
- Consumes: the binding `AGENTS.md` section and `ProjectSanityTest` helper from Task 1.
- Produces: one consistent authoring/scaling description and source-backed maintenance claims for future agents.

- [ ] **Step 1: Record repository enforcement and the second-character bootstrap in `VFX-core.md`**

Add this section after `## Agent authoring contract` and its forbidden shortcuts:

```markdown
## Repository enforcement and roster scaling

`AGENTS.md` makes this authoring path mandatory at repository scope. `ProjectSanityTest` guards the parts that can be checked structurally: packet receiver ownership, transient world/HUD callback ownership, startup ordering, and explicit registration of every `*VfxRecipes` class. The guard intentionally does not ban legitimate C2S gameplay payloads or real entity renderers.

Keep one `<Character>VfxRecipes` class per character so visual language remains local. When a second character becomes a real consumer, add a small explicit `JujutsuVfxRecipes.registerAll()` bootstrap between `VfxDirector.initialize()` and `JujutsuClientNetworking.registerReceivers()`. Do not use reflection or a universal effect switch.

Expand VFX Core only when a primitive is reused by at least two characters, enforces a global policy, or requires a reviewed new director-owned rendering channel. One-off scene composition stays in the character recipe.
```

Update the verification bullets so they cite `ProjectSanityTest.java:321-338` for lifecycle/startup ownership and `ProjectSanityTest.java:564-608` for the repository authoring/registration guard. Verify those planned anchors with:

```powershell
rg -n "assertVfxDirectorOwnsClientLifecycle|assertVfxCoreAuthoringContractAndOwnership|assertVfxCoreProvidesReusableChannels" src/test/java/jujutsu/mod/ProjectSanityTest.java
```

Use the emitted line numbers in the note; do not retain stale ranges.

- [ ] **Step 2: Strengthen the next-character checklist**

In `How-to-add-next-character.md`:

- Add reading the mandatory `AGENTS.md` VFX contract and `04-client-vfx/VFX-core.md` before visual design.
- Keep the existing `<Character>VfxIds` -> server `VfxCue` -> Java recipe workflow.
- Add the explicit rule that the second character introduces `JujutsuVfxRecipes.registerAll()` and later characters add one call there.
- Add the shared-core threshold: reuse by two characters, global policy, or a reviewed new channel.
- State that new per-character receivers, render/HUD callbacks, lifecycle managers, and effect-specific mixins fail the repository contract.

Use this exact checklist row for registration:

```markdown
| Registration | `*VfxRecipes.register()` is reachable through client startup; from the second character onward, add it to explicit `JujutsuVfxRecipes.registerAll()` |
```

- [ ] **Step 3: Update risk and claim evidence**

Change risk `R8` in `Risks-and-tech-debt.md` to state that future bypass risk is mitigated by the mandatory `AGENTS.md` contract plus structural `ProjectSanityTest` ownership/registration guards. Add a guardrail stating that the tests use a narrow allowlist and must not be broadened into a ban on legitimate gameplay networking or entity renderers.

Add this claim to the VFX section of `Claim-Source-Index.md`:

```markdown
| transient combat VFX have a repository-mandated authoring path; receiver/callback ownership, startup order, and character recipe registration are structurally guarded | `AGENTS.md:76-86`; `ProjectSanityTest.java:564-608` | VERIFIED |
```

Verify the planned exact anchors after editing:

```powershell
rg -n "^## Mandatory VFX Core Contract|^## Dependency Policy" AGENTS.md
rg -n "assertVfxCoreAuthoringContractAndOwnership|private static void assertNoForbiddenImports" src/test/java/jujutsu/mod/ProjectSanityTest.java
```

Expected: the mandatory contract occupies `AGENTS.md:76-86`; `assertNoForbiddenImports` remains at line 552; the new helper starts at line 564 and ends at line 608. If formatting changes those anchors, correct every affected codex citation before committing rather than leaving stale line numbers.

- [ ] **Step 4: Verify documentation consistency**

Run:

```powershell
rg -n "server-confirmed action|JujutsuVfxRecipes.registerAll|Persistent, stateful visuals|ProjectSanityTest" AGENTS.md "Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md"
.\gradlew.bat testProjectSanity --no-daemon
git diff --check
```

Expected: every document describes the same transient/persistent split and bootstrap rule; `ProjectSanityTest passed`; no whitespace errors.

- [ ] **Step 5: Commit the synchronized codex**

```powershell
git add -- "Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md" "Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md"
git commit -m "docs(vfx): mandate core authoring workflow"
```

---

### Task 3: Verify, review, and update the canonical session handoff

**Files:**
- Modify: `docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md`

**Interfaces:**
- Consumes: Tasks 1-2, the approved design spec, and the implementation plan.
- Produces: final verification evidence, independent review results, exact commit ledger, and a complete next-chat recovery packet.

- [ ] **Step 1: Run final terminal-only verification**

Run separately and record the exact result of each command:

```powershell
.\gradlew.bat testProjectSanity --no-daemon
.\gradlew.bat check --no-daemon
.\gradlew.bat build --no-daemon -x test
git diff --check
git status --short --branch
```

Expected: all Gradle commands end with `BUILD SUCCESSFUL`; `ProjectSanityTest passed`; no diff-check errors. No runtime source or assets changed, so this governance-only task does not replace the installed gameplay jar or claim manual gameplay QA.

- [ ] **Step 2: Request independent standards and spec reviews**

Use `requesting-code-review` or `code-review` with two read-only reviewers over `eb20dda..HEAD`:

- Standards reviewer: validate repository conventions, guard narrowness, false-positive risk, TDD evidence, documentation citations, and no unrelated changes.
- Spec reviewer: compare the implementation against `docs/superpowers/specs/2026-07-10-vfx-core-authoring-contract-design.md`, especially the transient/persistent split, deferred bootstrap, and no-runtime-change boundary.

Do not use Computer Use. If a reviewer reports a concrete issue, reproduce it, apply the smallest fix, rerun the relevant focused and final commands, and commit the fix separately before repeating that review.

- [ ] **Step 3: Append the completed authoring-contract pass to the canonical handoff**

Add a dated section covering:

- Why the pass was requested: future agents needed a mandatory and scalable VFX Core workflow.
- Approved choice: all transient combat effects use VFX Core; persistent entity/state visuals stay in real renderers.
- Exact files changed and the reason for each.
- RED/GREEN evidence from Task 1.
- Exact final verification command results from Step 1.
- Exact review findings and their disposition.
- The output of `git log --oneline eb20dda..HEAD` as the new commit ledger.
- Remaining limitations: no new gameplay behavior, no Computer Use, and manual gameplay/two-client QA remains unchanged from the existing handoff.
- Resume instruction: future character agents must read `AGENTS.md`, the design spec, this plan, and `04-client-vfx/VFX-core.md` before implementing visual effects.

Verify the handoff contains no stale action claiming the contract still needs implementation:

```powershell
rg -n "Mandatory VFX Core Contract|authoring contract|ProjectSanityTest|manual gameplay|Computer Use" docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md
git diff --check
```

- [ ] **Step 4: Commit the handoff and prove a clean finish**

```powershell
git add -- docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md
git commit -m "docs(session): record vfx authoring contract"
git status --short --branch
git log --oneline -8
```

Expected: clean `codex/nobara-cinematic-slice` worktree, with the plan, implementation, codex, review fixes if any, and handoff commits visible in the final log.
