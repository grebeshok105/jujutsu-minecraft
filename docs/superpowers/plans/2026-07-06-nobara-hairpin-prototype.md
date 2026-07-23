# Nobara Hairpin Prototype Implementation Plan

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first in-game Nobara Hairpin cinematic VFX prototype for Fabric `1.21.8`.

**Architecture:** Keep the slice small: server command chooses a target and nail positions, sends one S2C payload, and the client plays a deterministic Hairpin timeline. Visuals are local: custom particles, a tiny world renderer for tracer/ring geometry, a HUD impact flash, and registered custom sound events.

**Tech Stack:** Minecraft `1.21.8`, Fabric Loader `0.19.3`, Fabric API `0.136.1+1.21.8`, Java `21`, Mojang mappings, no new runtime VFX libraries.

## Global Constraints

- Do not use GitHub MCP.
- Do not add LibsFX, Veil, Satin, Lodestone, or ParticleAnimationLib as dependencies.
- Keep server-authoritative trigger logic in `src/main`.
- Keep particles, HUD, renderer, and local playback in `src/client`.
- Use Fabric public APIs only; never import `net.fabricmc.fabric.impl.*`.
- Register typed CustomPayload networking; send one event per Hairpin scene, not per particle.
- Commit after each meaningful verified change.
- Use Java 21 for Gradle commands: `JAVA_HOME=D:\WorkFlow\Minecraft\jdk-21.0.11+10`.

---

### Task 1: Add testable Hairpin timing model

**Files:**
- Modify: `build.gradle`
- Create: `src/main/java/jujutsu/mod/fx/HairpinTimeline.java`
- Create: `src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java`

**Interfaces:**
- Produces: `HairpinTimeline.phaseAt(long elapsedMillis)`, `HairpinTimeline.progressInPhase(long elapsedMillis)`, `HairpinTimeline.totalDurationMillis()`.
- Consumes: none.

- [ ] **Step 1: Add a self-test task and failing test**

Add to `build.gradle` after the `dependencies` block:

```groovy
tasks.register('testHairpinTimeline', JavaExec) {
	group = 'verification'
	description = 'Runs assertion-based tests for the Hairpin timeline model'
	dependsOn tasks.named('testClasses')
	classpath = sourceSets.test.runtimeClasspath
	mainClass = 'jujutsu.mod.fx.HairpinTimelineTest'
	jvmArgs '-ea'
}
```

Create `src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java`:

```java
package jujutsu.mod.fx;

public final class HairpinTimelineTest {
	private HairpinTimelineTest() {}

	public static void main(String[] args) {
		assertPhaseBoundaries();
		assertProgressClamps();
		System.out.println("HairpinTimelineTest passed");
	}

	private static void assertPhaseBoundaries() {
		assert HairpinTimeline.phaseAt(0) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(179) == HairpinTimeline.Phase.PREP_FREEZE;
		assert HairpinTimeline.phaseAt(180) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(239) == HairpinTimeline.Phase.HAMMER_SNAP;
		assert HairpinTimeline.phaseAt(240) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(559) == HairpinTimeline.Phase.NAIL_IGNITION;
		assert HairpinTimeline.phaseAt(560) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(899) == HairpinTimeline.Phase.HAIRPIN_BLOOM;
		assert HairpinTimeline.phaseAt(900) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1799) == HairpinTimeline.Phase.AFTERGLOW;
		assert HairpinTimeline.phaseAt(1800) == HairpinTimeline.Phase.DONE;
	}

	private static void assertProgressClamps() {
		assert HairpinTimeline.progressInPhase(-10) == 0.0f;
		assert HairpinTimeline.progressInPhase(0) == 0.0f;
		assert closeTo(HairpinTimeline.progressInPhase(90), 0.5f);
		assert HairpinTimeline.progressInPhase(1800) == 1.0f;
	}

	private static boolean closeTo(float actual, float expected) {
		return Math.abs(actual - expected) < 0.0001f;
	}
}
```

- [ ] **Step 2: Verify red**

Run:

```bat
cmd.exe /c gradlew.bat testHairpinTimeline --no-daemon
```

Expected: compile failure because `HairpinTimeline` does not exist.

- [ ] **Step 3: Implement timeline**

Create `src/main/java/jujutsu/mod/fx/HairpinTimeline.java`:

