# Test & Build Commands

← [[00-MOC]] · source primarily `build.gradle` in cinematic worktree / same in repo when merged

## Baseline (AGENTS.md)

```bat
gradlew.bat build --no-daemon -x test
```

**Proves:** compilation + jar packaging.  
**Does NOT prove:** in-game feel, multiplayer, resource correctness beyond compile/package.

Optional client:

```bat
gradlew.bat runClient --no-daemon
```

**Proves:** client boots (manual smoke).  
Set `JAVA_HOME` to JDK 21 if needed.

## Custom verification tasks (`build.gradle`)

| Task | Main class | Proves | Source |
|---|---|---|---|
| `testHairpinTimeline` | `HairpinTimelineTest` | timeline phase math | `:38-45` |
| `testHairpinVisualProfile` | `HairpinVisualProfileTest` | particle budget tables | `:47-54` |
| `testProjectSanity` | `ProjectSanityTest` | resource/side sanity | `:56-63` |
| `testHairpinDebugLog` | `HairpinDebugLogTest` | debug gate | `:65-72` |
| `testTargetResolver` | `TargetResolverTest` | targeting helpers | `:74-81` |
| `testNobaraCombatStateManager` | `NobaraCombatStateManagerTest` | legacy state machine | `:83+` |

Also present under test sources: `ProjectJjkNobaraProfileTest`, `HairpinGameplayServiceTest`.

**Status:** VERIFIED task registration in build.gradle; run from worktree that contains those tests.

## What compilation alone never proves

- Particle visibility in world
- Sound balance
- Mark UI readability
- Resonance through walls feel
- Multiplayer canSend edge cases

---
tags: #jujutsumod #tests
