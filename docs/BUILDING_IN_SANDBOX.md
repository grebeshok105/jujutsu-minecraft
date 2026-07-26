# Building in a restricted sandbox

This project targets Java 21, Minecraft 1.21.8, Gradle 9.5.1, and Fabric Loom. Start with the normal wrapper command; the current Hyperagent sandbox can run the Gradle single-use daemon and worker without the old bind-shim workaround.

## Normal verification

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build --no-daemon
python3 tools/audit_docs.py
```

The full build compiles main and client source sets, runs the Gradle test task, and runs every custom JavaExec verification program wired into check. The current branch has 23 custom verification programs. A successful remapped jar is written to build/libs/jujutsumod-1.0.0.jar.

For a clean proof rather than an up-to-date result:

```bash
./gradlew build --no-daemon --rerun-tasks
```

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
```

The test sources use main methods with Java assertions, not a conventional JUnit suite. The named JavaExec tasks enable assertions with -ea and are the authoritative automated checks. The standard test task remains part of build but is not the whole test suite.

## Client verification

Compilation does not prove rendering, mixin compatibility at runtime, UI hitboxes, combat feel, or cinematic timing. For UI/gameplay/VFX work, run a real client smoke test on a machine with graphics:

```bash
./gradlew runClient --no-daemon
```

This checklist is the owner of the client-smoke scope. It is not automated — nothing in the build covers any of it (see E1 in KNOWN_ISSUES.md).

### Menu and selection

- N opens ClickGui; the Characters tab lists Nobara, Todo, and None; Soon placeholders stay non-clickable.
- Select Todo, confirm it, and check the roster labels are localized rather than raw keys.
- Reconnect and confirm the selection persisted; confirm the Nobara starter kit is not re-granted on re-selection.

### Todo — Boogie Woogie (R)

- Valid swap: aim at a mob within 20 blocks, press R, and confirm both parties exchange positions while each keeps its own yaw, pitch, head yaw, and velocity.
- Player↔player swap when a second player is available.
- Empty-hands gate: hold any item and confirm R is refused with the hands-full message and no partial effect.
- Blocked destination: aim at a target standing where the reciprocal destination is inside solid blocks, and confirm either the small nudge resolves it or the cast is refused atomically with neither party moved.
- Destination policy sanity: mid-air, in-water, and crawl-space destinations are expected to succeed, and swapping with a target in a boat or minecart is expected to be allowed. This is the deliberate policy documented in AGENTS.md, not a bug to file.
- Cooldown: R is refused for 3 seconds after a success, and is not consumed after a refusal.
- Out-of-range and no-line-of-sight casts produce the right refusal message.

### Todo — melee and Black Flash

- Vanilla melee lands with Todo's heavier damage and slower swing.
- Black Flash procs produce the bonus damage number, stagger, and the shared Black Flash VFX. The bonus hit must not recurse into a second proc.

### Nobara regression

- Directed Hairpin (R) and mass Hairpin (B) pick the intended target — targeting is shared with Todo, so a Todo-side targeting change can regress this.
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
