# Test Architecture Remediation Plan

Status: PROPOSED — no code changes in this pull request

Scope: `src/test/java/jujutsu/mod/**` and the verification half of `build.gradle`.

Owner hierarchy: current code/tests → AGENTS.md → SESSION.md → Codebase Codex → KNOWN_ISSUES.md → this plan. This plan is subordinate to all of them. Where it disagrees with the code, the code wins and this file is wrong.

## Why this exists

A full read of the test tree produced two kinds of finding, and they deserve very different treatment:

1. **Assertions that are green today but do not prove what they claim.** These are actively misleading. A reader sees a passing check and stops worrying.
2. **Structure that makes future mistakes easy** — a hand-maintained task list, a fail-open path helper, floors that no longer track the tree.

Everything below is ordered by that distinction. The first tier is short on purpose: it is the set of things that are wrong *right now*, not things that could become wrong.

### Evidence conventions used here

The test suite in this repository is unusually honest about the limits of its own checks — `SourceBoundaryTripwireTest` calls itself a grep, `VesselBoundaryTest` documents why bytecode cannot see constant-folded reads, and the "Limits of the build-time gate" section of KNOWN_ISSUES.md lists what no structural rule can catch. This plan tries to hold the same standard:

- Claims verified by reading a file on `main` are stated plainly.
- Claims that require a local build, a checkout-wide count, or a Gradle run are marked **UNVERIFIED** and must be confirmed before being acted on.
- No line counts, file counts, or effort estimates appear here unless they were counted rather than guessed.
- Acceptance criteria must be able to fail for the right reason. A criterion that the current, unfixed code already satisfies is not a criterion — see T2.3, where the first draft of this plan got exactly that wrong.
- When reviewers disagree, the repository source or a command result decides the question; otherwise the claim stays **UNVERIFIED**. Of equally supported formulations, choose the narrower one; preserve existing coverage until an independent replacement is demonstrated, and prefer fail-closed behaviour.
- Paths in acceptance criteria come from the current tree. A synthetic path is permitted only when it is labelled as synthetic and tests the path classifier directly.
- Every acceptance criterion states how the unchanged implementation turns red. If a correction proposed by this plan is disproved, delete it rather than restating it more carefully; T2.3 is the recorded example. For all other evidence policy, see `AGENTS.md`.

---

## Tier 1 — Assertions that are false-green

These come first because they are the only items on this list that currently make a false claim.

### T1.1 — `BlackFlashWindowTest`: the chance disjunction proves nothing

The assertion is of the shape:

```java
assert hammer.contains("BLACK_FLASH_CHANCE")
        || profile.contains("BLACK_FLASH_CHANCE = 0.10f");
```

The left operand is true whenever the runtime mentions the constant by name, which it always does. The right operand — the only half that pins the value — is therefore never evaluated. Changing the chance to `0.99f` leaves this test green.

**Fix: two independent assertions, not one.** Deleting the left operand would also work, but reading the constant directly is cleaner than grepping for its declaration text:

```java
assert ProjectJjkNobaraProfile.BLACK_FLASH_CHANCE == 0.10f
        : "Black Flash chance must stay at 10%";

assert hammer.contains("BLACK_FLASH_CHANCE")
        : "hammer runtime must use the centralized Black Flash chance";
```

The first pins the number. The second pins the fact that the runtime uses the shared constant rather than an inline literal. Neither implies the other, so both are needed.

One consequence worth writing down, because this repository already documents the same mechanism elsewhere: `BLACK_FLASH_CHANCE` is a compile-time constant, so the comparison is folded into a literal in the compiled test and the test class keeps no bytecode reference to `ProjectJjkNobaraProfile`. That is harmless — Gradle recompiles the test when the profile changes — but it is exactly the constant-folding blindness that `SourceBoundaryTripwireTest` exists to cover, and nobody should later be surprised that ArchUnit does not see this edge.

**Acceptance:** temporarily change the constant to `0.99f`; the test must fail. Restore it; the test must pass.

### T1.2 — `BlackFlashWindowTest`: ordering checks that pass when the subject is missing

The call-order checks compare raw indices:

```java
runtime.indexOf(A) < runtime.indexOf(B)
```

If `A` is absent its index is `-1`, and `-1 < n` is true, so the check passes precisely when the thing it is guarding has been deleted. The failure mode is asymmetric: losing `B` gives `n < -1` and fails correctly, losing `A` gives a false green.

**Fix:** assert both indices are `>= 0` before comparing them, with a message naming whichever fragment was not found.

**Acceptance:** delete the `A` fragment from the runtime locally; the test must fail with a message that names `A`.

