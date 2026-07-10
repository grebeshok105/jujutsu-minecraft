# Test & Build Commands

← [[00-MOC]] · source primarily `build.gradle` in cinematic worktree / same in repo when merged

## Baseline (AGENTS.md)

```bat
gradlew.bat build --no-daemon -x test
```

**Proves:** compilation + jar packaging.  
**Does NOT prove:** in-game feel, multiplayer, resource correctness beyond compile/package.

Client startup/log smoke:

```bat
gradlew.bat runClient --no-daemon
```

**Proves:** only that the client reaches a usable startup state without a logged fatal error. It does not prove any gameplay scenario unless a human actually performs and observes it.
Set `JAVA_HOME` to JDK 21 if needed.

## Custom verification tasks (`build.gradle`)

| Task | Main class | Proves | Source |
|---|---|---|---|
| `testProjectSanity` | `ProjectSanityTest` | resources, side boundaries, VFX ownership, recipe registration, removed-path guards | `:41-48` |
| `testTargetResolver` | `TargetResolverTest` | targeting helpers | `:51-58` |
| `testProjectJjkNobaraProfile` | `ProjectJjkNobaraProfileTest` | current Nobara timing/range/damage policy | `:61-68` |
| `testVfxCore` | `VfxCueTest` | cue fields, stable IDs, payload codec | `:71-78` |
| `testVfxTimeline` | `VfxTimelineTest` | late/future cue age and expiry | `:81-88` |
| `testVfxQuality` | `VfxQualityTest` | vanilla particle-setting density scaling | `:91-98` |
| `testVfxAnchor` | `VfxAnchorResolverTest` | live-anchor and immutable-origin fallback | `:101-108` |

`check` depends on all seven assertion tasks at `build.gradle:111-119`. Removed `HairpinTimeline`, `HairpinVisualProfile`, legacy playback/state-manager, and their old test tasks are historical only and are not live build inputs.

**Status:** VERIFIED task registration in build.gradle; run from worktree that contains those tests.

## What compilation alone never proves

- Particle visibility in world
- Sound balance
- Mark UI readability
- Resonance through walls feel
- Multiplayer canSend edge cases

---
tags: #jujutsumod #tests
