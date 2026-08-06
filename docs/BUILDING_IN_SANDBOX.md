# Building in a restricted sandbox

This project targets Java 21, Minecraft 1.21.8, Gradle 9.5.1, and Fabric Loom. Start with the normal wrapper command; the current Hyperagent sandbox can run the Gradle single-use daemon and worker without the old bind-shim workaround.

## Normal verification

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew qualityGate --no-daemon
```

qualityGate is the canonical check and the only command whose green result may be called verified. CI runs this exact task, so a green local run and a green CI run mean the same thing. It is composed of three parts:

- check — compiles the main and client source sets, runs the Gradle test task, and runs every custom JavaExec verification program. Use `./gradlew verifyAssertionsEnabled` for the live program inventory.
- auditDocumentation — runs tools/audit_docs.py, which used to exist only as a CI step, so a documentation break was found after a push instead of before a commit.
- verifyAssertionsEnabled — reads the real Gradle task model and fails, listing the offenders, if any verification JavaExec task would run with assertions disabled. JavaExec defaults enableAssertions to false, and the verification programs are plain main() classes guarded by assert, so a task that loses its -ea does not fail: it passes unconditionally and silently.

The gate does not build a jar. For the remapped artifact at build/libs/jujutsumod-1.0.0.jar:

```bash
./gradlew assemble --no-daemon
```

For a clean proof rather than an up-to-date result:

```bash
./gradlew qualityGate --no-daemon --rerun-tasks
```

A green gate proves shape, contracts and pure logic. It proves nothing about behaviour inside a running world — see the client-smoke checklist below.

## Installing JDK 21 without root

If java is unavailable, install a verified JDK in the workspace. The following Temurin archive was used successfully in July 2026:

```bash
cd /agent/workspace
curl -L --fail -o temurin-jdk21.tar.gz   'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.11%2B10/OpenJDK21U-jdk_x64_linux_hotspot_21.0.11_10.tar.gz'
printf '%s  %s\n'   '4b2220e232a97997b436ca6ab15cbf70171ecff52958a46159dfa5a8c44ca4de'   '/agent/workspace/temurin-jdk21.tar.gz' | sha256sum -c -
tar -xzf /agent/workspace/temurin-jdk21.tar.gz -C /agent/workspace
export JAVA_HOME=/agent/workspace/jdk-21.0.11+10
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Do not skip the checksum.

## TLS interception

If Gradle fails with PKIX path building failed, import only the sandbox CA files that actually exist:

```bash
for cert in /usr/local/share/ca-certificates/*proxy-ca.crt; do
  [ -f "$cert" ] || continue
  alias_name=$(basename "$cert" .crt)
  "$JAVA_HOME/bin/keytool" -importcert -noprompt     -cacerts -storepass changeit     -alias "$alias_name" -file "$cert" || true
done
```

Typical dependency domains are services.gradle.org, maven.fabricmc.net, repo.maven.apache.org, dl.cloudsmith.io, libraries.minecraft.net, piston-meta.mojang.com, and piston-data.mojang.com. Request network access only for a domain that a real failed command identifies.

## Focused verification

```bash
./gradlew testCharacterPlayerState --no-daemon
./gradlew testProjectJjkNobaraProfile testProjectSanity --no-daemon
./gradlew check --no-daemon
./gradlew runGameTest --no-daemon
./gradlew runClientGameTest --no-daemon
./gradlew auditReleaseJarIsolation --no-daemon
```

Two kinds of automated test live side by side, and both run inside check.

The older kind is a plain class with a main method guarded by Java assertions, run by a named JavaExec task. Those tasks enable assertions with -ea, and verifyAssertionsEnabled enforces the flag so a new task cannot silently ship without it.

The newer kind is a JUnit 5 class run by the standard test task. It exists because the JavaExec programs cannot boot Minecraft: fabric-loader-junit starts the loader for the test JVM, so a JUnit test may call SharedConstants.tryDetectVersion() and Bootstrap.bootStrap() in @BeforeAll and then exercise registries, codecs and buffers for real instead of reading source text. Write new tests here by default. failOnNoDiscoveredTests is true, because a JUnit run that discovers nothing is green for the same reason a JavaExec task without -ea is green: it asserted nothing.

Migration of the existing JavaExec programs is deliberate and gradual. The four classes that own mutable static state — CharacterAbilityCooldowns, CombatStagger, EmbeddedNailRegistry, ProjectJjkNailMarks — must move last: today each JavaExec program is its own JVM, so state cannot leak between tests, and a shared JUnit JVM removes that guarantee.

