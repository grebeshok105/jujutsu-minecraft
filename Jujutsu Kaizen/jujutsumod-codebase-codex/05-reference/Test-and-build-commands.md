# Test and Build Commands

Status: CURRENT

## Full verification

```bash
./gradlew qualityGate --no-daemon --max-workers=1 --no-watch-fs
```

`qualityGate` is the only command whose green result may be called verified. It runs `check` (Java 21 compilation, JUnit 5, legacy JavaExec programs), `auditDocumentation`, and `verifyAssertionsEnabled`. The JavaExec inventory remains 31; JUnit 5 classes run through the standard test task and are counted with test Java files.

Focused commands:

```bash
./gradlew testCharacterPlayerState --no-daemon
./gradlew testCharacterDefinitions testCharacterClients --no-daemon
./gradlew testNobaraAbilitySlots testProjectJjkNobaraProfile testProjectSanity --no-daemon
./gradlew testTodoProfile testTodoSwapPlan testTodoTargetSafety testTodoHandsEmpty --no-daemon
./gradlew testTodoFakeClap testTodoPairSwap testTodoSwapMarker --no-daemon
./gradlew testTargetResolver testClickGuiDrag --no-daemon
```

Use `runClient` for UI, rendering, mixin, animation, combat-feel, and VFX claims. The build gate does not prove a real world, frame or second-player interaction. `docs/BUILDING_IN_SANDBOX.md` owns the full client smoke matrix and the rule that freeze/visual claims require real client evidence.