```java
package jujutsu.mod.fx;

public final class HairpinTimeline {
	public static final int PREP_FREEZE_MS = 180;
	public static final int HAMMER_SNAP_MS = 60;
	public static final int NAIL_IGNITION_MS = 320;
	public static final int HAIRPIN_BLOOM_MS = 340;
	public static final int AFTERGLOW_MS = 900;

	private HairpinTimeline() {}

	public enum Phase {
		PREP_FREEZE,
		HAMMER_SNAP,
		NAIL_IGNITION,
		HAIRPIN_BLOOM,
		AFTERGLOW,
		DONE
	}

	public static int totalDurationMillis() {
		return PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS + HAIRPIN_BLOOM_MS + AFTERGLOW_MS;
	}

	public static Phase phaseAt(long elapsedMillis) {
		if (elapsedMillis < 0) {
			return Phase.PREP_FREEZE;
		}
		long cursor = elapsedMillis;
		if (cursor < PREP_FREEZE_MS) return Phase.PREP_FREEZE;
		cursor -= PREP_FREEZE_MS;
		if (cursor < HAMMER_SNAP_MS) return Phase.HAMMER_SNAP;
		cursor -= HAMMER_SNAP_MS;
		if (cursor < NAIL_IGNITION_MS) return Phase.NAIL_IGNITION;
		cursor -= NAIL_IGNITION_MS;
		if (cursor < HAIRPIN_BLOOM_MS) return Phase.HAIRPIN_BLOOM;
		cursor -= HAIRPIN_BLOOM_MS;
		if (cursor < AFTERGLOW_MS) return Phase.AFTERGLOW;
		return Phase.DONE;
	}

	public static float progressInPhase(long elapsedMillis) {
		Phase phase = phaseAt(elapsedMillis);
		if (phase == Phase.DONE) {
			return 1.0f;
		}
		long start = phaseStartMillis(phase);
		int duration = phaseDurationMillis(phase);
		if (duration <= 0) {
			return 1.0f;
		}
		float progress = (elapsedMillis - start) / (float) duration;
		return Math.max(0.0f, Math.min(1.0f, progress));
	}

	public static long phaseStartMillis(Phase phase) {
		return switch (phase) {
			case PREP_FREEZE -> 0L;
			case HAMMER_SNAP -> PREP_FREEZE_MS;
			case NAIL_IGNITION -> PREP_FREEZE_MS + HAMMER_SNAP_MS;
			case HAIRPIN_BLOOM -> PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS;
			case AFTERGLOW -> PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS + HAIRPIN_BLOOM_MS;
			case DONE -> totalDurationMillis();
		};
	}

	public static int phaseDurationMillis(Phase phase) {
		return switch (phase) {
			case PREP_FREEZE -> PREP_FREEZE_MS;
			case HAMMER_SNAP -> HAMMER_SNAP_MS;
			case NAIL_IGNITION -> NAIL_IGNITION_MS;
			case HAIRPIN_BLOOM -> HAIRPIN_BLOOM_MS;
			case AFTERGLOW -> AFTERGLOW_MS;
			case DONE -> 0;
		};
	}
}
```

- [ ] **Step 4: Verify green and commit**

Run:

```bat
cmd.exe /c gradlew.bat testHairpinTimeline --no-daemon
```

Expected: `HairpinTimelineTest passed` and `BUILD SUCCESSFUL`.

Commit:

```bat
git add build.gradle src/main/java/jujutsu/mod/fx/HairpinTimeline.java src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java
git commit -m "feat(fx): add Hairpin timeline model"
```

---

### Task 2: Add Hairpin payload, networking, and server command

**Files:**
- Modify: `src/main/java/jujutsu/mod/JujutsuMod.java`
- Create: `src/main/java/jujutsu/mod/network/HairpinFxPayload.java`
- Create: `src/main/java/jujutsu/mod/network/JujutsuNetworking.java`
- Create: `src/main/java/jujutsu/mod/command/JujutsuCommands.java`

**Interfaces:**
- Consumes: `JujutsuMod.id(String)`.
- Produces: `/jujutsu hairpin` server command and `HairpinFxPayload` S2C payload.

- [ ] **Step 1: Implement payload with manual StreamCodec**

Create `HairpinFxPayload` with `seed`, target coordinates, and four nail positions. Use a manual codec so the code stays independent of list codec naming changes.

- [ ] **Step 2: Register payload and command from main init**

`JujutsuMod.onInitialize()` calls `JujutsuNetworking.registerPayloads();` and `JujutsuCommands.register();`.

- [ ] **Step 3: Verify compile and commit**

Run:

