# run-megumi-d — Блок D: пост-мерж фиксы Megumi + client desync + #75/#87 leftovers

Worktree: `D:/WorkFlow/jm-wt-megumi`, ветка `fix/review-megumi-misc` (чистая, не пересоздавать).
Env: `JAVA_HOME=~/scoop/apps/temurin21-jdk/current ./gradlew ...`
Spec: `D:/WorkFlow/jujutsu-minecraft/audit/review-3pr/REVIEW-SUMMARY.md` + reviewer-pr*.md; issues #91 #95 #96 #97 #98 #99, #90 п.4.

| Slice | Worker | Файлы | Artifact | Status |
|---|---|---|---|---|
| #91 elephant clock | worker | MegumiHostilityPolicy, MegumiElephantBrain, MegumiSummonRuntime(read), test | worker-91-elephant.md | done |
| #96 retaliation expiry | worker | MegumiShikigamiRuntime, MegumiSummonRuntime, MegumiRetaliationPolicy, GameTest | worker-96-retaliation.md | done |
| #95 client desync | worker | ClientCharacterSelectionManager, ClientAbilityCooldowns, test | worker-95-selection.md | done |
| #90.4 destructible tag | worker | destructible_by_shikigami.json, MegumiElephantPresencePolicy comment | worker-90-tag.md | done |
| #99 slam dead zone | worker | CursedSpiritAttackGoal, test | worker-99-slam.md | done |
| #97/#98 minors | worker | MegumiRabbitsBrain, CursedSpiritVariant, CursedSpiritRenderer, codex Megumi-shikigami.md, MegumiShikigamiCrossTests, KNOWN_ISSUES | worker-minors.md | done |
| issue-90-p4 elephant footprint tag | worker | done | worker-90-tag.md | destructible_by_shikigami сужен до натурального террейна, javadoc и контракт-тест обновлены |