---

## Tier 2 — Structural gaps that let a regression through silently

### T2.1 — `check` maintains its verification task list by hand

`build.gradle` registers the verification programs as individual `JavaExec` tasks in group `verification`, and then lists them a second time inside `tasks.named('check')`. Every new program needs both edits. Miss the second one and the task exists, has `-ea`, looks correct in the build file, and is never run by the gate.

`verifyAssertionsEnabled` cannot catch this. It reads the Gradle task model to confirm each verification `JavaExec` enables assertions — it says nothing about whether anything invokes them. This is the same class of silent-pass problem that `failOnNoDiscoveredTests = true` and `verifyAssertionsEnabled` were each added to close; the third instance is simply still open.

**Fix, in the style already used throughout the file:**

```groovy
tasks.named('check') {
    dependsOn tasks.withType(JavaExec).matching {
        it.group == 'verification'
    }
}
```

The manual list is then deleted. `dependsOn` accepts a task collection. This realizes the matching tasks at configuration time; with the current number of verification programs that cost is not worth avoiding.

`verifyAssertionsEnabled`, `auditDocumentation` and `qualityGate` are ordinary Gradle `Task`s, not `JavaExec` tasks, so the collection does not pull the audit or gate back into `check` and cannot form a cycle through `qualityGate -> check`.

**Acceptance:** the exact Groovy above must be run, not assumed to work.

1. `./gradlew check` and `./gradlew qualityGate` both pass.
2. `verifyAssertionsEnabled` reports the same verification `JavaExec` tasks as before the change.
3. The **set** of verification `JavaExec` tasks that `check` invokes equals the set registered. Compare sets, not counts — a count is satisfied by swapping one task for another.
   - Registered set: `./gradlew verifyAssertionsEnabled`. That task already logs `verifyAssertionsEnabled: ${programs.size()} verification JavaExec tasks all enable assertions`, and it builds that list with the same `tasks.withType(JavaExec).matching { it.group == 'verification' }` expression the wiring will use — so the audit and the wiring cannot disagree about what counts as a verification program.
   - Invoked set: `./gradlew check --dry-run`.
   - Do **not** count lines from `./gradlew tasks --group verification`. That group also holds `verifyAssertionsEnabled`, `auditDocumentation` and `qualityGate`, none of which are verification programs, so its output is not a clean count of `JavaExec` tasks.

### T2.2 — `isInsideVesselPackage` matches any path segment (fail-open)

The helper in `SourceBoundaryTripwireTest` decides vessel ownership with:

```java
path.contains("/" + vesselId + "/")
```

Any directory anywhere in the tree whose name happens to equal a vessel id is treated as that vessel's package and excluded from the shared-code scan. This is fail-open, which is the opposite of how the rest of the suite is built: `everyPackageUnderAVesselParentNamesARegisteredVessel` is deliberately fail-closed, and the pinned sets in `VesselBoundaryTest` are designed to break in both directions.

**Fix — and the fix is the risky part of this item.** A naive tightening can silently stop recognizing legitimate vessel code, which converts a fail-open hole into a fail-open hole *plus* lost coverage. The correct shape is:

- derive the package from the file path relative to its source root (`src/main/java` or `src/client/java`), or read the `package` declaration from the file itself;
- compare that package against the permitted vessel package prefixes under each of `VESSEL_PARENTS`;
- handle both source roots, not just `src/main/java`.

**Acceptance, both directions required:** make the helper directly testable and classify both slash styles.

1. Recognize the live Megumi paths: `character/megumi/MegumiProfile.java`, `character/megumi/vfx/MegumiVfxIds.java`, `client/character/megumi/MegumiClientDefinition.java`, `client/character/megumi/vfx/MegumiVfxRecipes.java`, `client/character/megumi/particle/MegumiShadowMoteParticle.java`, and `client/render/megumi/MegumiDivineDogRenderer.java`. Retain the existing Nobara `character/nobara/projectjjk` and Todo paths too.
2. Reject the synthetic shared path `client/rich/megumi/Anything.java`, plus the live shared paths `vfx/TodoVfxIds.java`, `client/fx/NobaraHudState.java`, and `client/render/ProjectJjkNailRenderer.java`.
3. On the unmodified tree, `sharedProductionCodeNamesNoVesselType` and `noVesselNamesAnotherVesselsType` still pass. The set returned by `sharedProductionFiles()` must not change, so tightening has not silently reclassified a real vessel file as shared.

### T2.3 — Scan-completeness floors no longer track the tree

`VesselBoundaryTest` and `SourceBoundaryTripwireTest` guard against "the scan silently found nothing" with lower bounds. These are **floors**: they must rise as the tree grows, and no upper bound should ever be introduced.