```bat
cmd.exe /c gradlew.bat compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Commit:

```bat
git add src/main/java/jujutsu/mod/JujutsuMod.java src/main/java/jujutsu/mod/network src/main/java/jujutsu/mod/command
git commit -m "feat(fx): add Hairpin trigger payload"
```

---

### Task 3: Add client playback shell and receiver

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/JujutsuModClient.java`
- Create: `src/client/java/jujutsu/mod/client/fx/HairpinPlayback.java`
- Create: `src/client/java/jujutsu/mod/client/fx/HairpinPlaybackManager.java`
- Create: `src/client/java/jujutsu/mod/client/network/JujutsuClientNetworking.java`

**Interfaces:**
- Consumes: `HairpinFxPayload`, `HairpinTimeline`.
- Produces: client receiver that starts a local playback.

- [ ] **Step 1: Register the receiver**

Register `ClientPlayNetworking.registerGlobalReceiver(HairpinFxPayload.TYPE, ...)` in client init.

- [ ] **Step 2: Add manager and playback update loop**

Use `ClientTickEvents.END_CLIENT_TICK` to tick active playbacks and remove completed playbacks.

- [ ] **Step 3: Verify client compile and commit**

Run:

```bat
cmd.exe /c gradlew.bat compileClientJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Commit:

```bat
git add src/client/java/jujutsu/mod/client
git commit -m "feat(client): add Hairpin playback shell"
```

---

### Task 4: Add particle and sound assets

**Files:**
- Modify: `src/main/java/jujutsu/mod/JujutsuMod.java`
- Create: `src/main/java/jujutsu/mod/registry/JujutsuParticles.java`
- Create: `src/main/java/jujutsu/mod/registry/JujutsuSounds.java`
- Modify: `src/client/java/jujutsu/mod/client/JujutsuModClient.java`
- Create: `src/client/java/jujutsu/mod/client/particle/HairpinSparkParticle.java`
- Create: `src/client/java/jujutsu/mod/client/particle/JujutsuClientParticles.java`
- Create: `src/main/resources/assets/jujutsumod/particles/hairpin_spark.json`
- Create: `src/main/resources/assets/jujutsumod/textures/particle/hairpin_spark.png`
- Create: `src/main/resources/assets/jujutsumod/sounds.json`

**Interfaces:**
- Produces: one custom particle type and registered custom sound events.
- Consumes: client playback manager.

- [ ] **Step 1: Register one custom particle and sound events**

Keep this first pass small: one sprite particle and event ids for sound layers. Real OGG files are added when sourced or generated; the code must not depend on missing audio files to compile.

- [ ] **Step 2: Spawn particles from playback**

During active phases, spawn particles from nail points and target point. Keep counts small.

- [ ] **Step 3: Verify build and commit**

Run:

```bat
cmd.exe /c gradlew.bat build --no-daemon -x test
```

Expected: `BUILD SUCCESSFUL`.

Commit:

```bat
git add src/main/java/jujutsu/mod src/client/java/jujutsu/mod/client src/main/resources/assets/jujutsumod
git commit -m "feat(fx): add Hairpin particle assets"
```

---

### Task 5: Add lightweight renderer and screen flash

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/JujutsuModClient.java`
- Create: `src/client/java/jujutsu/mod/client/fx/HairpinScreenOverlay.java`
- Create: `src/client/java/jujutsu/mod/client/fx/HairpinWorldRenderer.java`

**Interfaces:**
- Consumes: active playbacks from `HairpinPlaybackManager`.
- Produces: HUD white impact frame and simple world-space visual accents.

- [ ] **Step 1: Add HUD impact flash**

Register a HUD element that draws a short translucent white overlay during the hammer snap and bloom windows.

- [ ] **Step 2: Add world renderer only if compile-safe with local Fabric API**

If `LevelRenderEvents` / pipeline APIs compile cleanly, render simple tracer lines and ring. If local API names differ, leave renderer out of first commit and keep particles + HUD as the first verified slice.

- [ ] **Step 3: Verify build and commit**

Run:

```bat
cmd.exe /c gradlew.bat build --no-daemon -x test
```

Expected: `BUILD SUCCESSFUL`.

Commit:

```bat
git add src/client/java/jujutsu/mod/client
git commit -m "feat(client): add Hairpin screen effects"
```

---

### Task 6: Final verification

**Files:**
- No new files expected.

**Interfaces:**
- Consumes: all prior tasks.
- Produces: final evidence.

- [ ] **Step 1: Run timeline test**

```bat
cmd.exe /c gradlew.bat testHairpinTimeline --no-daemon
```

Expected: `HairpinTimelineTest passed` and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full build without project tests**

```bat
cmd.exe /c gradlew.bat build --no-daemon -x test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Report exact limitations**

State clearly whether the result is compile-verified only or in-game smoke-tested. Do not claim in-game feel without running a client.
