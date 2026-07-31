# CurseLink Payload Bounds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound `CurseLinkOptionsPayload` at its existing S2C codec boundary without changing valid wire bytes, while leaving semantic unknown-id validation blocked under Option A.

**Architecture:** Keep `CurseLinkOptionsPayload` as the sole owner of defensive limits. Decode the count before allocating, read technique ids with an explicit string cap, drop only entries whose fully-read strings fail `ResourceLocation` syntax, and let codec-level failures reject the whole payload. Validate both bounds before the existing writer emits any bytes.

**Tech Stack:** Fabric 1.21.8, Minecraft 1.21.8 `RegistryFriendlyByteBuf`, Mojang `StreamCodec`, Java 21, JUnit 5 with fabric-loader-junit.

## Global Constraints

- Use Option A; do not create a supported-technique-id registry.
- Preserve the byte format of valid payloads.
- `MAX_ENTRIES = 64` and `MAX_TECHNIQUE_ID_LENGTH = 256` are defensive bounds, not natural product limits.
- Reject negative or over-cap counts before list allocation.
- Reject over-length strings as a whole payload; do not continue after codec-level string failure.
- Drop syntactically malformed `ResourceLocation` entries after their complete string was consumed, continue with later entries, and log once per payload.
- Refuse invalid writer input; never truncate.
- Do not touch other payloads, UI pagination, VFX, Megumi, Nobara audio, issue #48, or issue #20 closure state.

---

### Task 1: Add real codec regression tests

**Files:**
- Create: `src/test/java/jujutsu/mod/network/CurseLinkOptionsPayloadTest.java`
- Reference: `src/test/java/jujutsu/mod/network/SelectionPayloadCodecTest.java`

**Interfaces:**
- Consumes: `CurseLinkOptionsPayload.STREAM_CODEC`, `RegistryFriendlyByteBuf`, and the public `Entry` record.
- Produces: focused tests covering every required rejection and round-trip path.

- [x] **Step 1: Write the failing tests**

Use the real production codec and a `RegistryFriendlyByteBuf` backed by `Unpooled.buffer()` with `RegistryAccess.EMPTY`. Cover negative count, over-cap count, over-length technique id, malformed-id drop, malformed-id alignment, writer refusal for both bounds, empty/one-entry/max-entry round trips, byte-identical valid re-encode, and zero readable bytes after valid decode.

- [x] **Step 2: Run the focused test before the production change**

Run:

```powershell
./gradlew.bat test --tests jujutsu.mod.network.CurseLinkOptionsPayloadTest --no-daemon --max-workers=1 --no-watch-fs
```

Expected: RED because the current decoder preallocates from the untrusted count, has no explicit string cap, propagates malformed ids instead of dropping them, and the writer accepts over-bound values.

### Task 2: Implement the bounded production codec

**Files:**
- Modify: `src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java`

**Interfaces:**
- Consumes: existing list-of-entry wire layout and `ResourceLocation.parse` syntax validation.
- Produces: unchanged valid encoding plus bounded decoding/encoding behavior for all failure-policy rows.

- [x] **Step 1: Add documented defensive constants**

Declare `public static final int MAX_ENTRIES = 64` and `public static final int MAX_TECHNIQUE_ID_LENGTH = 256` beside the payload codec, with a comment stating that current curse-link state has no natural maximum and these values only bound untrusted network input.

- [x] **Step 2: Bound the decoder**

Read the VarInt, throw `IllegalArgumentException` when it is negative or greater than `MAX_ENTRIES`, then use an unsized `new ArrayList<>()`. For each entry read the two UUIDs and `buffer.readUtf(MAX_TECHNIQUE_ID_LENGTH)`. Catch only `ResourceLocationException` from `ResourceLocation.parse` after the string has been fully read; omit that entry and emit one warning for the payload. Allow the `readUtf` codec exception to escape so the whole payload is rejected.

- [x] **Step 3: Bound the writer**

Before writing the count, throw `IllegalArgumentException` when `entries.size()` exceeds `MAX_ENTRIES`. Before each technique string is written, throw the same exception when its textual id length exceeds `MAX_TECHNIQUE_ID_LENGTH`. Write the existing UUID/string sequence unchanged for valid entries.

- [x] **Step 4: Run the focused tests**

Run the same focused Gradle test. Expected: GREEN, including full consumption for valid payloads and preservation of the following entry after a malformed id.

### Task 3: Record the narrowed contract

**Files:**
- Modify: `docs/KNOWN_ISSUES.md` E2
- Modify: `SESSION.md`
- Modify: `docs/FIX_PLAN_MEGUMI_AND_CURSELINK.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Networking.md` only if its payload contract needs a factual update

- [x] **Step 1: Narrow E2**

Record that count bounds, string bounds, malformed syntax filtering, and writer refusal are implemented under Option A. Keep E2 open because a well-formed unknown technique id cannot be rejected without a canonical supported-id catalog.

- [x] **Step 2: Rewrite the active handoff**

Record the branch/base, confirmed producer and registration facts, defensive constants and failure matrix, focused tests, verification commands, the unknown-id gap, and the draft PR link after creation. Do not add a volatile current-head line.

- [x] **Step 3: Add a short factual PR B status to the existing fix plan**

Do not rewrite the historical plan or change unrelated sections.

### Task 4: Verify, commit, push, and open the draft PR

- [x] **Step 1: Run the required checks**

Run focused tests, `git diff --check`, `auditDocumentation`, `qualityGate`, and `build`; record the build JAR SHA-256 if produced.

- [x] **Step 2: Perform the required red mutations**

Temporarily remove each count/string bound, restore trusted preallocation, change over-length rejection to dropping, remove malformed-entry filtering, and change writer refusal to silent truncation one at a time. Run the matching focused test after each mutation, record the failing test/output, then restore the production code. Do not commit mutations.

- [x] **Step 3: Commit small conventional changes**

Use English conventional commits for tests, production, and docs where each change is independently reviewable.

- [ ] **Step 4: Push and create a new draft PR**

Push `fix/curselink-payload-bounds` and create a draft PR titled `fix(network): bound CurseLink options payload`, with Option A, bounds, failure matrix, tests, RED evidence, the remaining unknown-id gap, and `Refs #20` rather than `Closes #20`.

- [ ] **Step 5: Stop after checking available CI**

Do not merge, mark ready, close issue #20, create Option B, or touch issue #48 or unrelated payloads.
