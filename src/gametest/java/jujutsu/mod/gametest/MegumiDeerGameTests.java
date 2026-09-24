package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiDeerEntity;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.registry.JujutsuEffects;

/** Round Deer lifecycle, support, cleansing, targeting and coexistence server scenarios. */
public final class MegumiDeerGameTests {
	private static final int SUMMON_TICK = 2;
	private static final int ACTIVE_TICK = 24;
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 400;

	private static void paveFloor(GameTestHelper helper) {
		for (int x = -1; x <= 9; x++) {
			for (int z = -1; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** Opaque lid prevents parked test zombies from taking daytime fire damage. */
	private static void layRoof(GameTestHelper helper) {
		for (int x = -1; x <= 9; x++) {
			for (int z = -1; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer setupOwner(GameTestHelper helper, String fixture) {
		return MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
	}

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(3, 1, 3));
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer owner) {
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
	}

	private static void cleanupDamageableOwner(GameTestHelper helper, ServerPlayer owner) {
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
		CursedSpiritTestFixtures.cleanupVictim(helper, owner);
	}

	private static void summon(GameTestHelper helper, String fixture, ServerPlayer owner,
			MegumiShikigami type) {
		MegumiShikigamiSelection.set(owner.getUUID(), type);
		boolean summoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
		helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"summon", helper.getTick(), owner.getUUID(), type.id() + " summoned", true, summoned));
	}

	private static List<MegumiDeerEntity> deerOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiDeerEntity> owned = new ArrayList<>();
		for (MegumiDeerEntity deer : level.getEntities(EntityTypeTest.forClass(MegumiDeerEntity.class),
				candidate -> true)) {
			if (ownerId.equals(deer.ownerUuid())) {
				owned.add(deer);
			}
		}
		return owned;
	}

	private static List<MegumiNueEntity> nueOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiNueEntity> owned = new ArrayList<>();
		for (MegumiNueEntity nue : level.getEntities(EntityTypeTest.forClass(MegumiNueEntity.class),
				candidate -> true)) {
			if (ownerId.equals(nue.ownerUuid())) {
				owned.add(nue);
			}
		}
		return owned;
	}