- `vesselTypes.size() >= 2` is a harmless but unhelpful remnant, not a floor to strengthen. `vesselOwnedTypeNames()` inserts one key for every `VESSEL_IDS` entry by construction, so an equality check would be tautological; an absent vessel tree already fails independently at `assertTrue(!names.isEmpty())`. Do not change either check in this tier.
- The real floors are `importOnly(build/classes/java/main, 90)`, `importOnly(build/classes/java/client, 150)`, and `sharedProductionFiles() > 100`. The first two count compiled classes; the third counts `.java` files. They are not comparable values and must be measured separately.

**UNVERIFIED:** the correct new floor values must come from a clean checkout after the Megumi polish branch is integrated, with `./gradlew classes clientClasses`. Do not take numbers for the main/client class counts or the shared-file count from any document, CodeGraph report, or this plan.

**Acceptance.** Stubbing the scan to return an empty list is **not** adequate here, and the first draft of this plan said exactly that and was wrong: the existing floors of 90, 150 and 100 all fail against an empty list too, so satisfying that criterion demonstrates nothing about whether a floor was raised. Test each floor at its new boundary instead.

1. Stub the corresponding scan to return exactly `newFloor - 1` items. The test must fail.
2. Restore the real result, or exactly `newFloor` items. The test must pass.

---

## Tier 3 — Duplicate and misplaced checks

Nothing here is wrong. All of it costs maintenance and makes contracts harder to find.

### T3.1 — Narrow switch checks in `CharacterDefinitionRegistryTest`

`JujutsuCharacters.definition(JujutsuCharacter)` is a switch **expression** with no `default` arm, and its javadoc states the intent outright: *"A registry map plus a test would fail the build; only an exhaustive switch fails compilation."* Adding an enum constant therefore breaks the build at the compiler, before any test runs.

The compiler guarantee has a precondition: the switch has no `default`. A legal catch-all would keep compilation green when a future vessel is added, silently turning the compile-time contract into a fallback. Keep the source assertion that no `default` arm exists; it protects that precondition rather than duplicating the compiler.

The clearest example is this assertion, which pins the *name of a local variable* inside `all()`:

```java
assert registry.contains("JujutsuCharacter[] characters = JujutsuCharacter.values()")
```

Renaming `characters` to `values` changes no behaviour whatsoever and turns the suite red. Meanwhile the property that actually matters — that the sweep and the switch agree — is already covered behaviourally by `assertTheSweepMatchesTheSwitch`.

**Fix:** retain the no-`default` assertion, but delete the per-constant `case` greps and the array-declaration grep. Keep `assertEveryVesselResolves` and `assertTheSweepMatchesTheSwitch`. As a separate small cleanup, rename the remaining method to `assertTheSwitchHasNoCatchAll` and scan text after comments and literals are removed, so prose cannot create a false red.

### T3.2 — Reduce the client-leak checks to two complementary mechanisms

"No client type reaches the server output" is currently asserted by an ArchUnit rule over compiled bytecode, by a source-text scan of the whole tree, and by at least one narrower grep that describes itself as the "focused, named half".

These are **not** redundant in pairs. The bytecode rule cannot see runtime-resolved names or constant-folded reads; the source scan can see forms that vanish at compile time; the narrow grep only produces a friendlier message. Deleting everything except ArchUnit would lose real coverage.

**Target state:** exactly one structural check (ArchUnit, over bytecode) and exactly one honest source tripwire (whole-tree, self-documented as a grep). Add a comment at each surviving check naming what the other one covers, so nobody later deletes the second thinking it is a copy.

**Getting there, in two steps that must not be merged into one:**

1. **Now:** `assertNothingClientOnlyLeakedIn` in `CharacterDefinitionRegistryTest` — a named check over a handful of files, whose own comment describes it as the focused half of a rule ArchUnit already enforces whole. It can be removed as part of this tier.
2. **Blocked on T4.1:** whatever `ProjectSanityTest` asserts in this area. Do **not** cut it here. Which of its checks are genuinely subsumed by ArchUnit is not yet known, and that is precisely the question the T4.1 inventory answers. Removing anything from that file before the inventory exists means deleting checks on a guess, which is how coverage disappears quietly.

### T3.3 — Move contracts to the file that names them

- `VfxTimelineTest` asserts the stability of Nobara VFX cue ids.
- `BlackFlashWindowTest` asserts `CombatStagger` behaviour.

Neither is incorrect; both are undiscoverable. This is the same problem that was already fixed once when the ability wire format was extracted out of `TodoFakeClapTest` into its own JUnit class — apply the same treatment.

