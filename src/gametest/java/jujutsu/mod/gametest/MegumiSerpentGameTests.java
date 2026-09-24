package jujutsu.mod.gametest;

import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.character.megumi.MegumiSerpentEntity.SerpentState;
import jujutsu.mod.character.megumi.MegumiSerpentPolicy;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.combat.SafeBodyPlacement;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Great Serpent server scenarios: the summon/recall skeleton plus the ambush-release signature
 * matrix (plan §C + §I). The serpent never swings — its whole attack is the bind — so every
 * hostile row ends in {@link HoldSupport#isHeld} and {@code bindVictimUuid}, and every release
 * row anchors on the VICTIM side of the release (unpaired, unmarked, free to move): a release
 * read on the serpent alone would pass while the pin is permanent.
 *
 * <p>Traps respected: owner-UUID body scans only (the world offset is random per run), literal
 * ticks/cooldown pins wherever a contract number is asserted (the red-proof mutates the profile
 * row), poll-until-deadline oracles instead of exact-tick equality, zombies either AI +
 * Slowness-100 under a sky cover or NoAI-frozen per the victim idiom table, and
 * {@code cleanupCaster} on every terminal path. Bind-band literals assert the plan's ~[110,130]
 * window on a silverfish victim (its real measurements land 112) and the zombie row pins its own
 * literal window — a mutated bind constant turns the row red, not silently follows.
 */
public final class MegumiSerpentGameTests {

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int SIC_TICK = 24;
	/** PREPARE 10 + SUBMERGED dwell 20 + EMERGE 8 + slack: the bind must land well inside this. */
	private static final long BIND_DEADLINE_TICK = SIC_TICK + 80;
	private static final long POLL_LAG_TICKS = 2;
	/** Zombie bind length is a literal pin: 120 − 20·1.0 − 0.702·8.0 ≈ 94 ticks. */
	private static final int EXPECTED_ZOMBIE_BIND_TICKS = 94;
	/** Silverfish bind length inside the plan's ~[110,130] band: 120 − 8 − 0.048·8 ≈ 112. */
	private static final int EXPECTED_SILVERFISH_BIND_TICKS = 112;
	private static final int BIND_BAND_MIN_TICKS = 110;
	private static final int BIND_BAND_MAX_TICKS = 130;
	/** Hard literal bounds = the pinned MIN/MAX clamps; a clamp mutation must fail them. */
	private static final int BIND_HARD_MIN_TICKS = 80;
	private static final int BIND_HARD_MAX_TICKS = 160;
	/** Victim displacement per bound tick stays under the hold's 0.5 pull cap + epsilon. */
	private static final double HELD_MAX_STEP = 0.6;
	/** Once converged, a bound victim sits at the mouth anchor: ~1.0 offset + one pull step slack. */
	private static final double BOUND_ANCHOR_SLACK = 2.0;
	private static final double BREAK_TELEPORT_BLOCKS = 20.0;

	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 200;

	// ---------------------------------------------------------------------------------------------
	// S1 — summon skeleton
	// ---------------------------------------------------------------------------------------------

	/** S1 — selecting SERPENT and pressing the technique key summons exactly one live body. */
	@GameTest(maxTicks = 60)
	public void serpentSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "serpentSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				helper.assertTrue(MegumiShikigami.SERPENT.id().equals(view.get().type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.SERPENT.id(), view.get().type()));

				List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiSerpentEntity.class);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned serpent bodies", "1", bodies.size()));
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"PRIMARY cooldown (summon is free)", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/** S2 — a manual recall removes the body and costs exactly the serpent recall cooldown. */
	@GameTest(maxTicks = 80)
	public void serpentRecallRemovesBodyAndChargesRecallCooldown(GameTestHelper helper) {
		String fixture = "serpentRecallRemovesBodyAndChargesRecallCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "second tryPrimary result", "true", recalled));

				// Same-tick read: the cooldown was just armed, so the remaining time is exact.
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.SERPENT, level.getGameTime());
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"serpent recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));
				// Issue #107: the recall price is per type, so the shared PRIMARY slot stays free.
				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY slot (the per-type map owns the deadline)", "0", primary));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	// ---------------------------------------------------------------------------------------------
	// Signature rows — the ambush/bind island
	// ---------------------------------------------------------------------------------------------

	/**
	 * A sic'd zombie is sunk under, emerged behind, and bound: GRIPPED marker on the victim, the
	 * registry pair live, {@code bindVictimUuid} naming it, and its per-tick displacement under
	 * the pull cap for the whole window ("cannot move" is observed, not assumed). The recorded
	 * bind length must sit inside a literal band around the zombie's 94-tick expectation — a
	 * hardcoded hold or a policy the brain ignores falls outside it.
	 */
	@GameTest(maxTicks = 260)
	public void serpentAmbushBindsTheMarkedTarget(GameTestHelper helper) {
		String fixture = "serpentAmbushBindsTheMarkedTarget";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean done = new AtomicBoolean();
		AtomicLong bindTick = new AtomicLong(-1L);
		AtomicReference<Vec3> previousVictimPosition = new AtomicReference<>(zombie.position());

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, zombie, SIC_TICK);

		for (long tick = SIC_TICK + 1; tick <= BIND_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "bind", pollTick, caster);
					Vec3 current = zombie.position();
					double step = current.distanceTo(previousVictimPosition.getAndSet(current));

					if (bindTick.get() < 0) {
						if (!body.isBinding() || !zombie.getUUID().equals(body.bindVictimUuid())) {
							if (pollTick == BIND_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"bind", pollTick, caster.getUUID(),
										"serpent bound the sic'd zombie", "binding by tick " + BIND_DEADLINE_TICK,
										"state=" + body.state() + " ambush=" + body.ambushTargetUuid()));
							}
							return;
						}
						bindTick.set(pollTick);
						helper.assertTrue(body.state() == SerpentState.BIND,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
										caster.getUUID(), "state machine at BIND", "BIND", body.state()));
						long recordedBind = body.bindEndGameTime() - level.getGameTime();
						helper.assertTrue(
								recordedBind <= EXPECTED_ZOMBIE_BIND_TICKS
										&& recordedBind >= EXPECTED_ZOMBIE_BIND_TICKS - POLL_LAG_TICKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
										caster.getUUID(), "recorded bind ticks (literal zombie band)",
										EXPECTED_ZOMBIE_BIND_TICKS + " down to "
												+ (EXPECTED_ZOMBIE_BIND_TICKS - POLL_LAG_TICKS),
										recordedBind));
					}

					helper.assertTrue(HoldSupport.isHeld(zombie),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
									caster.getUUID(), "registry pair live while bound", "held",
									"not held"));
					helper.assertTrue(zombie.hasEffect(JujutsuEffects.GRIPPED),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
									caster.getUUID(), "GRIPPED marker on the victim", "present",
									"absent"));
					helper.assertTrue(step <= HELD_MAX_STEP,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
									caster.getUUID(), "victim displacement per tick while bound",
									"<= " + HELD_MAX_STEP, step));

					if (pollTick - bindTick.get() >= 30) {
						Vec3 anchor = MegumiSerpentPolicy.mouthAnchor(body.position(), body.getLookAngle());
						double drift = Math.hypot(zombie.getX() - anchor.x, zombie.getZ() - anchor.z);
						helper.assertTrue(drift <= BOUND_ANCHOR_SLACK,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bind", pollTick,
										caster.getUUID(), "victim converged at the mouth anchor",
										"<= " + BOUND_ANCHOR_SLACK, drift));
						done.set(true);
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * Recall mid-bind: the hold must not outlive its body. The anchors are victim-side — the
	 * registry pair drops, GRIPPED clears, and the victim demonstrably moves under its own
	 * velocity afterwards (a pushed zombie that travels is the proof the pin is gone).
	 */
	@GameTest(maxTicks = 260)
	public void serpentRecallReleasesTheBoundVictim(GameTestHelper helper) {
		String fixture = "serpentRecallReleasesTheBoundVictim";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean recalled = new AtomicBoolean();
		AtomicBoolean pushed = new AtomicBoolean();
		AtomicReference<Vec3> releasePosition = new AtomicReference<>();

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, zombie, SIC_TICK);

		long deadline = BIND_DEADLINE_TICK + 40;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "release", pollTick, caster);
					if (!recalled.get()) {
						if (!body.isBinding() || !zombie.getUUID().equals(body.bindVictimUuid())) {
							if (pollTick == BIND_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"release", pollTick, caster.getUUID(),
										"zombie bound before the recall", "bound by " + BIND_DEADLINE_TICK,
										"state=" + body.state()));
							}
							return;
						}
						recalled.set(true);
						boolean re = MegumiShikigamiRuntime.tryPrimary(caster, false);
						helper.assertTrue(re, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"release", pollTick, caster.getUUID(),
								"tryPrimary recall while binding", "true", re));
						return;
					}
					// The release is not deferred to the end of the recall sink: the pair and the
					// marker are gone as soon as the body starts to sink away.
					if (HoldSupport.isHeld(zombie)) {
						return;
					}
					if (!pushed.get()) {
						releasePosition.set(zombie.position());
						helper.assertTrue(!zombie.hasEffect(JujutsuEffects.GRIPPED),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "release", pollTick,
										caster.getUUID(), "GRIPPED marker cleared on recall", "absent",
										"present"));
						helper.assertTrue(body.bindVictimUuid() == null,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "release", pollTick,
										caster.getUUID(), "bind fields cleared", "null",
										String.valueOf(body.bindVictimUuid())));
						// The pin applied zero velocity every bound tick; a pushed zombie that
						// actually travels is the observable "can move" anchor.
						zombie.setDeltaMovement(new Vec3(0.6, 0.0, 0.0));
						zombie.hurtMarked = true;
						pushed.set(true);
						return;
					}
					double travelled = zombie.position().distanceTo(releasePosition.get());
					helper.assertTrue(travelled > 0.3,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "release", pollTick,
									caster.getUUID(), "released victim moves under its own velocity",
									"> 0.3 blocks travelled", travelled));
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * The bind must die with its victim — a victim that dies inside the coil unbinds quietly
	 * (no toss for the dead) and frees the registry pair, so the same slot can bind again.
	 */
	@GameTest(maxTicks = 260)
	public void serpentVictimDeathReleasesTheBind(GameTestHelper helper) {
		String fixture = "serpentVictimDeathReleasesTheBind";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean killed = new AtomicBoolean();

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, zombie, SIC_TICK);

		long deadline = BIND_DEADLINE_TICK + 30;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "death", pollTick, caster);
					if (!killed.get()) {
						if (!body.isBinding() || !zombie.getUUID().equals(body.bindVictimUuid())) {
							if (pollTick == BIND_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"death", pollTick, caster.getUUID(),
										"zombie bound before the kill", "bound by " + BIND_DEADLINE_TICK,
										"state=" + body.state()));
							}
							return;
						}
							killed.set(true);
						zombie.kill(level);
						return;
					}
					if (HeldVictimRegistry.isHeld(zombie)) {
						return;
					}
					helper.assertTrue(body.bindVictimUuid() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "death", pollTick,
									caster.getUUID(), "bind fields cleared after the victim died",
									"null", String.valueOf(body.bindVictimUuid())));
					helper.assertTrue(body.state() == SerpentState.RECOVERY
									|| body.state() == SerpentState.FOLLOW,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "death", pollTick,
									caster.getUUID(), "quiet release lands in recovery", "RECOVERY",
									body.state()));
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * The coil releases when its own timer says so: a silverfish victim (8 hp, small hitbox)
	 * is held for the literal ~[110,130] band the plan pins — the silverfish's real measurements
	 * land 112 ticks — then unbound with no kill, no recall and no leash break.
	 */
	@GameTest(maxTicks = 340)
	public void serpentBindExpiresOnTimer(GameTestHelper helper) {
		String fixture = "serpentBindExpiresOnTimer";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos fishFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(fishFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Silverfish fish = GameTestFixtures.spawnMob(helper, fixture, EntityType.SILVERFISH, fishFeet);
		fish.setPersistenceRequired();
		// Inert: an AI silverfish wanders out of the ambush reach inside the approach window.
		fish.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(fish);

		AtomicBoolean done = new AtomicBoolean();
		AtomicLong bindTick = new AtomicLong(-1L);
		AtomicLong bindEnd = new AtomicLong(-1L);

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, fish, SIC_TICK);

		long deadline = BIND_DEADLINE_TICK + BIND_BAND_MAX_TICKS + 40;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "expiry", pollTick, caster);
					if (bindTick.get() < 0) {
						if (!body.isBinding() || !fish.getUUID().equals(body.bindVictimUuid())) {
							if (pollTick == BIND_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"expiry", pollTick, caster.getUUID(),
										"silverfish bound", "bound by " + BIND_DEADLINE_TICK,
										"state=" + body.state()));
							}
							return;
						}
						bindTick.set(pollTick);
						bindEnd.set(body.bindEndGameTime());
						long recorded = bindEnd.get() - level.getGameTime();
						helper.assertTrue(recorded >= BIND_HARD_MIN_TICKS
										&& recorded <= BIND_HARD_MAX_TICKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "expiry", pollTick,
										caster.getUUID(), "recorded bind inside the literal clamp band",
										"[" + BIND_HARD_MIN_TICKS + "," + BIND_HARD_MAX_TICKS + "]",
										recorded));
						return;
					}
					if (HoldSupport.isHeld(fish)) {
						if (pollTick >= deadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"expiry", pollTick, caster.getUUID(),
									"bind released before the deadline", "released",
									"still held " + (pollTick - bindTick.get()) + " ticks"));
						}
						return;
					}
					long heldTicks = pollTick - bindTick.get();
					helper.assertTrue(heldTicks >= BIND_BAND_MIN_TICKS && heldTicks <= BIND_BAND_MAX_TICKS,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "expiry", pollTick,
									caster.getUUID(), "measured bind length (plan ~[110,130] band)",
									"[" + BIND_BAND_MIN_TICKS + "," + BIND_BAND_MAX_TICKS + "]",
									heldTicks));
					helper.assertTrue(body.state() != SerpentState.BIND,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "expiry", pollTick,
									caster.getUUID(), "serpent left the bind state", "not BIND",
									body.state()));
					done.set(true);
					fish.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					fish.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * A bound victim dragged past the break range snaps the coil. GRIPPED victims cannot
	 * self-move (the pin is exactly what proves this), so the break is driven by a teleport —
	 * teleports ignore arena walls and reliably beat {@code SERPENT_BIND_BREAK_RANGE}.
	 */
	@GameTest(maxTicks = 260)
	public void serpentBindBreaksBeyondRange(GameTestHelper helper) {
		String fixture = "serpentBindBreaksBeyondRange";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean teleported = new AtomicBoolean();

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, zombie, SIC_TICK);

		long deadline = BIND_DEADLINE_TICK + 40;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "break", pollTick, caster);
					if (!teleported.get()) {
						if (!body.isBinding() || !zombie.getUUID().equals(body.bindVictimUuid())) {
							if (pollTick == BIND_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"break", pollTick, caster.getUUID(),
										"zombie bound before the drag-out", "bound by " + BIND_DEADLINE_TICK,
										"state=" + body.state()));
							}
							return;
						}
						teleported.set(true);
						// +20 blocks beats the 16-block break range; the lift keeps the landing
						// survivable so the row exercises the live-exit path, not victim death.
						zombie.teleportTo(zombie.getX() + BREAK_TELEPORT_BLOCKS,
								zombie.getY() + 8.0, zombie.getZ());
						return;
					}
					if (HoldSupport.isHeld(zombie)) {
						if (pollTick >= deadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"break", pollTick, caster.getUUID(),
									"bind snapped once the victim was out of reach", "released",
									"still held at " + zombie.position().distanceTo(body.position())));
						}
						return;
					}
					helper.assertTrue(!zombie.hasEffect(JujutsuEffects.GRIPPED),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "break", pollTick,
									caster.getUUID(), "GRIPPED cleared on the range break", "absent",
									"present"));
					helper.assertTrue(body.bindVictimUuid() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "break", pollTick,
									caster.getUUID(), "bind fields cleared on the range break", "null",
									String.valueOf(body.bindVictimUuid())));
					helper.assertTrue(body.state() != SerpentState.BIND,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "break", pollTick,
									caster.getUUID(), "serpent left the bind state", "not BIND",
									body.state()));
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * The emerge point is re-validated while the coil waits: walling every rear-arc candidate
	 * once the body is SUBMERGED must abort the ambush into RECOVERY — no teleport into solid
	 * geometry, no bind. The anchors: the body is NEVER inside a block at any polled tick (a
	 * noclipped teleport would show up inside the wall it was denied), the victim stays unbound,
	 * and the abort lands in RECOVERY before the deadline. No position assertion: a recovered
	 * body walks back to its owner between retries, so where it stands is not the check.
	 */
	@GameTest(maxTicks = 320)
	public void serpentNeverNoclips(GameTestHelper helper) {
		String fixture = "serpentNeverNoclips";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);

		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean walled = new AtomicBoolean();
		AtomicBoolean sawRecovery = new AtomicBoolean();

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		sicOn(helper, fixture, caster, zombie, SIC_TICK);

		long deadline = SIC_TICK + 160;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "noclip", pollTick, caster);
					if (!walled.get()) {
						if (body.state() != SerpentState.SUBMERGED) {
							if (pollTick == deadline - 60) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"noclip", pollTick, caster.getUUID(),
										"serpent reached SUBMERGED", "SUBMERGED", body.state()));
							}
							return;
						}
						// Every requested emerge spot gets a solid 3×3×3 shell: whichever candidate
						// the placement scan picked is now inside a wall. setBlock is
						// structure-relative — the candidates are world coords, so each is
						// pulled back through the structure origin (the random-offset trap).
						BlockPos origin = helper.absolutePos(BlockPos.ZERO);
						for (Vec3 candidate : MegumiSerpentPolicy.emergeCandidates(
								zombie.position(), zombie.yBodyRot,
								MegumiShikigamiProfile.SERPENT_EMERGE_REAR_OFFSET)) {
							wallOff(helper, new BlockPos(
									BlockPos.containing(candidate).subtract(origin)));
						}
						walled.set(true);
						return;
					}
					// The noclip oracle is polled, not terminal: a teleport into the denied wall
					// is caught the tick it happens, wherever the state machine is.
					helper.assertTrue(level.noBlockCollision(body, body.getBoundingBox()),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "noclip", pollTick,
									caster.getUUID(), "serpent never inside a wall block", "clear",
									"inside solid in state " + body.state()));
					helper.assertTrue(body.bindVictimUuid() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "noclip", pollTick,
									caster.getUUID(), "nobody was bound through a wall", "null",
									String.valueOf(body.bindVictimUuid())));
					helper.assertTrue(!HoldSupport.isHeld(zombie),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "noclip", pollTick,
									caster.getUUID(), "victim unbound", "not held",
									HoldSupport.isHeld(zombie)));
					if (body.state() == SerpentState.RECOVERY) {
						sawRecovery.set(true);
					}
					if (pollTick == deadline) {
						helper.assertTrue(sawRecovery.get(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "noclip", pollTick,
										caster.getUUID(), "ambush aborted to RECOVERY after the wall went up",
										"RECOVERY", "never recovered; state=" + body.state()));
						done.set(true);
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * The pile-on invite (§I): while the serpent holds a victim the coordinator's held-bonus
	 * must move a dog onto the held target — an AUTONOMOUS-kind mark the pack placed on its own,
	 * which is what "autonomy follows the coordinator flag" reads as for this type.
	 */
	@GameTest(maxTicks = 420, skyAccess = true)
	public void serpentHeldVictimFeedsCoordinator(GameTestHelper helper) {
		String fixture = "serpentHeldVictimFeedsCoordinator";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicBoolean done = new AtomicBoolean();
		AtomicReference<Zombie> farRef = new AtomicReference<>();
		AtomicReference<Zombie> nearRef = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			boolean ok = MegumiSummonRuntime.tryToggle(owner, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "dogs summoned", "true", ok));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "serpent summoned", "true", ok));
		}));
		helper.runAtTickTime(30, () -> {
			// Tough victims: the dogs chew on marks, not on health pools — both must outlive the window.
			farRef.set(spawnToughZombie(helper, fixture, new BlockPos(8, 1, 8)));
			nearRef.set(spawnToughZombie(helper, fixture, new BlockPos(5, 1, 4)));
		});

		long deadline = 30 + 200;
		for (long tick = 31; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Zombie near = nearRef.get();
				Zombie far = farRef.get();
				List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
				if (near == null || far == null || dogs.isEmpty()) {
					return;
				}
				boolean held = near.hasEffect(JujutsuEffects.GRIPPED) && HoldSupport.isHeld(near);
				boolean dogOnNear = dogs.stream().anyMatch(dog -> dog.getTarget() == near);
				if (held && dogOnNear) {
					done.set(true);
					near.discard();
					far.discard();
					cleanupOwner(helper, owner);
					helper.succeed();
					return;
				}
				if (pollTick == deadline) {
					done.set(true);
					near.discard();
					far.discard();
					cleanupOwner(helper, owner);
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"intent", pollTick, owner.getUUID(),
							"a dog piles onto the serpent's held victim", "held+targeted",
							"held=" + held + " dogTargets="
									+ dogs.stream().map(d -> String.valueOf(d.getTarget())).toList()));
				}
			});
		}
	}

	/**
	 * The hold gate refuses a second holder: a victim already pinned by someone else is never
	 * a legal ambush, however it is marked. The zombie stays external-held through the whole
	 * window and the serpent's bind field never names it.
	 */
	@GameTest(maxTicks = 160)
	public void alreadyHeldVictimRefusesSecondHolder(GameTestHelper helper) {
		String fixture = "alreadyHeldVictimRefusesSecondHolder";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);

		AtomicBoolean done = new AtomicBoolean();
		summonSerpent(helper, fixture, caster, SUMMON_TICK);

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				// Someone else got here first: one external applyHold registers the pair and the
				// victim is simply not the serpent's to take.
				HoldSupport.applyHold(caster, zombie, zombie.position(),
						HoldSupport.CollisionPolicy.SERPENT, 10);
				helper.assertTrue(HeldVictimRegistry.isHeld(zombie),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "held", helper.getTick(),
								caster.getUUID(), "zombie already held by another holder", "held",
								"not held"));
				TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				MegumiShikigamiRuntime.trySic(caster, false);
			} catch (RuntimeException | AssertionError failure) {
				HeldVictimRegistry.release(zombie.getUUID());
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		long deadline = SIC_TICK + 90;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "second", pollTick, caster);
					helper.assertTrue(body.bindVictimUuid() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "second", pollTick,
									caster.getUUID(), "serpent never binds an already-held victim",
									"null", String.valueOf(body.bindVictimUuid())));
					helper.assertTrue(body.state() != SerpentState.BIND,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "second", pollTick,
									caster.getUUID(), "state never reaches BIND", "not BIND",
									body.state()));
					if (pollTick == deadline) {
						done.set(true);
						HeldVictimRegistry.release(zombie.getUUID());
						zombie.discard();
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					HeldVictimRegistry.release(zombie.getUUID());
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * Friendly fire matrix: an allied hostile (same scoreboard team), the owner's own dogs and
	 * the owner himself stand inside the ambush reach for a full window — however they get
	 * marked (sic into the ally is itself refused by eligibility, which the serpent must not
	 * bypass) the bind never lands on any of them.
	 *
	 * <p>The teamed zombie is asserted <em>conditionally</em>: every mock player in the whole
	 * gametest suite shares the profile name {@code "test-mock-player"}, and scoreboard teams
	 * key on the name — a neighbouring test teaming its own caster rips the shared name out of
	 * this row's team, so the alliance can legitimately flicker off mid-run for reasons this
	 * test cannot control. Binding the zombie only fails while the alliance stayed continuously
	 * live; the owner (an identity check) and the sibling dogs (ownerUuid) are checked
	 * unconditionally — their protection never touches the scoreboard.
	 */
	@GameTest(maxTicks = 200)
	public void serpentNeverBindsAlliedBodiesOrOwner(GameTestHelper helper) {
		String fixture = "serpentNeverBindsAlliedBodiesOrOwner";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(5, 1, 4);
		layStoneFloor(helper);
		laySkyCover(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);
		net.minecraft.world.scores.PlayerTeam team = level.getScoreboard()
				.addPlayerTeam(fixture + "_team");
		level.getScoreboard().addPlayerToTeam(caster.getScoreboardName(), team);
		level.getScoreboard().addPlayerToTeam(zombie.getScoreboardName(), team);

		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean allianceBroken = new AtomicBoolean();
		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.DOGS);
			boolean ok = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), caster.getUUID(), "dogs summoned", "true", ok));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.SERPENT);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), caster.getUUID(), "serpent summoned", "true", ok));
		}));
		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
			MegumiShikigamiRuntime.trySic(caster, false);
		}));

		long deadline = SIC_TICK + 90;
		for (long tick = SIC_TICK + 1; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					MegumiSerpentEntity body = singleSerpent(helper, fixture, "ally", pollTick, caster);
					if (!caster.isAlliedTo(zombie)) {
						allianceBroken.set(true);
					}
					java.util.UUID bound = body.bindVictimUuid();
					String boundDesc = "null";
					if (bound != null) {
						net.minecraft.world.entity.Entity resolved = level.getEntity(bound);
						boundDesc = bound + " " + (resolved == null ? "removed"
								: resolved.getType().toShortString() + " @" + resolved.blockPosition()
										+ " dist=" + String.format("%.1f", resolved.distanceTo(body))
										+ " held=" + (resolved instanceof LivingEntity living
												&& HoldSupport.isHeld(living))
										+ " ally=" + caster.isAlliedTo(resolved));
					}
					// A hostile body pathed in from a neighbouring fixture is a legal bind
					// (unallied, unheld, in range) — the invariant only names the protected set.
					helper.assertTrue(!caster.getUUID().equals(bound),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
									caster.getUUID(), "serpent never binds the owner",
									"not the owner", boundDesc));
					List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, caster.getUUID());
					for (MegumiDivineDogEntity dog : dogs) {
						helper.assertTrue(!dog.getUUID().equals(bound) && !HoldSupport.isHeld(dog),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
										caster.getUUID(), "serpent never binds the owner's own bodies",
										"dogs free", "dog=" + dog.getUUID() + " bind=" + boundDesc));
					}
					boolean teamedBind = zombie.getUUID().equals(bound) && !allianceBroken.get();
					helper.assertTrue(!teamedBind,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
									caster.getUUID(), "serpent never binds the ally",
									"not the ally", boundDesc));
					helper.assertTrue(!HoldSupport.isHeld(caster),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
									caster.getUUID(), "the owner is never held",
									"free", "held"));
					helper.assertTrue(!(HoldSupport.isHeld(zombie) && !allianceBroken.get()),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
									caster.getUUID(), "the ally is never held",
									"free", "held"));
					if (pollTick == deadline) {
						done.set(true);
						zombie.discard();
						for (MegumiDivineDogEntity dog : dogs) {
							dog.discard();
						}
						MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					for (MegumiDivineDogEntity dog : dogsOwnedBy(level, caster.getUUID())) {
						dog.discard();
					}
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * No order given: the coordinator's autonomous mark lands on the serpent (the pack's own
	 * pick, never the owner's aim) and the ambush that follows binds the marked target — the
	 * coordinator flag is what drives the pick, not a hardcoded nearest-enemy rule.
	 */
	@GameTest(maxTicks = 340, skyAccess = true)
	public void serpentAutonomousPickFollowsCoordinatorFlag(GameTestHelper helper) {
		String fixture = "serpentAutonomousPickFollowsCoordinatorFlag";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicBoolean done = new AtomicBoolean();
		AtomicReference<Zombie> zombieRef = new AtomicReference<>();

		summonSerpent(helper, fixture, owner, SUMMON_TICK);
		helper.runAtTickTime(30, () -> zombieRef.set(
				spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 4))));

		long markDeadline = 30 + 80;
		long bindDeadline = 30 + 240;
		AtomicBoolean marked = new AtomicBoolean();
		for (long tick = 31; tick <= bindDeadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Zombie zombie = zombieRef.get();
				if (zombie == null) {
					return;
				}
				try {
					List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
							level, owner.getUUID(), MegumiSerpentEntity.class);
					if (bodies.isEmpty()) {
						return;
					}
					MegumiSerpentEntity body = bodies.get(0);
					if (!marked.get()) {
						if (body.getTarget() != zombie) {
							if (pollTick == markDeadline) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"autonomy", pollTick, owner.getUUID(),
										"coordinator's autonomous mark lands on the serpent",
										"target=" + zombie.getUUID(),
										"target=" + body.getTarget()));
							}
							return;
						}
						marked.set(true);
						return;
					}
					if (!zombie.getUUID().equals(body.bindVictimUuid())) {
						if (pollTick == bindDeadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"autonomy", pollTick, owner.getUUID(),
									"ambush binds the coordinator-marked target", zombie.getUUID(),
									"state=" + body.state() + " bind=" + body.bindVictimUuid()));
						}
						return;
					}
					done.set(true);
					zombie.discard();
					cleanupOwner(helper, owner);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					if (zombie != null) {
						zombie.discard();
					}
					cleanupOwner(helper, owner);
					throw failure;
				}
			});
		}
	}

	/**
	 * The owner-hurt answer: a scripted hit lands on the owner and the retaliation pass marks the
	 * attacker — the serpent must engage THAT mark (ambush → bind), not pick its own target.
	 */
	@GameTest(maxTicks = 340, skyAccess = true)
	public void serpentRetaliationMarkEngages(GameTestHelper helper) {
		String fixture = "serpentRetaliationMarkEngages";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicBoolean done = new AtomicBoolean();
		AtomicReference<Zombie> attackerRef = new AtomicReference<>();
		AtomicBoolean marked = new AtomicBoolean();

		summonSerpent(helper, fixture, owner, SUMMON_TICK);
		helper.runAtTickTime(30, () -> {
			Zombie attacker = spawnFrozenZombie(helper, fixture, new BlockPos(5, 1, 4));
			attackerRef.set(attacker);
			owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
			helper.assertTrue(owner.getLastHurtByMob() == attacker,
					MegumiShikigamiTestFixtures.diagnostic(fixture, "retaliation", helper.getTick(),
							owner.getUUID(), "the hit is attributed", attacker.getUUID(),
							String.valueOf(owner.getLastHurtByMob())));
		});

		long markDeadline = 30 + 60;
		long bindDeadline = 30 + 240;
		for (long tick = 31; tick <= bindDeadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				Zombie attacker = attackerRef.get();
				if (attacker == null) {
					return;
				}
				try {
					List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
							level, owner.getUUID(), MegumiSerpentEntity.class);
					if (bodies.isEmpty()) {
						return;
					}
					MegumiSerpentEntity body = bodies.get(0);
					if (!marked.get()) {
						if (body.getTarget() != attacker) {
							if (pollTick == markDeadline) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"retaliation", pollTick, owner.getUUID(),
										"retaliation mark lands on the serpent", attacker.getUUID(),
										"target=" + body.getTarget()));
							}
							return;
						}
						marked.set(true);
						return;
					}
					if (!attacker.getUUID().equals(body.bindVictimUuid())) {
						if (pollTick == bindDeadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"retaliation", pollTick, owner.getUUID(),
									"ambush binds the retaliation-marked attacker", attacker.getUUID(),
									"state=" + body.state() + " bind=" + body.bindVictimUuid()));
						}
						return;
					}
					done.set(true);
					attacker.discard();
					cleanupOwner(helper, owner);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					attacker.discard();
					cleanupOwner(helper, owner);
					throw failure;
				}
			});
		}
	}

	/**
	 * The attack-side of friendly fire: with only pack bodies nearby (two Divine Dogs out and no
	 * hostile in reach) the serpent must never commit a bind against its own side — every dog
	 * stays unheld, unmarked and undamaged through a full watch window.
	 */
	@GameTest(maxTicks = 160, skyAccess = true)
	public void serpentNeverAttacksAlliedBodies(GameTestHelper helper) {
		String fixture = "serpentNeverAttacksAlliedBodies";
		layPad(helper);
		ServerPlayer owner = setupDamageableOwner(helper, fixture, new BlockPos(2, 1, 2));
		ServerLevel level = helper.getLevel();
		AtomicBoolean done = new AtomicBoolean();
		AtomicReference<List<MegumiDivineDogEntity>> dogsRef = new AtomicReference<>();
		AtomicReference<Float> healthBefore = new AtomicReference<>();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			boolean ok = MegumiSummonRuntime.tryToggle(owner, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "dogs summoned", "true", ok));
		}));
		helper.runAtTickTime(SUMMON_TICK + 2, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.SERPENT);
			boolean ok = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(ok, MegumiShikigamiTestFixtures.diagnostic(fixture, "summon",
					helper.getTick(), owner.getUUID(), "serpent summoned", "true", ok));
		}));
		helper.runAtTickTime(30, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			List<MegumiDivineDogEntity> dogs = dogsOwnedBy(level, owner.getUUID());
			helper.assertTrue(!dogs.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"ally", helper.getTick(), owner.getUUID(), "dogs present to refuse", "present",
					"absent"));
			dogsRef.set(dogs);
			healthBefore.set(dogs.stream().map(LivingEntity::getHealth).reduce(0f, Float::max));
		}));

		long deadline = 30 + 90;
		for (long tick = 31; tick <= deadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
							level, owner.getUUID(), MegumiSerpentEntity.class);
					if (bodies.isEmpty() || dogsRef.get() == null) {
						return;
					}
					MegumiSerpentEntity body = bodies.get(0);
					for (MegumiDivineDogEntity dog : dogsRef.get()) {
						helper.assertTrue(!HoldSupport.isHeld(dog),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
										owner.getUUID(), "no pack dog is ever bound", "not held",
										"held " + dog.getUUID()));
						helper.assertTrue(!dog.hasEffect(JujutsuEffects.GRIPPED),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
										owner.getUUID(), "no pack dog wears GRIPPED", "absent",
										"present " + dog.getUUID()));
						helper.assertTrue(dog.getHealth() >= healthBefore.get(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
										owner.getUUID(), "no pack dog took serpent damage",
										healthBefore.get(), dog.getHealth()));
					}
					helper.assertTrue(body.bindVictimUuid() == null,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "ally", pollTick,
									owner.getUUID(), "serpent never binds its own pack", "null",
									String.valueOf(body.bindVictimUuid())));
					if (pollTick == deadline) {
						done.set(true);
						for (MegumiDivineDogEntity dog : dogsRef.get()) {
							dog.discard();
						}
						cleanupOwner(helper, owner);
						helper.succeed();
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					if (dogsRef.get() != null) {
						for (MegumiDivineDogEntity dog : dogsRef.get()) {
							dog.discard();
						}
					}
					cleanupOwner(helper, owner);
					throw failure;
				}
			});
		}
	}

	/**
	 * §10's negative pin: a summon with no legal spawn spot refuses — no pack, no body, no
	 * cooldown and (by construction) no {@code serpent_summon_body} cue: that cue is emitted
	 * only inside {@code commitSummon}, which the empty staging list never reaches.
	 */
	@GameTest(maxTicks = 60)
	public void serpentNoRoomSummonRefusesAndKeepsCooldownFree(GameTestHelper helper) {
		String fixture = "serpentNoRoomSummonRefusesAndKeepsCooldownFree";
		BlockPos casterFeet = new BlockPos(2, 2, 2);
		// A solid shell around the caster: every ground candidate (±1.5 right, +1.8 forward,
		// centre) and all seven vertical offsets collide, so staging returns empty. The caster's
		// own 1×2 pocket stays air so the mock player survives the few ticks.
		for (int dx = 0; dx <= 4; dx++) {
			for (int dz = 1; dz <= 4; dz++) {
				for (int dy = 0; dy <= 6; dy++) {
					if (dx == 2 && dz == 2 && (dy == 1 || dy == 2)) {
						continue;
					}
					helper.setBlock(new BlockPos(dx, dy, dz), Blocks.STONE);
				}
			}
		}

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.SERPENT);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"noRoom", helper.getTick(), ownerId, "tryPrimary result", "false", summoned));
				helper.assertTrue(MegumiShikigamiRuntime.packView(level.getServer(), ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "noRoom", helper.getTick(),
								ownerId, "no pack registered (no summon_body cue is reachable)",
								"absent", "present"));
				List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiSerpentEntity.class);
				helper.assertTrue(bodies.isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "noRoom", helper.getTick(),
								ownerId, "no serpent body spawned", "0", bodies.size()));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.SERPENT, level.getGameTime());
				helper.assertTrue(remaining == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "noRoom", helper.getTick(),
								ownerId, "summon cooldown stays unarmed", "0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * Same negative pin through the cooldown gate: after a recall arms the serpent's cooldown a
	 * fresh press is rejected by {@code rejectRecharging} — before staging, before commitSummon,
	 * so the summon_body sign can never play for a refused cast.
	 */
	@GameTest(maxTicks = 80)
	public void serpentCooldownRejectsSecondSummonWithoutBodyCue(GameTestHelper helper) {
		String fixture = "serpentCooldownRejectsSecondSummonWithoutBodyCue";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		summonSerpent(helper, fixture, caster, SUMMON_TICK);
		helper.runAtTickTime(RECALL_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"cooldown", helper.getTick(), caster.getUUID(), "recall arms the cooldown",
					"true", recalled));
		}));
		helper.runAtTickTime(RECALL_TICK + 2, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(!summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cooldown", helper.getTick(), ownerId, "tryPrimary while recharging",
						"false", summoned));
				helper.assertTrue(MegumiShikigamiRuntime.packView(level.getServer(), ownerId).isEmpty(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(),
								ownerId, "no pack (no summon_body cue is reachable)", "absent",
								"present"));
				long remaining = MegumiSummonCooldowns.remainingTicks(
						ownerId, MegumiShikigami.SERPENT, level.getGameTime());
				helper.assertTrue(remaining > 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(),
								ownerId, "the armed cooldown is what refused it", ">0", remaining));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		// The recall despawn takes SERPENT_RECALL_TICKS: only after it has finished does an empty
		// arena prove the refused press spawned nothing — reading it earlier races the sinking body.
		helper.runAtTickTime(30, () -> {
			List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
					level, caster.getUUID(), MegumiSerpentEntity.class);
			helper.assertTrue(bodies.isEmpty(),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(),
							caster.getUUID(), "no serpent body spawned by the refused press", "0",
							bodies.size()));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------------
	// helpers
	// ---------------------------------------------------------------------------------------------

	private static void summonSerpent(GameTestHelper helper, String fixture, ServerPlayer caster, int tick) {
		helper.runAtTickTime(tick, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			MegumiShikigamiSelection.set(caster.getUUID(), MegumiShikigami.SERPENT);
			helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(caster, false),
					MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(),
							caster.getUUID(), "tryPrimary result", "true", "false"));
		}));
	}

	private static void sicOn(GameTestHelper helper, String fixture, ServerPlayer caster,
			LivingEntity victim, int tick) {
		helper.runAtTickTime(tick, () -> {
			try {
				TodoSwapTestFixtures.aimAt(caster,
						victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), caster.getUUID(), "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				victim.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});
	}

	private static MegumiSerpentEntity singleSerpent(GameTestHelper helper, String fixture,
			String stage, long pollTick, ServerPlayer caster) {
		List<MegumiSerpentEntity> bodies = MegumiShikigamiTestFixtures.ownedBy(
				helper.getLevel(), caster.getUUID(), MegumiSerpentEntity.class);
		helper.assertTrue(bodies.size() == 1,
				MegumiShikigamiTestFixtures.diagnostic(fixture, stage, pollTick, caster.getUUID(),
						"serpent body present", "1", bodies.size()));
		return bodies.get(0);
	}

	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		jujutsu.mod.character.CharacterSelectionManager.select(owner,
				jujutsu.mod.character.JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	private static void cleanupOwner(GameTestHelper helper, ServerPlayer owner) {
		MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
		CursedSpiritTestFixtures.cleanupVictim(helper, owner);
	}

	private static Zombie spawnFrozenZombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, feet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(true);
		CursedSpiritTestFixtures.freezeGround(zombie);
		return zombie;
	}

	private static Zombie spawnToughZombie(GameTestHelper helper, String fixture, BlockPos feet) {
		Zombie zombie = spawnFrozenZombie(helper, fixture, feet);
		zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		zombie.setHealth(400.0f);
		return zombie;
	}

	private static List<MegumiDivineDogEntity> dogsOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiDivineDogEntity> owned = new java.util.ArrayList<>();
		for (MegumiDivineDogEntity dog : level.getEntities(
				EntityTypeTest.forClass(MegumiDivineDogEntity.class), candidate -> true)) {
			if (ownerId.equals(dog.ownerUuid())) {
				owned.add(dog);
			}
		}
		return owned;
	}

	/**
	 * A solid 3×3×3 shell around the requested point. Three tall is required, not cosmetic:
	 * {@link SafeBodyPlacement.Policy}'s search walks a ±1 horizontal ring at +0/+1/+2 over the
	 * request — a shorter shell leaves the top ring open and the emerge escapes through it.
	 */
	private static void wallOff(GameTestHelper helper, BlockPos center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					helper.setBlock(center.offset(dx, dy, dz), Blocks.STONE);
				}
			}
		}
	}

	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 0; dx <= 7; dx++) {
			for (int dz = 0; dz <= 7; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}

	/** A stone ceiling over the arena so an AI zombie victim never burns mid-scenario. */
	private static void laySkyCover(GameTestHelper helper) {
		for (int dx = 0; dx <= 7; dx++) {
			for (int dz = 0; dz <= 7; dz++) {
				helper.setBlock(new BlockPos(dx, 4, dz), Blocks.STONE);
			}
		}
	}

	private static void layPad(GameTestHelper helper) {
		for (int dx = -1; dx <= 9; dx++) {
			for (int dz = -1; dz <= 9; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
				helper.setBlock(new BlockPos(dx, 6, dz), Blocks.STONE);
			}
		}
	}
}