	private static Zombie spawnFrozenZombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, feet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		// Cross-test isolation: the tag bars every foreign coordinator's autonomous mark (manual
		// sic and retaliation bypass it). No invulnerable — the no-damage oracle must stay real:
		// an unhittable target would pass the assertion without the Deer ever being tested.
		zombie.addTag("jujutsu.autonomous_mark.none");
		CursedSpiritTestFixtures.freezeGround(zombie);
		return zombie;
	}

	/** Summon (D1), materialization, manual recall and the per-type recall cooldown (D2). */
	@GameTest(maxTicks = 60)
	public void summonActivatesAndManualRecallChargesItsCooldown(GameTestHelper helper) {
		String fixture = "summonActivatesAndManualRecallChargesItsCooldown";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(ACTIVE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			List<MegumiDeerEntity> deer = deerOwnedBy(level, owner.getUUID());
			helper.assertTrue(deer.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"active", helper.getTick(), owner.getUUID(), "owned Deer bodies", 1, deer.size()));
			helper.assertTrue(deer.get(0).combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"active", helper.getTick(), owner.getUUID(), "Deer phase is ACTIVE", true,
					deer.get(0).combatEnabled()));
			helper.assertTrue(MegumiShikigamiTestFixtures.hasPack(level.getServer(), owner.getUUID(),
					MegumiShikigami.DEER), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"active", helper.getTick(), owner.getUUID(), "Deer pack present", true,
					MegumiShikigamiTestFixtures.hasPack(level.getServer(), owner.getUUID(), MegumiShikigami.DEER)));
		}));

		helper.runAtTickTime(30, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			boolean recalled = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"recall", helper.getTick(), owner.getUUID(), "second primary recalls Deer", true, recalled));
			long cooldown = MegumiSummonCooldowns.remainingTicks(owner.getUUID(), MegumiShikigami.DEER,
					level.getGameTime());
			helper.assertTrue(cooldown == EXPECTED_RECALL_COOLDOWN_TICKS,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), owner.getUUID(),
							"per-type recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, cooldown));
			int primaryCooldown = CharacterAbilityCooldowns.remainingTicks(owner, CharacterAbility.PRIMARY);
			helper.assertTrue(primaryCooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"recall", helper.getTick(), owner.getUUID(), "shared PRIMARY cooldown", 0, primaryCooldown));
			MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", owner);
		}));
		helper.runAtTickTime(45, () -> {
			try {
				List<MegumiDeerEntity> deer = deerOwnedBy(level, owner.getUUID());
				helper.assertTrue(deer.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recalled", helper.getTick(), owner.getUUID(), "recall sink removed the Deer body", 0,
						deer.size()));
			} finally {
				cleanup(helper, owner);
			}
			helper.succeed();
		});
	}

	/** A floorless placement refusal leaves no Deer, pack record or summon cooldown. */
	@GameTest(maxTicks = 20)
	public void noRoomRefusesDeerSummonWithoutPartialState(GameTestHelper helper) {
		String fixture = "noRoomRefusesDeerSummonWithoutPartialState";
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 6; z++) {
				for (int y = -3; y <= 0; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		ServerPlayer owner = setupOwner(helper, fixture);
		try {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DEER);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"no-room", helper.getTick(), owner.getUUID(), "unsafe ground placement refused", false, summoned));
			helper.assertTrue(deerOwnedBy(helper.getLevel(), owner.getUUID()).isEmpty(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "no-room", helper.getTick(), owner.getUUID(),
							"no staged Deer remains", 0, deerOwnedBy(helper.getLevel(), owner.getUUID()).size()));
			helper.assertTrue(!MegumiShikigamiTestFixtures.hasPack(helper.getLevel().getServer(), owner.getUUID(),
					MegumiShikigami.DEER), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"no-room", helper.getTick(), owner.getUUID(), "no committed Deer pack", false,
					MegumiShikigamiTestFixtures.hasPack(helper.getLevel().getServer(), owner.getUUID(), MegumiShikigami.DEER)));
			long cooldown = MegumiSummonCooldowns.remainingTicks(owner.getUUID(), MegumiShikigami.DEER,
					helper.getLevel().getGameTime());
			helper.assertTrue(cooldown == 0L, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"no-room", helper.getTick(), owner.getUUID(), "no cooldown on refusal", 0, cooldown));
		} finally {
			cleanup(helper, owner);
		}
		helper.succeed();
	}

	/** Death teardown charges only Deer’s per-type death cooldown. */
	@GameTest(maxTicks = 70)
	public void deathTeardownChargesDeerDeathCooldown(GameTestHelper helper) {
		String fixture = "deathTeardownChargesDeerDeathCooldown";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(30, () -> {
			try {
				List<MegumiDeerEntity> deer = deerOwnedBy(level, owner.getUUID());
				helper.assertTrue(deer.size() == 1 && deer.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "death", helper.getTick(), owner.getUUID(),
								"one active Deer before lethal hit", "1 active", deer.size()));
				boolean damaged = deer.get(0).hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"death", helper.getTick(), owner.getUUID(), "lethal damage routed", true, damaged));
				long cooldown = MegumiSummonCooldowns.remainingTicks(owner.getUUID(), MegumiShikigami.DEER,
						level.getGameTime());
				helper.assertTrue(cooldown == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "death", helper.getTick(), owner.getUUID(),
								"per-type death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, cooldown));
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "death", owner);
			} finally {
				cleanup(helper, owner);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/** Global sic marks Deer, but the support-only body never damages the mark. */
	@GameTest(maxTicks = 80, skyAccess = true)
	public void globalSicIsThreatAwarenessWithoutDeerAttack(GameTestHelper helper) {
		String fixture = "globalSicIsThreatAwarenessWithoutDeerAttack";
		paveFloor(helper);
		layRoof(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		ServerLevel level = helper.getLevel();
		Zombie target = spawnFrozenZombie(helper, fixture, new BlockPos(2, 1, 7));
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(ACTIVE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			List<MegumiDeerEntity> deers = deerOwnedBy(level, owner.getUUID());
			helper.assertTrue(deers.size() == 1,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "active", helper.getTick(), owner.getUUID(),
							"one Deer body is active", 1, deers.size()));
			deerRef.set(deers.get(0));
			helper.assertTrue(MegumiShikigamiTestFixtures.hasPack(level.getServer(), owner.getUUID(),
					MegumiShikigami.DEER),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "active", helper.getTick(), owner.getUUID(),
							"Deer pack exists", true, false));
		}));
		helper.runAtTickTime(30, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			boolean marked = MegumiShikigamiRuntime.trySic(owner, false);
			helper.assertTrue(marked, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), owner.getUUID(), "global sic found target", true, marked));
			helper.assertTrue(deerRef.get().getTarget() == target,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), owner.getUUID(),
							"global mark reaches Deer", target.getUUID(), deerRef.get().getTarget()));
		}));
		helper.runAtTickTime(55, () -> {
			try {
				helper.assertTrue(target.getHealth() == target.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "no-attack", helper.getTick(), owner.getUUID(),
								"sic target receives no Deer damage", target.getMaxHealth(), target.getHealth()));
				helper.assertTrue(owner.getHealth() == owner.getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "friendly-fire", helper.getTick(), owner.getUUID(),
								"owner never takes Deer damage", owner.getMaxHealth(), owner.getHealth()));
				helper.assertTrue(deerRef.get().getHealth() == deerRef.get().getMaxHealth(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "friendly-fire", helper.getTick(), owner.getUUID(),
								"Deer does not damage itself", deerRef.get().getMaxHealth(),
								deerRef.get().getHealth()));
			} finally {
				cleanup(helper, owner);
			}
			helper.succeed();
		});
	}

	/** Deer’s autonomous support pulse heals the owner and respects the full pulse cooldown. */
	@GameTest(maxTicks = 110)
	public void autonomousOwnerHealingEmitsDiscreteCooldownBoundedPulses(GameTestHelper helper) {
		String fixture = "autonomousOwnerHealingEmitsDiscreteCooldownBoundedPulses";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		owner.setHealth(8.0f);
		ServerLevel level = helper.getLevel();
		AtomicLong firstPulseAt = new AtomicLong(Long.MIN_VALUE);
		AtomicBoolean complete = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		for (int tick = 18; tick <= 105; tick++) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (complete.get()) {
					return;
				}
				try {
					List<MegumiDeerEntity> deer = deerOwnedBy(level, owner.getUUID());
					if (deer.isEmpty()) {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"first-pulse", helper.getTick(), owner.getUUID(),
								"deer body alive during pulse window", "present", "missing"));
						return;
					}
					long first = firstPulseAt.get();
					if (first == Long.MIN_VALUE && deer.get(0).actionTicks() > 0) {
						firstPulseAt.set(level.getGameTime());
						helper.assertTrue(owner.getHealth() == 14.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "first-pulse", helper.getTick(),
										owner.getUUID(), "one configured heal pulse", 14.0f, owner.getHealth()));
						owner.setHealth(8.0f);
						return;
					}
					if (first == Long.MIN_VALUE) {
						if (pollTick == 105) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"first-pulse", helper.getTick(), owner.getUUID(), "autonomous pulse arrived",
									"within 88 ticks", "none"));
						}
						return;
					}
					long elapsed = level.getGameTime() - first;
					if (elapsed < MegumiShikigamiProfile.DEER_PULSE_COOLDOWN_TICKS - 1L) {
						helper.assertTrue(owner.getHealth() == 8.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(),
										owner.getUUID(), "no second pulse during cooldown", 8.0f, owner.getHealth()));
					}
					if (elapsed >= MegumiShikigamiProfile.DEER_PULSE_COOLDOWN_TICKS
							+ MegumiShikigamiProfile.DEER_SCAN_TICKS + 2L) {
						helper.assertTrue(owner.getHealth() == 14.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown-ended", helper.getTick(),
										owner.getUUID(), "next discrete pulse after cooldown", 14.0f, owner.getHealth()));
						cleanup(helper, owner);
						complete.set(true);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanup(helper, owner);
					throw failure;
				}
			});
		}
	}

	/** A wounded older shikigami is a legal healing recipient; Deer and Nue remain separate packs. */
	@GameTest(maxTicks = 100)
	public void pulseHealsWoundedOlderShikigamiAndCoexists(GameTestHelper helper) {
		String fixture = "pulseHealsWoundedOlderShikigamiAndCoexists";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicBoolean damagedNue = new AtomicBoolean();
		AtomicBoolean complete = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			summon(helper, fixture, owner, MegumiShikigami.DEER);
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.NUE);
			boolean nueSummoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(nueSummoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"coexist", helper.getTick(), owner.getUUID(), "old Nue pack summoned beside Deer", true, nueSummoned));
		}));
		helper.runAtTickTime(25, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			List<MegumiNueEntity> nues = nueOwnedBy(level, owner.getUUID());
			helper.assertTrue(nues.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"wound", helper.getTick(), owner.getUUID(), "one old Nue body", 1, nues.size()));
			nues.get(0).setHealth(5.0f);
			damagedNue.set(true);
		}));
		for (int tick = 26; tick <= 65; tick++) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (complete.get() || !damagedNue.get()) {
					return;
				}
				try {
					List<MegumiNueEntity> nues = nueOwnedBy(level, owner.getUUID());
					if (!nues.isEmpty() && nues.get(0).getHealth() > 5.0f) {
						helper.assertTrue(nues.get(0).getHealth() == 11.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "heal-old-type", helper.getTick(),
										owner.getUUID(), "one pulse heals old shikigami", 11.0f, nues.get(0).getHealth()));
						helper.assertTrue(MegumiShikigamiTestFixtures.hasPack(level.getServer(), owner.getUUID(),
								MegumiShikigami.DEER)
								&& MegumiShikigamiTestFixtures.hasPack(level.getServer(), owner.getUUID(), MegumiShikigami.NUE),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), owner.getUUID(),
										"both pack records survive support pulse", true, false));
						cleanup(helper, owner);
						complete.set(true);
						helper.succeed();
					} else if (pollTick == 65) {
						cleanup(helper, owner);
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"heal-old-type", helper.getTick(), owner.getUUID(), "wounded old type receives pulse",
								"health > 5", nues.isEmpty() ? "no Nue" : nues.get(0).getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanup(helper, owner);
					throw failure;
				}
			});
		}
	}

	/** Cleanse removes allowed debuffs but preserves fear, grip, and the Slowness-100 hold state. */
	@GameTest(maxTicks = 70)
	public void cleanseRemovesVanillaDebuffButPreservesAuthorityMarkers(GameTestHelper helper) {
		String fixture = "cleanseRemovesVanillaDebuffButPreservesAuthorityMarkers";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		owner.setHealth(10.0f);
		owner.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
		owner.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
		owner.addEffect(new MobEffectInstance(JujutsuEffects.CURSED_FEAR, 200, 0));
		owner.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED, 200, 0));
		owner.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_SHADOW_GRIP, 200, 0));
		owner.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 100));
		ServerLevel level = helper.getLevel();
		AtomicBoolean complete = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		for (int tick = 18; tick <= 45; tick++) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (complete.get()) {
					return;
				}
				try {
					if (!owner.hasEffect(MobEffects.POISON)) {
						helper.assertTrue(owner.getHealth() > 10.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "cleanse", helper.getTick(),
										owner.getUUID(), "positive-energy pulse healed owner", "> 10", owner.getHealth()));
						helper.assertTrue(owner.hasEffect(JujutsuEffects.CURSED_FEAR)
								&& owner.hasEffect(JujutsuEffects.GRIPPED)
								&& owner.hasEffect(JujutsuEffects.MEGUMI_SHADOW_GRIP)
								&& owner.hasEffect(MobEffects.SLOWNESS),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "marker-safety", helper.getTick(),
										owner.getUUID(), "authority/hold effects survive", true, false));
						helper.assertTrue(owner.hasEffect(MobEffects.DARKNESS) && owner.hasEffect(MobEffects.NAUSEA),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "fear-safety", helper.getTick(),
										owner.getUUID(), "fear's readable debuffs survive with marker", true, false));
						cleanup(helper, owner);
						complete.set(true);
						helper.succeed();
					} else if (pollTick == 45) {
						cleanup(helper, owner);
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"cleanse", helper.getTick(), owner.getUUID(), "pulse cleanses allowed poison",
								"poison absent", "poison active"));
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanup(helper, owner);
					throw failure;
				}
			});
		}
	}

	/** An empty owned scan ignores a wounded hostile and does not spend the pulse cooldown. */
	@GameTest(maxTicks = 70)
	public void emptyScanDoesNotHealEnemiesOrConsumePulseCooldown(GameTestHelper helper) {
		String fixture = "emptyScanDoesNotHealEnemiesOrConsumePulseCooldown";
		paveFloor(helper);
		layRoof(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		ServerLevel level = helper.getLevel();
		Zombie enemy = spawnFrozenZombie(helper, fixture, new BlockPos(4, 1, 5));
		enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
		enemy.setHealth(5.0f);
		enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
		AtomicBoolean injuredOwner = new AtomicBoolean();
		AtomicBoolean complete = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(25, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			helper.assertTrue(enemy.getHealth() == 5.0f && enemy.hasEffect(MobEffects.WEAKNESS),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "empty-scan", helper.getTick(), owner.getUUID(),
							"hostile outside the owned list is untouched", "5 + weakness",
							enemy.getHealth() + " + " + enemy.hasEffect(MobEffects.WEAKNESS)));
			owner.setHealth(10.0f);
			injuredOwner.set(true);
		}));
		for (int tick = 26; tick <= 52; tick++) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (complete.get() || !injuredOwner.get()) {
					return;
				}
				try {
					helper.assertTrue(enemy.getHealth() == 5.0f && enemy.hasEffect(MobEffects.WEAKNESS),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "enemy-exclusion", helper.getTick(),
									owner.getUUID(), "enemy remains unhealed and uncleansed", "5 + weakness",
									enemy.getHealth() + " + " + enemy.hasEffect(MobEffects.WEAKNESS)));
					if (owner.getHealth() > 10.0f) {
						helper.assertTrue(owner.getHealth() == 16.0f,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "empty-scan", helper.getTick(),
										owner.getUUID(), "next scheduled scan heals owner", 16.0f, owner.getHealth()));
						cleanup(helper, owner);
						complete.set(true);
						helper.succeed();
					} else if (pollTick == 52) {
						cleanup(helper, owner);
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"empty-scan", helper.getTick(), owner.getUUID(), "empty scan leaves pulse ready",
								"owner health > 10", owner.getHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanup(helper, owner);
					throw failure;
				}
			});
		}
	}

	/** Retaliation may mark its source, but Deer never converts that mark into a damaging action. */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void retaliationMarkNeverTurnsDeerIntoAnAttacker(GameTestHelper helper) {
		String fixture = "retaliationMarkNeverTurnsDeerIntoAnAttacker";
		paveFloor(helper);
		layRoof(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture);
		ServerLevel level = helper.getLevel();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicReference<MegumiDeerEntity> deerRef = new AtomicReference<>();
		AtomicLong markedAt = new AtomicLong(Long.MIN_VALUE);
		AtomicBoolean complete = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(40, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			Zombie attacker = spawnFrozenZombie(helper, fixture, new BlockPos(3, 1, 6));
			attackerRef.set(attacker);
			List<MegumiDeerEntity> deer = deerOwnedBy(level, owner.getUUID());
			helper.assertTrue(deer.size() == 1 && deer.get(0).combatEnabled(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "attack-owner", helper.getTick(),
							owner.getUUID(), "one active Deer", 1, deer.size()));
			deerRef.set(deer.get(0));
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			// The attack itself records the real retaliation signal; Deer is support-only thereafter.
		}));
		for (int tick = 41; tick <= 140; tick++) {
			final int pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (complete.get()) {
					return;
				}
				try {
					Zombie attacker = attackerRef.get();
					MegumiDeerEntity deer = deerRef.get();
					if (attacker == null || deer == null) {
						return;
					}
					if (markedAt.get() == Long.MIN_VALUE && deer.getTarget() == attacker) {
						markedAt.set(level.getGameTime());
					}
					long marked = markedAt.get();
					if (marked != Long.MIN_VALUE && level.getGameTime() >= marked + 12L) {
						helper.assertTrue(attacker.getHealth() == attacker.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation", helper.getTick(),
										owner.getUUID(), "retaliation target takes no Deer damage",
										attacker.getMaxHealth(), attacker.getHealth()));
						cleanupDamageableOwner(helper, owner);
						complete.set(true);
						helper.succeed();
					} else if (pollTick == 140) {
						cleanupDamageableOwner(helper, owner);
						helper.assertTrue(marked != Long.MIN_VALUE,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation", helper.getTick(),
										owner.getUUID(), "runtime assigns the actual attacker as Deer mark", true,
										deer.getTarget()));
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanupDamageableOwner(helper, owner);
					throw failure;
				}
			});
		}
	}

	/** Owner disconnect cleanup removes the body and pack without charging a cooldown. */
	@GameTest(maxTicks = 60)
	public void ownerDisconnectCleanupRemovesDeerWithoutCooldown(GameTestHelper helper) {
		String fixture = "ownerDisconnectCleanupRemovesDeerWithoutCooldown";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(ACTIVE_TICK, () -> {
			try {
				UUID ownerId = owner.getUUID();
				MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
						MegumiShikigamiRuntime.TeardownReason.DISCONNECT);
				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "disconnect", owner);
				helper.assertTrue(deerOwnedBy(helper.getLevel(), ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "disconnect", helper.getTick(), ownerId,
								"disconnected owner's body is discarded", 0, deerOwnedBy(helper.getLevel(), ownerId).size()));
				long cooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.DEER,
						helper.getLevel().getGameTime());
				helper.assertTrue(cooldown == 0L, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"disconnect", helper.getTick(), ownerId, "no cooldown on disconnect", 0, cooldown));
			} finally {
				cleanup(helper, owner);
			}
			helper.succeed();
		});
	}

	/** Dimension teardown recalls Deer and charges the per-type recall family. */
	@GameTest(maxTicks = 70)
	public void dimensionCleanupChargesRecallFamilyAndRemovesPack(GameTestHelper helper) {
		String fixture = "dimensionCleanupChargesRecallFamilyAndRemovesPack";
		paveFloor(helper);
		ServerPlayer owner = setupOwner(helper, fixture);
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner,
				() -> summon(helper, fixture, owner, MegumiShikigami.DEER)));
		helper.runAtTickTime(ACTIVE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			UUID ownerId = owner.getUUID();
			MegumiShikigamiRuntime.teardown(helper.getLevel().getServer(), ownerId,
					MegumiShikigamiRuntime.TeardownReason.DIMENSION_CHANGE);
			MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "dimension", owner);
			long cooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.DEER,
					helper.getLevel().getGameTime());
			helper.assertTrue(cooldown == EXPECTED_RECALL_COOLDOWN_TICKS,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "dimension", helper.getTick(), ownerId,
							"dimension dismissal charges recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, cooldown));
		}));
		helper.runAtTickTime(ACTIVE_TICK + MegumiShikigamiProfile.DEER_RECALL_TICKS + 2, () -> {
			try {
				List<MegumiDeerEntity> deers = deerOwnedBy(helper.getLevel(), owner.getUUID());
				helper.assertTrue(deers.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"dimension-recalled", helper.getTick(), owner.getUUID(), "dimension teardown removes body",
						0, deers.size()));
			} finally {
				cleanup(helper, owner);
			}
			helper.succeed();
		});
	}
}
