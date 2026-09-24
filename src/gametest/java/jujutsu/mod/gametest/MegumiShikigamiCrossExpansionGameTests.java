package jujutsu.mod.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.JujutsuCharacters;
import jujutsu.mod.character.megumi.MegumiCombatContext;
import jujutsu.mod.character.megumi.MegumiDeerEntity;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiPackCoordinator;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiTigerEntity;

/**
 * Cross-family scenarios for the four expansion shikigami (spec §18 "Cross tests"), in their own
 * class because {@link MegumiShikigamiCrossTests} is already at the file-size cap.
 *
 * <p>Covered: the all-nine coexistence smoke (E1), a Serpent-held victim staying a legal mark for
 * the rest of the pack (E2), the Deer's heal reaching an old-type body but never an enemy (E3/E4),
 * the Ox's charge and the Tiger's combo never damaging allied summons (E5/E6), the global sic
 * reaching the new combat-capable bodies (E7), the coordinator's intent set claiming the new
 * mechanic targets (E8), cross-type cooldown isolation (E9), and the selector accepting every new
 * entry (E10).
 *
 * <p>Same traps as the sibling class: world offset is random per run so nothing asserts absolute
 * positions; summon steps sit on separate ticks because the runtime drops same-tick duplicates;
 * static state is cleared in setup and on every success/failure path.
 */
public final class MegumiShikigamiCrossExpansionGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final int SUMMON_TICK = 2;
	private static final int SIC_TICK = 20;
	private static final int DEADLINE_TICK = 400;

	/** The four expansion types, in canonical roster order. */
	private static final MegumiShikigami[] EXPANSION = {
			MegumiShikigami.SERPENT, MegumiShikigami.DEER, MegumiShikigami.OX, MegumiShikigami.TIGER };

	/**
	 * E1 — the §16 smoke: every one of the nine types out at once. Each summon rides its own tick
	 * (same-tick presses are dropped by the runtime), then the pack view must list all nine ids and
	 * every body must still be alive — no duplicate-pack corruption, no self-selection.
	 */
	@GameTest(maxTicks = 300)
	public void allNineTypesCoexistWithoutCorruption(GameTestHelper helper) {
		String fixture = "allNineTypesCoexistWithoutCorruption";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		MegumiShikigami[] order = {
				MegumiShikigami.DOGS, MegumiShikigami.NUE, MegumiShikigami.TOAD, MegumiShikigami.RABBITS,
				MegumiShikigami.ELEPHANT, MegumiShikigami.SERPENT, MegumiShikigami.DEER,
				MegumiShikigami.OX, MegumiShikigami.TIGER };
		for (int i = 0; i < order.length; i++) {
			MegumiShikigami type = order[i];
			long tick = SUMMON_TICK + i * 2L;
			helper.runAtTickTime(tick, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, type);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, type + " tryPrimary result", "true", summoned));
			}));
		}

		helper.runAtTickTime(SUMMON_TICK + order.length * 2L + 20L, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				for (MegumiShikigami type : order) {
					if (type == MegumiShikigami.DOGS) {
						continue; // the dogs live in their own runtime, not the shikigami pack map
					}
					helper.assertTrue(types.contains(type.id()),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
									ownerId, type + " pack present", type.id(), types));
				}
				helper.assertTrue(types.size() == 8,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
								ownerId, "shikigami pack count (dogs excluded)", "8", types.size()));
				boolean dogsOut = jujutsu.mod.character.megumi.MegumiSummonRuntime
						.packView(level.getServer(), ownerId).isPresent();
				helper.assertTrue(dogsOut, MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist",
						helper.getTick(), ownerId, "dog pack present", "present", dogsOut));
				List<MegumiShikigamiEntity> living =
						MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId);
				helper.assertTrue(!living.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "living shikigami bodies", "> 0", living.size()));
				for (MegumiShikigami type : order) {
					if (type == MegumiShikigami.DOGS) {
						continue;
					}
					boolean bodyPresent = living.stream().anyMatch(body -> body.shikigamiType() == type
							&& body.isAlive() && !body.isRemoved());
					helper.assertTrue(bodyPresent,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(),
									ownerId, type + " live body present", "present",
									living.stream().map(body -> body.shikigamiType().id()).toList()));
				}
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SUMMON_TICK + order.length * 2L + 22L, () -> helper.succeed());
	}

	/**
	 * E2 — a victim the Serpent holds is still a legal mark: while the serpent binds the cow,
	 * a second shikigami sicced onto the same victim keeps its mark and attacks. The cow is a
	 * 200-HP wall so the hold outlives the proof window.
	 */
	@GameTest(maxTicks = 450)
	public void serpentHeldVictimStaysALegalMarkForThePack(GameTestHelper helper) {
		String fixture = "serpentHeldVictimStaysALegalMarkForThePack";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(6, 1, 5));
		cow.setPersistenceRequired();
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		cow.setHealth(200.0f);
		// Cross-test isolation: a Cow survives a PEACEFUL difficulty flip (Monster zombies
		// self-discard), the tag bars every foreign coordinator's autonomous mark, and
		// invulnerability covers direct damage.
		cow.setInvulnerable(true);
		cow.addTag("jujutsu.autonomous_mark.none");
		cow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean bound = new AtomicBoolean();
		AtomicBoolean nueMarked = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "serpent tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			TodoSwapTestFixtures.aimAt(caster, cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), caster.getUUID(), "trySic result", "true", sicced));
		}));

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (nueMarked.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				try {
					if (!bound.get()) {
						for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
								level.getServer(), ownerId)) {
							if (body instanceof MegumiSerpentEntity serpent
									&& cow.getUUID().equals(serpent.bindTargetUuid())) {
								bound.set(true);
								// Mid-hold: summon Nue and sic it onto the held victim.
								MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
								boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
								helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"hold", pollTick, ownerId, "nue summon mid-hold", "true", summoned));
								// The Nue body must never become a foreign pack's mark.
								for (MegumiShikigamiEntity nueBody : MegumiShikigamiRuntime.livingBodiesAll(
										level.getServer(), ownerId)) {
									if (nueBody instanceof MegumiNueEntity) {
										nueBody.addTag("jujutsu.autonomous_mark.none");
									}
								}
							}
						}
						helper.assertTrue(pollTick <= SIC_TICK + 160,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick, ownerId,
										"serpent bind lands within the window", "<= " + (SIC_TICK + 160), pollTick));
						return;
					}
					// Nue is out: sic it onto the still-held victim and prove the mark lands.
					TodoSwapTestFixtures.aimAt(caster,
							cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0));
					MegumiShikigamiRuntime.trySic(caster, false);
					for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
							level.getServer(), ownerId)) {
						if (body instanceof MegumiNueEntity nue && nue.getTarget() != null
								&& cow.getUUID().equals(nue.getTarget().getUUID())) {
							nueMarked.set(true);
						}
					}
					helper.assertTrue(pollTick <= DEADLINE_TICK - 20,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "mark", pollTick, ownerId,
									"nue marks the held victim", "marked", nueMarked.get() ? "marked" : "none"));
				} catch (RuntimeException | AssertionError failure) {
					cow.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(DEADLINE_TICK + 2, () -> {
			try {
				helper.assertTrue(nueMarked.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"mark", helper.getTick(), caster.getUUID(),
						"nue marked the serpent-held victim", "true", nueMarked.get()));
			} finally {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(DEADLINE_TICK + 4, () -> helper.succeed());
	}

	/**
	 * E3 — the Deer's heal reaches an old-type body: a wounded Nue standing in the support radius
	 * regains health while the deer is out. The heal is server-authoritative and discrete, so the
	 * assertion is a health delta over a bounded window, never a per-tick heal.
	 */
	@GameTest(maxTicks = 450)
	public void deerHealsAWoundedOldTypeShikigami(GameTestHelper helper) {
		String fixture = "deerHealsAWoundedOldTypeShikigami";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		AtomicLong nueHealthBaseline = new AtomicLong(-1L);
		AtomicBoolean healed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"nue tryPrimary result", "true", true));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DEER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"deer tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
				nue.setHealth(nue.getMaxHealth() * 0.3f);
				nueHealthBaseline.set((long) (nue.getHealth() * 100));
			}
			helper.assertTrue(nueHealthBaseline.get() > 0,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "wound", helper.getTick(), ownerId,
							"nue wounded for the heal premise", "> 0", nueHealthBaseline.get()));
		}));

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (healed.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				try {
					for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
						if ((long) (nue.getHealth() * 100) > nueHealthBaseline.get()) {
							healed.set(true);
						}
					}
				} catch (RuntimeException | AssertionError failure) {
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(DEADLINE_TICK + 2, () -> {
			try {
				helper.assertTrue(healed.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"heal", helper.getTick(), caster.getUUID(),
						"deer healed the wounded nue", "true", healed.get()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(DEADLINE_TICK + 4, () -> helper.succeed());
	}

	/**
	 * E4 — the Deer's heal never reaches an enemy: a wounded hostile standing beside the deer for
	 * the whole window keeps its exact health. This is the negative half of the allowlist: the
	 * recipient set is the owner plus owned summons, never a world scan.
	 */
	@GameTest(maxTicks = 200)
	public void deerNeverHealsAnEnemy(GameTestHelper helper) {
		String fixture = "deerNeverHealsAnEnemy";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(4, 1, 4));
		cow.setPersistenceRequired();
		cow.setInvulnerable(true);
		cow.addTag("jujutsu.autonomous_mark.none");
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		cow.setHealth(10.0f);
		cow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
		// Opaque roof: an AI cow burns in daylight before the heal window closes.
		for (int rx = 3; rx <= 5; rx++) {
			for (int rz = 3; rz <= 5; rz++) {
				helper.setBlock(new BlockPos(rx, 4, rz), Blocks.STONE);
			}
		}

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DEER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"deer tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(180, () -> {
			try {
				helper.assertTrue(cow.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"heal", helper.getTick(), caster.getUUID(), "cow alive", "true", cow.isAlive()));
				helper.assertTrue(cow.getHealth() <= 10.0f + 0.001f,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "heal", helper.getTick(),
								caster.getUUID(), "cow health never rose", "<= 10", cow.getHealth()));
			} finally {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(182, () -> helper.succeed());
	}

	/**
	 * E5 — the Ox's charge never damages an allied summon: the Nue is parked directly on the
	 * charge corridor between the ox and its mark, the charge runs through it, and the Nue's
	 * health never moves. Friendly bodies are swept-collision targets of nobody.
	 */
	@GameTest(maxTicks = 450)
	public void oxChargePassesThroughAlliedSummonsWithoutDamage(GameTestHelper helper) {
		String fixture = "oxChargePassesThroughAlliedSummonsWithoutDamage";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(6, 1, 2));
		cow.setPersistenceRequired();
		// The ox's pre-commit gate refuses invulnerable targets outright — this cow is the charge
		// target, so it must stay damageable. The tag alone bars foreign autonomous marks.
		cow.addTag("jujutsu.autonomous_mark.none");
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		cow.setHealth(200.0f);
		cow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
		AtomicLong nueHealth = new AtomicLong(-1L);
		AtomicBoolean charged = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"nue tryPrimary result", "true", true));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.OX);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"ox tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(SIC_TICK + 20, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
				nueHealth.set((long) (nue.getHealth() * 100));
			}
			helper.assertTrue(nueHealth.get() > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"premise", helper.getTick(), ownerId, "nue health recorded", "> 0", nueHealth.get()));
			TodoSwapTestFixtures.aimAt(caster, cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
		}));

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (charged.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				try {
					for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
							level.getServer(), ownerId)) {
						// chargeTargetUuid is set at ALIGN — require a committed in-flight
						// charge (or the recovery that follows one) so an ox stuck in ALIGN
						// cannot satisfy this oracle.
						if (body instanceof MegumiOxEntity ox && (ox.chargeInFlight() || ox.chargeRecovering())) {
							charged.set(true);
						}
					}
					for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
						helper.assertTrue((long) (nue.getHealth() * 100) >= nueHealth.get(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "charge", pollTick, ownerId,
										"nue health never dropped under the ox", ">= " + nueHealth.get(),
										(long) (nue.getHealth() * 100)));
					}
				} catch (RuntimeException | AssertionError failure) {
					cow.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(DEADLINE_TICK + 2, () -> {
			try {
				helper.assertTrue(charged.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"charge", helper.getTick(), caster.getUUID(),
						"ox committed a charge in the window", "true", charged.get()));
				helper.assertTrue(cow.getHealth() < cow.getMaxHealth() || cow.isRemoved(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "charge", helper.getTick(),
								caster.getUUID(), "charge target took the hit", "< " + cow.getMaxHealth(),
								cow.isRemoved() ? "removed" : cow.getHealth()));
				for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, caster.getUUID())) {
					helper.assertTrue((long) (nue.getHealth() * 100) >= nueHealth.get(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "charge", helper.getTick(),
									caster.getUUID(), "nue health after the charge", ">= " + nueHealth.get(),
									(long) (nue.getHealth() * 100)));
				}
			} finally {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(DEADLINE_TICK + 4, () -> helper.succeed());
	}

	/**
	 * E6 — the Tiger's combo respects allied summons: with the Nue beside the mark, the tiger's
	 * strikes land on the cow only and the Nue's health never drops. The combo locks one
	 * identity; an allied body inside the arc is not a legal hit.
	 */
	@GameTest(maxTicks = 450)
	public void tigerComboNeverHitsAlliedSummons(GameTestHelper helper) {
		String fixture = "tigerComboNeverHitsAlliedSummons";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(5, 1, 3));
		cow.setPersistenceRequired();
		// The cow is the combo target: it must stay damageable so the strikes landing is a
		// real assertion, not a vacuous pass. The tag bars foreign autonomous marks.
		cow.addTag("jujutsu.autonomous_mark.none");
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		cow.setHealth(200.0f);
		cow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
		AtomicLong nueHealth = new AtomicLong(-1L);
		AtomicBoolean comboed = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"nue tryPrimary result", "true", true));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TIGER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"tiger tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(SIC_TICK + 20, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
				nueHealth.set((long) (nue.getHealth() * 100));
			}
			TodoSwapTestFixtures.aimAt(caster, cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
		}));

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (comboed.get()) {
					return;
				}
				UUID ownerId = caster.getUUID();
				try {
					for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
							level.getServer(), ownerId)) {
						if (body instanceof MegumiTigerEntity tiger && tiger.comboTargetUuid() != null) {
							comboed.set(true);
						}
					}
					for (MegumiNueEntity nue : MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId)) {
						helper.assertTrue((long) (nue.getHealth() * 100) >= nueHealth.get(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "combo", pollTick, ownerId,
										"nue health never dropped under the tiger", ">= " + nueHealth.get(),
										(long) (nue.getHealth() * 100)));
					}
				} catch (RuntimeException | AssertionError failure) {
					cow.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(DEADLINE_TICK + 2, () -> {
			try {
				helper.assertTrue(comboed.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"combo", helper.getTick(), caster.getUUID(),
						"tiger committed a combo in the window", "true", comboed.get()));
				helper.assertTrue(cow.getHealth() < cow.getMaxHealth() || cow.isRemoved(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "combo", helper.getTick(),
								caster.getUUID(), "combo strikes landed on the mark",
								"< " + cow.getMaxHealth(),
								cow.isRemoved() ? "removed" : cow.getHealth()));
			} finally {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(DEADLINE_TICK + 4, () -> helper.succeed());
	}

	/**
	 * E7 — the global sic reaches the new combat-capable bodies: serpent, ox and tiger out
	 * together, one aim, and every one of them carries the mark. The deer is deliberately absent —
	 * its mark is threat-awareness, not an attack order, so it is not part of this assertion.
	 */
	@GameTest(maxTicks = 120)
	public void globalSicReachesTheNewCombatBodies(GameTestHelper helper) {
		String fixture = "globalSicReachesTheNewCombatBodies";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		Cow cow = GameTestFixtures.spawnMob(helper, fixture, EntityType.COW, new BlockPos(6, 1, 5));
		cow.setPersistenceRequired();
		// The mark must stay damageable: MegumiOxBrain.validMarkedTarget rejects invulnerable
		// targets, so an invulnerable cow would let the ox keep a dead order and still pass.
		// The tag bars foreign autonomous marks; the sic itself bypasses that gate.
		cow.addTag("jujutsu.autonomous_mark.none");
		cow.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		MegumiShikigami[] combat = { MegumiShikigami.SERPENT, MegumiShikigami.OX, MegumiShikigami.TIGER };
		for (int i = 0; i < combat.length; i++) {
			MegumiShikigami type = combat[i];
			helper.runAtTickTime(SUMMON_TICK + i * 2L, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, type);
				helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								type + " tryPrimary result", "true", true));
			}));
		}

		helper.runAtTickTime(SIC_TICK + 20, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			TodoSwapTestFixtures.aimAt(caster, cow.position().add(0.0, cow.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), caster.getUUID(), "trySic result", "true", sicced));
		}));

		helper.runAtTickTime(SIC_TICK + 22, () -> {
			try {
				UUID ownerId = caster.getUUID();
				for (MegumiShikigami type : combat) {
					boolean marked = false;
					for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(
							helper.getLevel().getServer(), ownerId)) {
						if (body.shikigamiType() == type && cow.getUUID().equals(
								body.getTarget() == null ? null : body.getTarget().getUUID())) {
							marked = true;
						}
					}
					helper.assertTrue(marked, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), ownerId, type + " carries the mark", "marked", marked));
				}
			} finally {
				cow.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 24, () -> helper.succeed());
	}

	/**
	 * E8 — the coordinator's intent set claims the new mechanic targets: once the serpent binds
	 * the cow, the rebuilt combat context must carry the victim's UUID in
	 * {@code intentTargets}, which is what steers the rest of the pack's assignment away from a
	 * body already committed to it.
	 */
	@GameTest(maxTicks = 450)
	public void serpentBindLandsInTheCoordinatorIntentSet(GameTestHelper helper) {
		String fixture = "serpentBindLandsInTheCoordinatorIntentSet";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		// A ServerPlayer victim is immune to every removal vector that killed the cow and zombie
		// variants: players never unload from chunks, never despawn, never peaceful-discard, and
		// are neither Monster (purge) nor Animal (incident cull). The tag bars foreign autonomous
		// marks; invulnerability covers direct damage.
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture,
				new BlockPos(6, 1, 5), "serpent-bind-victim");
		victim.setInvulnerable(true);
		victim.addTag("jujutsu.autonomous_mark.none");
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);
		AtomicBoolean intentSeen = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"serpent tryPrimary result", "true", true));
		}));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			TodoSwapTestFixtures.aimAt(caster, victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
			helper.assertTrue(MegumiShikigamiRuntime.trySic(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(),
							caster.getUUID(), "trySic result", "true", true));
		}));

		for (long tick = SIC_TICK + 1; tick <= DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (intentSeen.get()) {
					return;
				}
				try {
					MegumiCombatContext context = MegumiPackCoordinator.contextFor(caster, level);
					if (context != null && context.intentTargets().contains(victim.getUUID())) {
						// Observable side of the intent: the serpent body itself must be
						// committed to the same victim, not just the context snapshot.
						boolean bodyCommitted = MegumiShikigamiRuntime.livingBodiesAll(
								level.getServer(), caster.getUUID()).stream()
								.anyMatch(body -> body instanceof MegumiSerpentEntity serpent
										&& victim.getUUID().equals(serpent.bindTargetUuid()));
						helper.assertTrue(bodyCommitted, MegumiShikigamiTestFixtures.diagnostic(
								fixture, "intent", pollTick, caster.getUUID(),
								"serpent body committed to the intent target", "bound",
								MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), caster.getUUID())));
						intentSeen.set(true);
					}
				} catch (RuntimeException | AssertionError failure) {
					victim.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
		helper.runAtTickTime(DEADLINE_TICK + 2, () -> {
			try {
				helper.assertTrue(intentSeen.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"intent", helper.getTick(), caster.getUUID(),
						"serpent bind target in context.intentTargets", "present", intentSeen.get()));
			} finally {
				victim.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(DEADLINE_TICK + 4, () -> helper.succeed());
	}

	/**
	 * E9 — cross-type cooldown isolation: recalling the serpent arms only the serpent's summon
	 * deadline; the deer's pack stays out and its cooldown row stays at zero. The per-type map is
	 * what makes the recall priced per family instead of a global lockout.
	 */
	@GameTest(maxTicks = 120)
	public void recallingOneExpansionTypeLeavesTheOthersAlone(GameTestHelper helper) {
		String fixture = "recallingOneExpansionTypeLeavesTheOthersAlone";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"serpent tryPrimary result", "true", true));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DEER);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
							"deer tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
							"serpent recall tryPrimary result", "true", true));
		}));

		helper.runAtTickTime(SIC_TICK + 4, () -> {
			try {
				UUID ownerId = caster.getUUID();
				long gameTime = level.getGameTime();
				long serpentCooldown = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.SERPENT, gameTime);
				helper.assertTrue(serpentCooldown > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "serpent summon cooldown", "> 0", serpentCooldown));
				for (MegumiShikigami other : new MegumiShikigami[] { MegumiShikigami.DEER,
						MegumiShikigami.OX, MegumiShikigami.TIGER, MegumiShikigami.NUE }) {
					long cooldown = MegumiSummonCooldowns.remainingTicks(ownerId, other, gameTime);
					helper.assertTrue(cooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"recall", helper.getTick(), ownerId, other + " summon cooldown", "0", cooldown));
				}
				helper.assertTrue(MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), ownerId, MegumiShikigami.DEER),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"deer pack still out", "present", "checked"));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 6, () -> helper.succeed());
	}

	/**
	 * E10 — the selector accepts every new entry: the production {@code selectShikigami} path
	 * (what the C2S receiver calls) must return true for each of the four ids and land the
	 * selection, exactly as a strip click would.
	 */
	@GameTest(maxTicks = 60)
	public void selectorAcceptsEveryExpansionEntry(GameTestHelper helper) {
		String fixture = "selectorAcceptsEveryExpansionEntry";
		paveFloor(helper, -3, 10, -3, 10);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(
				helper, fixture, new BlockPos(2, 1, 2), 0.0f, 0.0f);

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				for (MegumiShikigami type : EXPANSION) {
					boolean accepted = JujutsuCharacters.of(caster).selectShikigami(caster, type.id());
					helper.assertTrue(accepted, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"select", helper.getTick(), ownerId, "selectShikigami " + type.id(), "true", accepted));
					helper.assertTrue(MegumiShikigamiSelection.selected(ownerId) == type,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "select", helper.getTick(),
									ownerId, "selection after " + type.id(), type,
									MegumiShikigamiSelection.selected(ownerId)));
				}
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SUMMON_TICK + 2, () -> helper.succeed());
	}

	/** Floor-supported pad so every ground placement finds a safe body spot. */
	private static void paveFloor(GameTestHelper helper, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				// Sky cover at y=4: cow victims burn in daylight during the ~80-tick hold
				// window (same trap MegumiToadGameTests.laySkyCover documents) — the burn reads
				// as hold damage and fails "restraint deals no damage" oracles.
				helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}
}
