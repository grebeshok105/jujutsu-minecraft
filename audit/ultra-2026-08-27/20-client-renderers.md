## Renderers — 20-client-renderers

### 1) Что проверено (файлы/символы)

**Инструменты:** `grep`, `read`, `glob`, `bash git ls-files`, `codegraph`-эквивалент через grep/read. Проверялось 2026-08-27 на `D:/WorkFlow/jujutsu-minecraft`, ветка main-ish (commit 4268c4a).

**Живой стек (skin-bridge, не Geo-replaced):**
- `src/client/java/jujutsu/mod/client/mixin/CharacterSkinAnimationMixin.java:28-139` — единственный dispatch для всех сосудов; `@Inject` после `EntityModel.setupAnim` + `@WrapMethod` вокруг `LivingEntityRenderer.render`.
- `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationRenderer.java:11-34` — `apply()`/`renderScale()` → `ClientCharacterSelectionManager.selectionByEntityId()` → `JujutsuCharacterClients.definition().skinAnimation()`.
- `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationAdapter.java:22-223` — shared bridge: `getInstanceId`/`playerTriggerInstanceId`, `getRenderType()=null`, `apply()` с snapshot + `fillRenderState` + `handleAnimations` + `applyPose` (root/body/head/arms/legs, folding elbow/hand/knee, waist compensation).
- `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationModel.java:13-71` — shared `setCustomAnimations`: копирование `DataTickets.HUMANOID_MODEL` → `rightArm/leftArm`, clamped head-look (38° yaw / 22° pitch), `actionKeyframedIsPlaying` guard.
- `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationState.java:8-58` — snapshot 7 `ModelPart` (root/body/head/2 arms/2 legs) с `AutoCloseable`, `close()` идемпотентен, `finally` в миксине.
- `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimation.java:7-16` — функциональный интерфейс сосуда.
- `src/client/java/jujutsu/mod/client/render/HiddenBodyRenderGate.java:13-33` и `src/client/java/jujutsu/mod/client/render/ShadowBodySink.java:18-154` — TTL-гейты для скрытия/погружения тела (Megumi shadow move).
- `src/client/java/jujutsu/mod/client/character/JujutsuCharacterClients.java:26-65` — registry `definition()`/`all()`/`registerAll()`, vessel seam.
- `src/client/resources/jujutsumod.client.mixins.json:6` — `CharacterSkinAnimationMixin` единственный player-render миксин; `CharacterRenderDispatchMixin` отсутствует в runtime (проверено `grep -rn` по `src/` — 0 совпадений, только `archive/` и `ProjectSanityTest:1025,1120`).

**Vessel-адаптеры:**
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiSkinAnimationAdapter.java:8-44` — `WeakHashMap<AbstractClientPlayer,SwingState>`; цикл `punch_1→punch_2→kick`, `COMBAT_IDLE` (22t window + stationary guard).
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoAnimatable.java:23-168` — `MELEE_VARIANT_COUNT=3`, `SUMMON_ANIM=summon_divine_dogs`, `SHADOW_DIVE/EMERGE`, `triggerSummon/Dive/Emerge` + `restartMeleeTrigger` с `forceAnimationReset()`.
- `src/client/java/jujutsu/mod/client/render/nobara/NobaraSkinAnimationAdapter.java:9-41` — `attack1→attack2→attack3` цикл, `LOCOMOTION_VARIANT = tickCount/120 + getId()`.
- `src/client/java/jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java:21-172` — 15+ triggerableAnims (idle/walk/run/idle2/walk2/one_two/attack1-3/snap/spell1-5/swipe1/hammer_* /self_resonance/black_flash).
- `src/client/java/jujutsu/mod/client/render/todo/TodoSkinAnimationAdapter.java:9-33` — одиночный `attack` trigger, `LOCOMOTION_VARIANT` аналогично Nobara.
- `src/client/java/jujutsu/mod/client/render/todo/TodoPlayerGeoAnimatable.java:22-144` — `attack`, `ability.boogie_woogie`, locomotion variant.

