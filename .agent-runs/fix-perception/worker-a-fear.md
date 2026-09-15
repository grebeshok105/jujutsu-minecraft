# Brief A — Fear look-inversion (CRITICAL)

- agent: worker | run: fix-perception | status: done

## Summary

The `@ModifyVariable` on `MouseHandler.turnPlayer(D)V` was modifying the frame
time-delta (smooth-camera interpolation input) — a no-op for look inversion when
smooth camera is off, and it corrupted smoothing when on. Retargeted to
`@ModifyArgs` on the `INVOKE` of `LocalPlayer.turn(DD)V` inside `turnPlayer`,
negating both yaw/pitch args only while `FearClientState.hasFear()`. Pure negate
decision extracted to `FearInputMap.lookDelta(double, boolean)` and JUnit-tested
(6 tests, 0 failures). Compile + tests green; committed `e1f62fc`.

## Detail

### javap evidence (mapped 1.21.8 jar)

Jar: `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-clientonly/1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2/minecraft-clientonly-...-v2.jar`,
`javap -p -c net.minecraft.client.MouseHandler` (temurin21 javap):

`private void turnPlayer(double)`:
- arg `dload_1` (the `(D)V` param) is used ONLY at bc 71-75 / 91-95 as second arg
  to `SmoothDouble.getNewDeltaValue(DD)D` inside the `options.smoothCamera` branch
  (bc 47-107). It never reaches the player.
- Real deltas: local 3 = `accumulatedDX * sens³` (or `*sens²` when first-person
  scoping), local 5 = `accumulatedDY * ...` — computed in all three branches
  (smooth 60-107, scoping 126-170, normal 173-202), then consumed at bc 262-269:
  `player.turn(d3, d5 * invertY)` — `LocalPlayer.turn(DD)V`.

Chosen injection: `@ModifyArgs(method = "turnPlayer(D)V", at = @At(INVOKE,
target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))` — one point
covers all three branches, post-invertY (negating both args inverts look
regardless of the invertY sign). Rejected modifying `LocalPlayer.turn` itself —
it is a public API callable elsewhere; the call-site injection is narrower.

### Changes

- `src/client/java/jujutsu/mod/client/mixin/FearMouseMixin.java:51-65` —
  replaced the broken `@ModifyVariable(index=1)` with `@ModifyArgs` on
  `LocalPlayer.turn(DD)V`; early-out when `hasFear()` is false (zero behaviour
  change when inactive — args untouched). Updated class javadoc (lines 15-18)
  documenting the javap-verified signature. Added imports for `ModifyArgs`,
  `Args`, `FearInputMap`.
- `src/client/java/jujutsu/mod/client/curse/fear/FearInputMap.java:66-73` — new
  pure `lookDelta(double delta, boolean fearActive)` (negate when active).
- `src/test/java/jujutsu/mod/client/curse/fear/FearInputMapTest.java:52-74` —
  two new tests: `lookDeltaMirrorsUnderFear` (incl. `-0.0` edge) and
  `lookDeltaIsIdentityWithoutFear`. Red-mutant: dropping the negation fails the
  first; flipping the predicate fails the second.

`FearClientState.hasFear()` reused as the flag (single canonical predicate, per
brief). No second mixin needed; mixin json untouched.

## Evidence

- `javap -p -c MouseHandler` — output excerpted above (bc 0-272 of turnPlayer).
- `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew compileClientJava compileTestJava` — exit 0 (only pre-existing deprecation notes).
- `./gradlew test --tests '*Fear*'` — exit 0; `TEST-jujutsu.mod.client.curse.fear.FearInputMapTest.xml`: `tests="6" skipped="0" failures="0" errors="0"`.
- Commit: `e1f62fc672a789c474c922af485c6bb918a89969 fix(client): invert fear look via LocalPlayer.turn deltas` (only the 3 files added).

## Rejected/Open

- Rejected: `@ModifyVariable` on locals 3/5 — they are stored in three separate
  branches; LOAD-targeted modification would need multiple points and is fragile
  vs the single INVOKE.
- Rejected: mixin into `LocalPlayer.turn(DD)V` — broader than needed; `turn` is
  invoked from other call paths.
- Open: negating both axes also mirrors yaw — matches the design intent
  ("mouse cursor mirrored" for buttons/wheel already established); pitch-only
  inversion would leave yaw correct, which is NOT fear. Behaviour verified only
  by bytecode reading + unit test; no in-game run performed (out of brief scope).