Server GameTest (issue #42 Stage A) adds two more checks, and both already run inside `qualityGate`. `runGameTest` boots the headless GameTest server, runs the server canaries, and writes JUnit XML to `build/test-results/gametest/junit.xml`, with logs and crash-reports under `build/run/gameTest/`; Loom wires it into `check`, so the canonical gate covers it. `auditReleaseJarIsolation` proves the release jar carries no test-mod content (gametest classes, a second `fabric.mod.json`, dev-only package prefixes, or test-only libraries). Neither replaces the fast tests above; the canaries prove the harness, not gameplay — the world/ability scenario backlog stays in issue #21.

Client GameTest (issue #42 Stage B) is the parallel lane that boots the real client: `./gradlew runClientGameTest --no-daemon` (`gradlew.bat runClientGameTest --no-daemon` on Windows) launches the modded client with an integrated singleplayer world and runs the client canaries (`jujutsu.mod.gametest.client.ClientLoadCanaryTest`, `ClientObservationCanaryTest`). Artifacts land under `build/run/clientGameTest/` — `logs/`, `crash-reports/`, `saves/`, and `screenshots/%04d_<name>.png` (the observation canary writes `0000_observation_canary.png` at 854x480); Loom's `deleteGameTestRunDir` wipes that directory before every run. There is no machine-readable client report in fabric-api 0.136.1 (source-verified): the lane's result is the task exit code, with logs and crash-reports as evidence — the server lane's JUnit XML at `build/test-results/gametest/junit.xml` stays server-only. The lane is deliberately OUTSIDE `qualityGate`: issue #42 Stage B keeps it an optional, non-required experiment (manual run, or the `client-gametest` workflow_dispatch CI lane), so the required gate composition is unchanged.

## Client verification

Compilation does not prove rendering, mixin compatibility at runtime, UI hitboxes, combat feel, or cinematic timing. For UI/gameplay/VFX work, run a real client smoke test on a machine with graphics:

```bash
./gradlew runClient --no-daemon
```

This checklist is the owner of the client-smoke scope. The client GameTest lane above proves the harness — the client boots with the production mod and observes server state — but nothing automated verifies gameplay or visual quality, which stay manual (see E1 in KNOWN_ISSUES.md).

### Menu and selection

- N opens ClickGui; the Characters tab lists Nobara, Todo, and None; Soon placeholders stay non-clickable.
- Select Todo, confirm it, and check the roster labels are localized rather than raw keys.
- Reconnect and confirm the selection persisted. Drop or lose a Nobara starter tool, re-select her, and confirm the missing hammer/doll/nails come back — and that tools still held are **not** duplicated. The re-grant on every selection is deliberate and idempotent.
- Vanilla crosshair is gone the whole time the menu is open, including through the close fade, and is back afterwards. Other HUD elements, third person, and the F3 crosshair are unaffected.
- Panel drag: left-drag the header band and confirm the panel tracks the cursor one-to-one at GUI scale 1, 2, and 4 — that is the exact case the old scale conversion got wrong. Middle-drag anywhere on the panel does the same.
- Drag never steals a click: pressing a tab, a roster card, or confirm still activates it instead of grabbing the panel.
- Drag the panel far off each edge and confirm part of the header stays grabbable; resize the window while it is off-centre and confirm it is pulled back into reach.
- Release the mouse during the close fade, reopen with N, and confirm the panel is not still following the cursor.

### Todo — Boogie Woogie (R)

- Valid swap: aim at a mob within 20 blocks, press R, and confirm both parties exchange positions while each keeps its own yaw, pitch, head yaw, and velocity.
- Player↔player swap when a second player is available.
- Empty-hands gate: hold any item and confirm R is refused with the hands-full message and no partial effect.
- Blocked destination: aim at a target standing where the reciprocal destination is inside solid blocks, and confirm either the small nudge resolves it or the cast is refused atomically with neither party moved.
- Destination policy sanity: mid-air, in-water, and crawl-space destinations are expected to succeed, and swapping with a target in a boat or minecart is expected to be allowed. This is the deliberate policy documented in AGENTS.md, not a bug to file.
- Cooldown: R is refused for 3 seconds after a success, and is not consumed after a refusal.
- Out-of-range and no-line-of-sight casts produce the right refusal message.

### Todo — feint clap (Shift+R)

Nothing in the build covers this; it is the only way to check the indistinguishability claim (see the feint section in the Codex note `03-systems/Todo-Boogie-Woogie.md`).

- Shift+R plays the full clap — same animation, same sound, same cue — and moves nobody.
- With a second player watching: alternate real and feint casts and confirm the two are only tellable by the swap itself, not by any difference in the clap.
- The caster-only tell (a small dust wisp at chest height) is visible to the caster and to nobody else.
- Independent cooldowns: a feint does not block or delay R, and R does not block Shift+R.
- Hands-full and spectator refusals behave identically for R and Shift+R.
- Known and expected: a real swap teleports at cast time, before the palms meet at 0.39 of the animation, so a feint is already distinguishable at t = 0. Confirm how bad that reads in practice — it is the open product question, not a bug to fix on the spot.

### Todo — pair swap (B twice)

Nothing in the build teleports anything, so every line here is only checkable in game.

- First B on an eligible body: actionbar names it, a caster-only mark burst appears on it, and no cooldown starts — press B on a second body immediately after and it must go through.
- Second B on a different body: the two trade places, Todo does not move, and the two endpoint bursts land on the bodies that actually moved.
- Second B back at the marked body: the mark clears with no cooldown taken.
- Second B at empty air: refused, and the mark **survives** — the next B must still commit.
- Pair distance is unlimited by design: mark someone, walk until the two are 40 blocks apart, and confirm the swap still commits as long as both are within 20 blocks of Todo.
- Marks drop on: 5 s expiry (silent), the marked body dying (message), death, dimension change, relog, and switching vessel in the ClickGui.
- STRICT placement: mark someone, aim the second cast at a body whose destination is solid rock, and confirm the whole cast cancels with nothing moved — there must be no partial teleport.

### Todo — thrown marker (R with no target)

- Give yourself a Boogie Woogie Marker, right click to throw. The stack is consumed as it leaves the hand.
- On a block: the marker stays visible resting against the face. Aim at nothing and press R — Todo swaps to it, and the marker is consumed.
- On a body: the marker vanishes and the body glows for the mark duration. R with nothing under the crosshair swaps Todo with that body.
- Priority: with both a live mark and an enemy under the crosshair, R must take the enemy, not the mark.
- Glow bookkeeping: mark a body that is already glowing from a Nobara target mark, let the Todo mark expire, and confirm the Nobara glow survives.
- Leak check: throw a marker, let the 10 s TTL run out, and confirm the resting projectile disappears. Repeat with relog, dimension change and death mid-mark — no orphan marker, no stuck glow.
- The empty-hands rule still holds: holding a second marker (or anything else) must refuse the swap.

### Todo — melee and Black Flash

- Vanilla melee lands with Todo's heavier damage and slower swing.
- Black Flash procs produce the bonus damage number, stagger, and the shared Black Flash VFX. The bonus hit must not recurse into a second proc.

### Nobara regression

- Directed Hairpin (R) and mass Hairpin (B) pick the intended target — targeting is shared with Todo, so a Todo-side targeting change can regress this.
- Targeting regression owed by E1b in KNOWN_ISSUES.md: hammer targeting, nail launch, and directed Hairpin, with two mobs where a nearer one is off to the side and a farther one is dead-centre in the crosshair. The centred one must now win. This is a roster-wide comparator change and has only pure-logic coverage.
- Shift+R Self Resonance, Shift+B Nail Trap, and contextual hammer melee still behave.
- Embedded-nail TTL and per-owner cap still expire and cap; disconnect/rejoin leaves no orphaned nails.

### Shared vessel rendering

- Third person: both Nobara and Todo render their GeckoLib vessel model, not the vanilla player model.
- Held items attach to the correct hand on both vessels, in third person and first person.
- Head look tracks the camera and stays inside the clamp; no pose-stack corruption after ability casts or menu open/close.
- Todo animations play: idle, walk, attack, and `ability.boogie_woogie` on cast, with the clap SFX at the palm-contact beat.

## Troubleshooting

| Symptom | Likely cause | Action |
|---|---|---|
| Permission denied for gradlew | Executable bit unavailable in the checkout | Run bash ./gradlew ... or chmod +x ./gradlew locally |
| java not found | No JDK in the sandbox | Install verified JDK 21 as above |
| PKIX path building failed | JDK truststore lacks the sandbox CA | Import the existing proxy CA certificate(s) |
| Dependency download is forbidden | Egress domain is not approved | Request access for the exact failed domain, then retry |
| Could not connect to Gradle daemon | Local worker/daemon networking is restricted | Retry --no-daemon; if the environment still blocks loopback, use a current environment-specific in-process workaround rather than copying the old hard-coded port-8090 script |
| Build succeeds but game crashes | Compile checks cannot prove runtime behavior | Run runClient and inspect the latest game log |

A former port-8090 LD_PRELOAD workaround is intentionally not included here. It is not required in the current sandbox; only recreate an environment-specific workaround after reproducing the same loopback failure.