---

## Tier 4 — Long-running work, to be done incrementally

### T4.1 — Split `ProjectSanityTest` by topic

It is a single `main()` entry point dispatching into several dozen helper methods in one very large file. The helpers mean it is not a wall of inline code, but the file is still too large to navigate, and any failure stops the whole program at the first tripped assertion.

**Do the inventory before the split, and before any deletion.** Produce a list of every property the file checks, mark which ones ArchUnit or the source tripwire already prove, and only then decide what moves and what is deleted. No assumption should be made about how much is duplicated — that number is not known.

This inventory is a hard prerequisite for the second half of T3.2. Until it exists, nothing is removed from `ProjectSanityTest` on the grounds of duplication.

### T4.2 — Migrate `main()` + `assert` programs to JUnit, one class at a time

The cost of the current form is diagnostic, not correctness: each program halts at its first failed assertion, so a class covering ten scenarios reports one and skips nine, and none of the internal assertions appear as named tests in a report. `-ea` is enforced on the tasks and audited separately, so these programs do run — they just report poorly.

Start with the classes that have the most independent scenarios: `TargetResolverTest`, `CharacterDefinitionRegistryTest`, `CharacterPlayerStateTest`, `CurseLinkRegistryTest`. Migrate and verify one at a time. Do not attempt a single sweeping refactor of every program at once.

Each migrated class leaves the `verification` group, so T2.1 should land first — with the wiring dynamic, a migration is one deletion in `build.gradle` rather than an edit in three places.

**UNVERIFIED:** the per-class effort depends on Fabric Loader classpath behaviour under `useJUnitPlatform()` for these specific classes. Confirm with the first migration before planning the rest.

### T4.3 — Cover `CharacterAbilityExecutor`

The executor is the shared seam every ability passes through — selection, canonical slot, cooldown — and KNOWN_ISSUES.md already records under "Limits of the build-time gate" that catching a vessel that registers a callback into shared dispatch *"needs a unit test over the dispatcher, not a dependency rule"*. That test does not exist as a dedicated class.

**UNVERIFIED:** whether the executor is covered indirectly by other tests. Establish this first with a local `grep -rn CharacterAbilityExecutor src/test` before writing anything; a remote code search was inconclusive.

### T4.4 — Evaluate mutation testing on pure policies only

The dependency-free Megumi policies and `VfxTimeline` are candidates for PIT. `TargetResolver` depends on Minecraft server and geometry types, so it requires separate classpath investigation rather than being grouped with pure policies.

Treat it as an investigation, not a checklist item. A Minecraft/Fabric classpath with transformed classes needs real configuration work, and the mutation targets have to be scoped deliberately. Budget accordingly.

---

## Documentation correction found along the way

The stale *30 verification programs* claim has this verified inventory on the current plan base:

- `docs/KNOWN_ISSUES.md:219` — `Verified 2026-07-26`; the count is stale with a dated verification mark. Its follow-up at `:223` also refers to the same stale number.
- `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Test-and-build-commands.md:13` — `VERIFIED — build.gradle`, and it additionally claims `check depends on all 30`.
- `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md:21` — `VERIFIED`. This is the claim-source index itself, so its stale evidence statement is the highest-priority documentation correction.
- `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md:69` — the third-vessel procedure says there are 30 programs.
- `docs/VESSEL_DEFINITION_REFACTOR.md:284` — it reports 30 green verification programs.

Inventory every exact verification-program count again before changing prose; do not assume this list is permanent. Since `tools/audit_docs.py` validates links, current-document presence and selected MOC metrics only, it does not inspect numeric claims in prose and cannot catch this drift.

Take the number from `./gradlew verifyAssertionsEnabled`, for the reason given in T2.1: it is filtered by the same expression the wiring uses. Do not take it from `./gradlew tasks --group verification`, which lists non-program tasks alongside the programs. The target state is no count in prose: documentation points to that command and says `check` depends on every verification `JavaExec`. Prefer removing such counters to adding another audit parser.

## Order of work

1. Merge this corrected document, then take a new implementation branch after the Megumi polish branch is integrated.
2. T1.1, T1.2, T2.1, the verification-count documentation update, T2.2 and the three real T2.3 floors — each in a reviewable commit.
3. T3.1, T3.2 (first half only), and T3.3 remain deferred; T4.1's inventory still unblocks the second half of T3.2.
4. T4.1 through T4.4 remain incremental, with no deadline implied.

Tier 1 is the only tier that should be considered urgent. Everything after it is maintenance, and none of it justifies a large refactor undertaken for its own sake.