**Client definitions:**
- `src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java:99-132`, `.../nobara/NobaraClientDefinition.java:84-118`, `.../todo/TodoClientDefinition.java:101-136` — каждый держит `static final CharacterSkinAnimation SKIN_ANIMATION`, `playerSkin()` → `textures/entity/character/*.png`, `registerClientHooks()`.

**Архив (должен быть мёртв):**
- `archive/character-player-gecko/src/client/java/jujutsu/mod/client/render/CharacterGeoRenderers.java:23-32` (`create()` через `JujutsuCharacterClients.all()` + `definition.createRenderer()`), `CharacterGeoRenderer.java`, `CharacterPlayerGeoRenderer.java`, `CharacterPlayerGeoModel.java`, `CharacterHeldItemLayer.java`, `.../megumi/`, `.../nobara/`, `.../todo/` + `archive/character-player-gecko/manifest.txt:1-27` + `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/Vessel-render-stack.md:5-135`.

**Ресурсы/анимации:**
- `src/main/resources/assets/jujutsumod/geckolib/` — live bone-only `character_skin/{nobara,todo,megumi}.geo.json` и `animations/{projectjjk/npc,megumi/todo}.animation.json`; старые `geckolib/models/{projectjjk/todo/megumi}/*.geo.json` + `geo/` только в `archive/`.
- Live клипы сверены с `MegumiPlayerPresentationTest.java:63-84` и `CharacterSkinAnimationPackTest.java:46-51`.

**Дополнительно просмотрено:**
- `AGENTS.md:45-46, 141`, `README.md:24` — утверждения о renderer stack.
- `src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogRenderer.java` — отдельный `WolfRenderer`, не связан с player bridge.
- `grep -rn "CharacterGeoRenderers|CharacterRenderDispatch|CharacterPlayerGeo|HeldItemLayer" src/` — 0 live-хитов; `grep -rn "HiddenBodyRenderGate|ShadowBodySink" src/` — только ожидаемые 3 потребителя.

---

### 2) Находки

