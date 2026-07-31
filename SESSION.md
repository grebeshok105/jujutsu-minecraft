# Session Handoff - CurseLink Payload Bounds

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/fix-curselink-payload-bounds`
- Branch: `fix/curselink-payload-bounds`
- Base: `da309cfab9c01bca1525f468039c674644ee0f27` (`fix(megumi): stabilize Divine Dog pounce and shadows`), the squash merge of PR #47.
- Scope: PR B, issue #20 only. Divine Dogs, Megumi VFX, Nobara audio, issue #48 and every other payload remain untouched.
- PR: [#49](https://github.com/grebeshok105/jujutsu-minecraft/pull/49), `OPEN` and `draft`.

## Confirmed code facts

- `CurseLinkOptionsPayload` is registered as an existing S2C payload in `JujutsuNetworking` and uses `CustomPacketPayload.codec(write, read)`.
- `SelfResonanceRuntime` is the real producer. It sends the participant's current `CurseLinkRegistry` list when selection is needed; `CurseLinkSelectionScreen` receives the decoded list through `JujutsuClientNetworking`.
- The registry and participant list have no natural maximum. `MAX_ENTRIES` is therefore defensive rather than a product limit.
- There is no canonical catalog of supported technique ids. `ResourceLocation.parse` provides syntax validation only; a well-formed unknown id remains accepted under Option A.
- Codec-level exceptions from `readUtf(MAX_TECHNIQUE_ID_LENGTH)` are allowed to escape the existing stream codec, so the whole payload is rejected. Only the fully consumed string-to-`ResourceLocation` parse step has a controlled drop path.

## Option A

Option A was accepted in [issue #20 comment](https://github.com/grebeshok105/jujutsu-minecraft/issues/20#issuecomment-5143288156): implement bounded entry count, bounded technique-id strings, malformed syntax handling, writer-side refusal and codec regression tests. Unknown-id validation is explicitly **BLOCKED** until a canonical supported-id catalog exists. No allowlist or registry was added, and issue #20 remains open.

## Defensive bounds

- `MAX_ENTRIES = 64`: finite protection for list allocation and client UI construction; no natural maximum exists in current curse-link state.
- `MAX_TECHNIQUE_ID_LENGTH = 256`: finite per-field wire budget, well above current namespaced ids; not a semantic or product limit.

## Failure-policy matrix

| Input | Result |
|---|---|
| negative count | reject entire payload before list allocation |
| count above `MAX_ENTRIES` | reject entire payload before list allocation |
| technique-id string above `MAX_TECHNIQUE_ID_LENGTH` | reject entire payload; do not continue after codec-level length failure |
| syntactically malformed `ResourceLocation` after full string read | drop only that entry, continue aligned decoding, log once per payload |
| well-formed unknown technique id | accept under Option A; semantic validation remains blocked |
| writer list above `MAX_ENTRIES` | throw before writing; never truncate |
| writer technique id above `MAX_TECHNIQUE_ID_LENGTH` | throw before writing; never truncate |

## Tests and RED evidence

- `CurseLinkOptionsPayloadTest` uses the real production `STREAM_CODEC` for raw malformed buffers, rejection paths, alignment, round trips and byte-identical re-encoding. It also pins the no-trusted-preallocation source invariant because allocation capacity is not observable through the public codec value seam.
- Focused result after restoration: 13 tests passed.
- Initial RED: test compilation failed before the production constants existed.
- Mutation RED evidence: removing count bounds failed both count tests; restoring `readUtf()` failed the over-length test; trusted `new ArrayList<>(size)` failed the allocation invariant; dropping over-length codec failures failed whole-payload rejection; removing malformed filtering failed both malformed-entry tests; removing writer count refusal failed its writer test; removing explicit writer string refusal failed its writer exception-contract test.

## Verification

- Focused `CurseLinkOptionsPayloadTest`: 13 tests passed.
- `./gradlew.bat auditDocumentation --no-daemon --max-workers=1 --no-watch-fs`: `BUILD SUCCESSFUL`; MOC metrics report 68 test Java files.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs`: `BUILD SUCCESSFUL`; all 31 verification JavaExec tasks enable assertions.
- `./gradlew.bat build --no-daemon --max-workers=1 --no-watch-fs`: `BUILD SUCCESSFUL`; produced `build/libs/jujutsumod-1.0.0.jar`.
- Build JAR SHA-256: `B634613C4176A911CDE136C0961E381C7D37CA7B647BE70FE282F5411551FA91`.
- No in-game smoke or JAR installation is required for this codec-only issue.
- Valid payload wire format remains the existing VarInt count followed by UUID, UUID and UTF technique-id string per entry.

## Remaining gap

Issue #20 is partially addressed, not closed. A well-formed unknown technique id cannot be rejected until the project defines a canonical supported-id catalog. Do not create Option B or close the issue in this PR.
