# Сессия 2026-07-07 — Hairpin Prototype And Research Corpus

## Проект

- `D:/WorkFlow/Jujutsu Minecraft`
- Рабочее дерево: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/brainstorming`
- Ветка: `chore/jujutsu-brainstorming`
- Fabric `1.21.8`, Fabric API `0.136.1+1.21.8`, Java `21`
- Mod id: `jujutsumod`

## Что строили

Продолжили первый vertical slice мода по *Магической битве / Jujutsu Kaisen*:
**Нобара — Hairpin cinematic VFX prototype**.

Цель slice: доказать workflow `VFX bible -> standalone visual target -> minimal in-game Fabric prototype` без тяжёлых VFX-зависимостей.

## Главные решения

- Первый персонаж: **Нобара**.
- Первый showcase эффект: **Hairpin**.
- Реализация остаётся Fabric-native, без LibsFX/Veil/Satin/Lodestone/ParticleAnimationLib как runtime dependencies.
- World renderer для tracer/ring пока не внедрён: частицы + HUD flash дают compile-safe первый world-space read; отдельный renderer лучше делать следующим самостоятельным шагом после smoke test.
- Звуки сейчас зарегистрированы как custom sound event ids, но `sounds.json` временно мапит их на vanilla sounds. Финальные OGG ассеты ещё нужны.
- Particle texture `hairpin_spark.png` — маленький placeholder, не финальный арт.

## Артефакты дизайна

- Спека: `docs/superpowers/specs/2026-07-06-nobara-hairpin-cinematic-design.md`
- План: `docs/superpowers/plans/2026-07-06-nobara-hairpin-prototype.md`
- Standalone visual target: `docs/visual-targets/nobara-hairpin/index.html`
  - Один self-contained HTML/Canvas файл.
  - Показывает пять фаз: Marked Nails, Trigger Ping, Cursed Compression, Hairpin Snap, Blood-Black Residue.
  - Тайминги совпадают с `HairpinTimeline`: `0/180/240/560/900/1800`.
  - Supports fixed-frame QA with `?t=<milliseconds>`, for example `?t=720`.

## Research corpus added after visual feedback

User provided seven deep-research files and explicitly allowed copying them into project docs. Raw copies are stored in:

- `docs/research/sources/2026-07-07-combat-ability-design.md`
- `docs/research/sources/2026-07-07-fabric-technical-bible.md`
- `docs/research/sources/2026-07-07-jujutsu-kaisen-minecraft-bible.md`
- `docs/research/sources/2026-07-07-fabric-vfx-development.md`
- `docs/research/sources/2026-07-07-hairpin-vfx-ux-bible.md`
- `docs/research/sources/2026-07-07-fabric-combat-architecture.md`
- `docs/research/sources/2026-07-07-fabric-1218-vfx-production-bible.md`

Synthesis:

- `docs/research/2026-07-07-fabric-vfx-combat-research-synthesis.md`
- `docs/research/2026-07-07-jujutsu-deep-research-synthesis.md`
- `docs/research/prompts/2026-07-07-vfx-next-deep-research-prompts.md`

Important conclusions from the new corpus:

- Hairpin must read as `mark -> warn -> compression -> snap -> burst -> residue`, driven by one nail-anchored timeline.
- Bloom and afterglow should share one anchor, palette, curve, and residue motion.
- Palette should shift darker: blood-black / black cherry first, dirty fuchsia edge second, cold metal third.
- Crooked nails are acceptable only when they read as intentionally embedded. Random-looking tilt weakens the effect.
- Server owns gameplay and nail anchors; client owns transient VFX/audio/screen/camera playback from semantic events and deterministic seeds.
- First real implementation should stay minimal: no broad ability framework and no heavy VFX dependency before Hairpin proves the pattern.
- Treat raw research API snippets as checkpoints, not copy-paste truth. Verify Fabric 1.21.8 class/method names locally before code.
- Latest visual target uses one nail-anchored timeline for bloom/burst/residue, darker blood-black palette, intentionally embedded nails, broken fracture arcs instead of clean circular rings, and a smoother compact UI.

## Что реализовано

### Timing model

- `src/main/java/jujutsu/mod/fx/HairpinTimeline.java`
- `src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java`
- Gradle task: `testHairpinTimeline`

### Server trigger and networking

- `src/main/java/jujutsu/mod/network/HairpinFxPayload.java`
- `src/main/java/jujutsu/mod/network/JujutsuNetworking.java`
- `src/main/java/jujutsu/mod/command/JujutsuCommands.java`
- Команда: `/jujutsu hairpin`
- Сервер выбирает target и 4 nail positions.
- Payload typed/custom, один S2C event на сцену.
- Broadcast идёт nearby players в той же dimension в радиусе `64.0`, с `ServerPlayNetworking.canSend(...)` per client.

### Client playback

- `src/client/java/jujutsu/mod/client/network/JujutsuClientNetworking.java`
- `src/client/java/jujutsu/mod/client/fx/HairpinPlayback.java`
- `src/client/java/jujutsu/mod/client/fx/HairpinPlaybackManager.java`
- Receiver зарегистрирован через `ClientPlayNetworking.registerGlobalReceiver`.
- Playback тикает через `ClientTickEvents.END_CLIENT_TICK`.
- При смене фаз проигрываются local sounds и создаются частицы.

### Particles and sounds

- `src/main/java/jujutsu/mod/registry/JujutsuParticles.java`
- `src/main/java/jujutsu/mod/registry/JujutsuSounds.java`
- `src/client/java/jujutsu/mod/client/particle/HairpinSparkParticle.java`
- `src/client/java/jujutsu/mod/client/particle/JujutsuClientParticles.java`
- `src/main/resources/assets/jujutsumod/particles/hairpin_spark.json`
- `src/main/resources/assets/jujutsumod/textures/particle/hairpin_spark.png`
- `src/main/resources/assets/jujutsumod/sounds.json`

### HUD impact flash

- `src/client/java/jujutsu/mod/client/fx/HairpinScreenOverlay.java`
- Uses Fabric `HudElementRegistry`, not deprecated `HudRenderCallback`.
- Flash triggers during `HAMMER_SNAP` and `HAIRPIN_BLOOM`.

## Коммиты этой worktree

Implementation/research range from `main` commit `d962cc2` through `83c83c9`, plus this session handoff update:

- `2745684 docs(design): define Nobara Hairpin cinematic slice`
- `23b272e docs(design): document Universal FX decision`
- `39aa731 docs(plan): add Nobara Hairpin prototype plan`
- `eb789ed feat(fx): add Hairpin timeline model`
- `21af244 feat(fx): add Hairpin trigger payload`
- `2ac1444 docs(session): add 2026-07-06 cutoff notes`
- `cec55e2 feat(client): add Hairpin playback shell`
- `612ba37 feat(fx): add Hairpin particle assets`
- `02eda52 feat(client): add Hairpin screen effects`
- `dc98710 fix(fx): play Hairpin prep sound on start`
- `bcd4161 feat(fx): broadcast Hairpin and add visual target`
- `5780b3b docs(session): update Hairpin prototype handoff`
- `69e7250 docs(vfx): darken Hairpin visual target`
- `90f38b3 docs(vfx): smooth Hairpin afterglow transition`
- `20f672c docs(research): synthesize Jujutsu design sources`
- `ad4a91f docs(research): add Fabric VFX research corpus`
- `1bc51c9 docs(research): add first design research sources`
- `52115f0 docs(vfx): refine Hairpin visual target`
- `47ef839 docs(design): align Hairpin spec with research`
- `83c83c9 docs(research): add Hairpin VFX research prompts`

## Verification

Commands run with:

```powershell
$env:JAVA_HOME='D:\WorkFlow\Minecraft\jdk-21.0.11+10'
```

Fresh verification after final commit:

```bat
cmd.exe /c gradlew.bat testHairpinTimeline --no-daemon
```

Result: `HairpinTimelineTest passed`, `BUILD SUCCESSFUL`.

```bat
cmd.exe /c gradlew.bat build --no-daemon -x test
```

Result: `BUILD SUCCESSFUL`.

```bat
git diff --check d962cc2..HEAD
```

Result: no output.

After adding the new research corpus:

```bat
git diff --check
```

Result: no output.

After updating the visual target:

```text
Playwright with installed Chrome opened:
file:///D:/WorkFlow/Jujutsu%20Minecraft/.worktrees/brainstorming/docs/visual-targets/nobara-hairpin/index.html?t=0
?t=320
?t=720
?t=1300
```

Result: no console/page errors; phase/time/readout/progress values matched the fixed milliseconds.

```bat
git status --short --branch
```

Result: clean on `chore/jujutsu-brainstorming`.

This clean status was true before the final session handoff update. Re-check status after committing this `SESSION.md` update.

## Code review

Subagent review on updated range `d962cc2..bcd4161`:

- Critical: none.
- Important: none.
- Assessment: ready to merge.

Reviewer caveat:

- Do not claim in-game feel/visibility is proven until `runClient` smoke test is performed.

## Not verified

- `runClient` smoke test was not run.
- In-game visual feel, resource reload warnings, and multiplayer observation were not manually verified.
- Placeholder particle texture and vanilla-backed sound mappings are not final assets.

## Next good steps

1. Run `cmd.exe /c gradlew.bat runClient --no-daemon`.
2. In a dev world, run `/jujutsu hairpin`.
3. Check:
   - command sends to the triggering client;
   - particles appear around target/nail positions;
   - HUD flash appears on snap/bloom;
   - sound placeholders play without resource errors;
   - nearby second client receives the event if multiplayer smoke test is available.
4. Replace placeholder particle and vanilla sound mappings with final custom assets.
5. Consider a separate `HairpinWorldRenderer` only after the particle/HUD slice feels good in-game.