| severity | файл:строка | описание | рекомендация |
|---|---|---|---|
| **major** | `AGENTS.md:45-46` и `README.md:24` | Документация описывает **архивированный** стек как живой: "Nobara/Todo/Megumi render through GeckoLib replaced-player renderers / `CharacterGeoRenderers` + `CharacterRenderDispatchMixin` / `CharacterPlayerGeoRenderer/Model/HeldItemLayer`". Факт: с 2026-08-03 live стек — `CharacterSkinAnimationMixin` + `CharacterSkinAnimationRenderer/Adapter/Model/State` (vanilla `PlayerRenderer` не канселится, архив в `archive/character-player-gecko/` вне `src/`). Codex `Vessel-render-stack.md` корректен, AGENTS/README — нет. Риск: новый контрибьютор будет расширять несуществующий `CharacterPlayerGeoRenderer`. `read AGENTS.md:45` + `bash git ls-files \| grep CharacterGeo` + `read jujutsumod.client.mixins.json:6` подтверждают. | Обновить `AGENTS.md:45-46` на формулировку из Codex: "vanilla PlayerRenderer + invisible GeckoLib rig → PlayerModel copy, dispatch `CharacterSkinAnimationMixin` → `CharacterSkinAnimationRenderer` → `definition.skinAnimation()`". В `README.md:24` аналогично. |
| **major** | `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationAdapter.java:51-63` | `getInstanceId()` и `playerTriggerInstanceId()` возвращают только `player.getUUID().getLeastSignificantBits()`. Два разных `UUID` с одинаковым LSB (вероятность 2^-64, но детерминирована) делят один `AnimatableInstanceCache` → анимация одного игрока управляет другим. `SingletonGeoAnimatable` кэш keyed by `instanceId`, а не по полному UUID. | Использовать `UUID.hashCode()` / `Most+Least ^` / `Objects.hash(msb,lsb)` или конкатенацию в `long` через `msb ^ lsb`. Тест: два мок-UUID с одинаковым LSB должны давать разные id. |
| **major** | `src/client/java/jujutsu/mod/client/render/HiddenBodyRenderGate.java:17-33` и `src/client/java/jujutsu/mod/client/render/ShadowBodySink.java:18-35,33-35` | TTL гейтов на **wall-clock** (`System.currentTimeMillis() + ttl*50`) тогда как `sinkProgress/emergeProgress` считаются от **server gameTime** (`startGameTime` vs `frameTime = level.gameTime + partialTick`). При hit-stop (Resonance меняет tick rate, KNOWN_ISSUES Accepted), паузе, лаге или разнице скорости тиков wall-clock и gameTime расходятся: тело может become visible раньше чем кончится dive (преждевременный fail-open) или наоборот висеть скрытым дольше положенного. Slack `UNDER_TTL_TICKS=8` частично маскирует, но не лечит рассинхрон. `read ShadowBodySink.java:18-35` + `read CharacterSkinAnimationMixin.java:129-135`. | Унифицировать на gameTime: хранить `expireGameTime = startGameTime + ttl` и сверять с `level.getGameTime()`; либо хотя бы документировать что wall-clock намеренно fail-open и замерить дрейф при hit-stop. |
| **minor** | `src/client/java/jujutsu/mod/client/render/megumi/MegumiSkinAnimationAdapter.java:10`, `.../nobara/NobaraSkinAnimationAdapter.java:10`, `.../todo/TodoSkinAnimationAdapter.java:10` | `WeakHashMap<AbstractClientPlayer,SwingState>` keyed by live `AbstractClientPlayer` instance. При respawn/dimension change создаётся новый entity объект, старый key становится weakly reachable и висит до GC. При 100+ игроков/быстрой смене сосудов map кратковременно растёт; также `SwingState.variant=-1` и `combatIdleUntilTick=-1` — магия, неочевидная для нового сосуда. Функционально ок (проверено `grep WeakHashMap`), но отсутствие явной очистки на `CLIENT_DISCONNECT` может оставить stale TTL для `HiddenBodyRenderGate` дольше чем нужно. | Добавить `ClientPlayConnectionEvents.DISCONNECT` очистку `swingStates` или перейти на `IntMap` по `player.getId()` с явным `remove` при `isRemoved()`. Задокументировать `variant=-1` как "next swing → punch_1". |
| **minor** | `src/client/java/jujutsu/mod/client/mixin/CharacterSkinAnimationMixin.java:30-61,64-109` | Состояние анимации хранится в **поле синглтона** `jujutsumod$skinAnimationState` на единственном `PlayerRenderer`. Рендер игроков идёт последовательно (`PlayerRenderer.render` вызывается per-entity), и `finally { close() }` закрывает, но при исключении между `apply` (Inject) и `WrapMethod` поле может быть перезаписано следующим игроком до `close()` предыдущего (если Mojang когда-то распараллелит entity render). Сейчас безопасно, но хрупко; плюс `Shadow` `model` может быть не `PlayerModel` для spectator. | Сделать `ThreadLocal<CharacterSkinAnimationState>` или локальную переменную внутри `@WrapMethod` вместо поля, или стек `Deque`. Оставить `field` только если добавить assert single-thread. |
| **minor** | `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationAdapter.java:95-108` | Early-return при `isCrouching/isVisuallySwimming/isFallFlying/bedOrientation/isPassenger/isAutoSpinAttack` возвращает `null` без очистки `GeoRenderState` data tickets. Поскольку `addSkinAnimationData` не вызывается, `COMBAT_IDLE`/`LOCOMOTION_VARIANT` предыдущего кадра остаётся в `AnimatableInstanceCache` до следующего успешного кадра. На практике билеты ephemeral (кладутся каждый кадр), но Megumi `combat_idle` window 22t может "залипнуть" если игрок присел в середине window — следующий кадр после вставания получит stale `true`. | Либо явно `geoState.removeGeckolibData(COMBAT_IDLE)` в early-return, либо документировать что `baseAnimation` проверяет `!movement.moving()` и stationary guard и stale флаг не влияет. Добавить тест на crouch mid-combatIdle. |
| **minor** | `src/client/java/jujutsu/mod/client/render/todo/TodoSkinAnimationAdapter.java:26-27` + `src/client/java/jujutsu/mod/client/render/nobara/NobaraSkinAnimationAdapter.java:32-33` | `LOCOMOTION_VARIANT = floorMod(tickCount/120 + getId(),2)` использует **entity id**, который меняется при респавне и может быть переиспользован. Вариант idle/walk флипает непредсказуемо после смерти, тогда как Megumi вообще не использует variant (у него один idle). Несоответствие — minor visual jitter. | Использовать стабильный `UUID.getLeastSignificantBits()` или `hashCode` вместо `getId()`, как в `getInstanceId`. Унифицировать между Todo/Nobara. |
| **minor** | `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationModel.java:47-52` + `CharacterSkinAnimationAdapter.java:133-146` | `applyVanillaArmPose` копирует `poseModel.rightArm/leftArm` только когда `actionKeyframedIsPlaying==false` **и** `isUsingItem`/`ArmPose` требует. Для vessel с кастомным `actionKeyframedIsPlaying` (Megumi включает `combat_idle/summon/shadow_*`, Todo — `boogie_woogie/attack`) — если новая анимация забудет добавить себя в `isX()` guard, ванильная поза рук тихо перетрёт авторскую. Нет compile-time защиты. | Добавить `CharacterSkinAnimationPackTest`-подобный тест что каждый `triggerableAnim` сосуда входит в его `actionKeyframedIsPlaying` allowlist, иначе fail. Сейчас такой инвариант не проверяется. |
| **minor** | `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationAdapter.java:158-176` | `accumulated()` + `ancestorsWithoutRoot()` + `waistCompensated()` делают quaternion compose `ZYX` каждый кадр для 7 частей; аллокации `new Vector3f/Quaternionf` per-part per-player per-frame. При 10 игроках × 60fps — ~420 кватернионов/кадр. Не критично, но `SdfRenderer` уже отмечен в `KNOWN_ISSUES.md:E6` как per-shape flush; здесь аналогичный per-frame allocation pressure. | Профилировать; при необходимости переиспользовать `ThreadLocal<Quaternionf>` или пул. Сейчас — nit, не править без профайла. |

