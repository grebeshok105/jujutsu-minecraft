# Test and Build Commands

Status: CURRENT

## Full verification

```bash
./gradlew build --no-daemon --rerun-tasks
python3 tools/audit_docs.py
git diff --check
```

The current build defines 30 custom JavaExec verification programs (VERIFIED — build.gradle, `tasks.register('test…', JavaExec)`), each using a main method with assertions enabled by -ea. check depends on all 30. The standard Gradle test task remains part of build but is not the whole suite.

Focused commands:

```bash
./gradlew testCharacterPlayerState --no-daemon
./gradlew testCharacterDefinitions testCharacterClients --no-daemon
./gradlew testNobaraAbilitySlots testProjectJjkNobaraProfile testProjectSanity --no-daemon
./gradlew testTodoProfile testTodoSwapPlan testTodoTargetSafety testTodoHandsEmpty --no-daemon
./gradlew testTodoFakeClap testTodoPairSwap testTodoSwapMarker --no-daemon
./gradlew testTargetResolver testClickGuiDrag --no-daemon
```

Use runClient for UI, rendering, mixin, animation, combat-feel, and VFX claims. The docs audit is currently a required local PR check; wiring it into GitHub Actions needs workflow-write permission for the connected integration.