> Проверено отсутствие дублирования: `grep -rn CharacterGeoRenderer src/` = 0 живых хитов; `archive/character-player-gecko/manifest.txt:6-27` вне `src/main`/`src/client`/Gradle resources → не пакуется в jar (`gradle build` + `ProjectSanityTest:1025,1064` это пинуют). Vessel-специфичные `HeldItemLayer` удалены, held items теперь vanilla layers (`Vessel-render-stack.md:22`). Дублирования shared кода нет — `CharacterSkinAnimationAdapter/Model/State` единственные.

---

### 3) Позитивные наблюдения

- **Vessel Seam соблюдён образцово.** `CharacterSkinAnimationRenderer.apply()` (`:14-23`) и `renderScale()` (`:26-33`) не содержат `switch/if` по сосуду — только `JujutsuCharacterClients.definition(selection.character()).skinAnimation()/bodyScale()`. Новые сосуды добавляются без правки shared файлов — проверено `read JujutsuCharacterClients.java:26-33` (exhaustive switch в одном месте) и отсутствием `JujutsuCharacter.NOBARA/TODO/MEGUMI` в `src/client/java/jujutsu/mod/client/render/` (grep 0 хитов).
- **Архивация чистая.** Старый Geo-replaced стек полностью вынесен в `archive/character-player-gecko/` и `archive/character-skin-animation/`, не в classpath, не в `jujutsumod.client.mixins.json`. `ProjectSanityTest:1025` и `:1120` явно ассертят что `CharacterRenderDispatchMixin` не в миксинах.
- **Shared bridge минимален и корректен.** `CharacterSkinAnimationAdapter.getRenderType()=null`, колбэки no-op, `fillRenderState` + `handleAnimations` → `applyPose` с `finally { snapshot.close() }` даже при `RuntimeException` (`:119-122`). Snapshot сохраняет `x/y/z/xRot/yRot/zRot/xScale/yScale/zScale/visible/skipDraw` — полный restore, idempotent `close()`.
- **Pose math аккуратная.** `Transform.fromGeo` инвертирует X, `waistCompensated` конъюгирует поворот вокруг `BODY_WAIST_PIVOT_Y=12`, `ancestorsWithoutRoot` компонует кватернионы, `vanillaEuler ZYX` — без покомпонентного сложения углов (распространённая ошибка). `applyPart` пишет `xRot/yRot/zRot` напрямую.
- **Megumi `punch_1→punch_2→kick` верно.** `MegumiSkinAnimationAdapter:22-29` цикл `(variant+1)%3`, `MegumiPlayerGeoAnimatable:40-44,87-89` три `RawAnimation`, `actionKeyframedIsPlaying:124-137` покрывает все melee + `combat_idle` + `summon` + `shadow_*`. `LOCOMOTION_VARIANT` для Megumi намеренно отсутствует — у него один idle/walk/run, без вариантов (Codex: Megumi pack single idle).
- **Todo/Nobara анимации чисты.** Todo: `attack` + `ability.boogie_woogie` (торс коил, не ноги) + locomotion variant 120t, `headLookWeight` clamp 38/22°. Nobara: 17 клипов, `headLookWeight` с damped 0.35/0.25/0.55/0.72, `LOCOMOTION_VARIANT` аналогично Todo. Оба используют `forceAnimationReset()+tryTriggerAnimation` — перезапуск без залипания.
- **VFX → анимация разделены правильно.** `MegumiVfxRecipes` триггерит `summon_divine_dogs/shadow_dive/shadow_emerge` через сервер-подтверждённый `VfxCue`; `punch_1/2/kick` — чисто client-side на `player.swinging` (presentation, не геймплей) — как требует `AGENTS.md:45` ("presentation state changes no melee gameplay").
- **Hidden/sink fail-open.** Оба гейта expire lazily, `markRevealed/remove` при `emerge`, `isHidden` чистит просрочку. Потеря пакета → тело становится видимым, не застревает под полом.
- **Отсутствие дублирования.** Нет второго механизма рендера, нет копипасты `HeldItemLayer` на сосуд, нет пер-сосуд `if` в shared коде. Каждый сосуд поставляет только `SkinAnimationAdapter+Model+Animatable+assets`, как требует `add-vessel` skill.

---

### 4) Вердикт

**PASS WITH CAVEATS**

Срез реализован **корректно и без дублирования**. Миграция `CharacterGeoRenderer` → `CharacterSkinAnimation` выполнена чисто: shared код минимален, vessel seam не нарушен, архив изолирован, snapshot/restore и pose math верны, Megumi цикл `punch_1→punch_2→kick` и `summon_divine_dogs`/`shadow_*` триггеры на месте, Todo/Nobara клипы покрыты, held-items делегированы vanilla.

Критичных рантайм-багов (краш, десинк геймплея, утечка памяти) не найдено. Единственные **major** — это **документационный дрейф** (`AGENTS.md`/`README.md` врут о живом стеке) и два дизайн-нюанса (LSB-only instanceId + wall-clock vs gameTime TTL), которые не ломают текущую игру на 1-2 игроков, но должны быть поправлены до паблика/большого онлайна.

**Приоритет фиксов:**

1. **P1 (до мерджа / ближайший PR):** Обновить `AGENTS.md:45-46` и `README.md:24` под `Vessel-render-stack.md` (skin-bridge). Иначе каждый новый сосуд будет реализован по устаревшему шаблону.
2. **P2 (следующий tech-debt спринт):** Починить `CharacterSkinAnimationAdapter:51-63` LSB-only `instanceId` (использовать полный UUID) + унифицировать TTL на gameTime в `HiddenBodyRenderGate`/`ShadowBodySink` (или задокументировать wall-clock trade-off и покрыть тестом hit-stop).
3. **P3 (polish):** Очистка `WeakHashMap` при disconnect, стабилизация `LOCOMOTION_VARIANT` на UUID вместо `getId()`, тест что каждый `triggerableAnim` входит в `actionKeyframedIsPlaying`, `ThreadLocal` для `CharacterSkinAnimationMixin` state если планируется параллельный entity render.

AUDIT_ID: 20-client-renderers
