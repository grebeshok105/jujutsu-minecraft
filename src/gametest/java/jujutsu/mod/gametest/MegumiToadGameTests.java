package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime.PackView;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.character.megumi.MegumiToadPolicy;
import jujutsu.mod.combat.HoldSupport;

/**
 * Toad (Ten Shadows selection layer) server scenarios, block 1 — summon (S1), the grab on a mob:
 * hold, no damage, throw (S2), the held victim's damage refused (S5), a held player (S6), release
 * on recall (S7), the tag refusal (S8), recall (S3), death (S4), and the sic-free self-pick (R2)
 * — exercised through the production
 * runtime calls
 * {@code MegumiShikigamiRuntime.tryPrimary} / {@code trySic}, the same hop the vessel router
 * reaches for the PRIMARY / PRIMARY_SNEAK slots.
 *
 * <p><b>Pinned literals.</b> S3/S4 assert the literal 240/400 ticks rather than the profile
 * constants ON PURPOSE: the red-proof mutates the profile row (240-&gt;241, 400-&gt;401) and the
 * assert must follow the balance contract, not the constant. Every other number (recall window,
 * windup, hold window) references {@link MegumiShikigamiProfile} directly.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * bodies are found by owner-UUID scan ({@link #toadOwnedBy}), never by bounds. The
 * summon/recall/kill steps sit on different ticks — the runtime drops same-tick duplicate
 * technique presses. {@code hurtServer} on the body is gated on the ACTIVE phase, so S4 kills
 * only after the 16-tick materialization (with an explicit ACTIVE premise assert). The grab
 * victim is an AI zombie with zeroed speed (Slowness 100): full AI keeps physics, so the hold and
 * the throw move it while its own AI cannot. (A NoAI body is fully frozen — position never
 * changes — so a NoAI oracle would be vacuous.) The throw is asserted on the velocity field, not
 * on displacement: a speed-zeroed body barely converts one into the other. Player victims come
 * from {@link CursedSpiritTestFixtures#setupVictim} (placed SURVIVAL, hurtable) — the mock-player
 * shortcut would produce a CREATIVE body no hold can be asserted on. The zombie gets a stone roof
 * one block above it, so sky burn cannot fail the scenario. Static state (selection map, both pack
 * maps, both cooldown slots) is cleared in setup and on every success/failure path.
 */
public final class MegumiToadGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int SUMMON_TICK = 2;
	private static final int RECALL_TICK = 4;
	private static final int KILL_TICK = 25;
	private static final int SIC_TICK = 24;
	/**
	 * The windup is six ticks, but the commit also waits on the body's approach, its attack clock
	 * and the owner's line of sight — the player-victim scenarios were observed to commit anywhere
	 * inside ~50 ticks of the sic. 60 stays far below the shortest legal hold (60 ticks) plus the
	 * fixed half of both, so "holding by then" still cannot be met by simply waiting.
	 */
	private static final long GRAB_DEADLINE_TICK = SIC_TICK + 60;
	/** Windup + the longest legal hold (100) + margin: past this the scenario has already failed. */
	private static final long HOLD_DEADLINE_TICK = SIC_TICK + 140;
	/** The profile clamps a hold to 60..100 ticks; 55 keeps the floor assert non-vacuous. */
	private static final long MIN_HOLD_TICKS = 55;
	/**
	 * How far a pinned victim may drift from the anchor. The pin is re-applied every tick while the
	 * victim's own AI still moves it inside the same tick (Slowness drains that walk to ~0.02/tick),
	 * so this is a jitter bound, not a walk bound: an unheld body covers ~1.2 blocks over a hold.
	 */
	private static final double PIN_TOLERANCE_BLOCKS = 0.15;
	/** How far the holding body may drift: it must plant its feet, or the anchor walks away. */
	private static final double BODY_PIN_TOLERANCE_BLOCKS = 0.5;
	/** The throw has to read as a throw, not a nudge. */
	private static final double THROW_MIN_SPEED = 0.3;
	/** How late a poll callback can observe a commit the brain made inside an earlier tick. */
	private static final long POLL_LAG_TICKS = 2;
	/** The anchor sits {@code TOAD_GRIP_OFFSET} in front of the body; this is the extra slack. */
	private static final double ANCHOR_SLACK = 0.75;

	/**
	 * S3 pins this row: a manual recall costs exactly the Toad recall cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.TOAD_RECALL_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_RECALL_COOLDOWN_TICKS = 240;
	/**
	 * S4 pins this row: losing the body costs exactly the Toad death cooldown. Deliberately NOT
	 * {@code MegumiShikigamiProfile.TOAD_DEATH_COOLDOWN_TICKS} — the red-proof mutates that row.
	 */
	private static final int EXPECTED_DEATH_COOLDOWN_TICKS = 400;

	/**
	 * S1 — selecting TOAD and pressing the technique key summons exactly one live body with no
	 * cooldown: the pack view reads type "toad" with one anchored body, one
	 * {@link MegumiToadEntity} owned by the caster sits in the level, and PRIMARY stays at 0.
	 */
	@GameTest(maxTicks = 60)
	public void toadSummonCreatesSingleBodyWithoutCooldown(GameTestHelper helper) {
		String fixture = "toadSummonCreatesSingleBodyWithoutCooldown";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);

				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));

				Optional<PackView> view = MegumiShikigamiRuntime.packView(level.getServer(), ownerId);
				helper.assertTrue(view.isPresent(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"summon", helper.getTick(), ownerId, "pack view present", "present", "absent"));
				PackView pack = view.get();
				helper.assertTrue(MegumiShikigami.TOAD.id().equals(pack.type()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"pack type", MegumiShikigami.TOAD.id(), pack.type()));
				helper.assertTrue(pack.aliveBodies() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"alive bodies", "1", pack.aliveBodies()));
				helper.assertTrue(pack.anchorAlive(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"anchor alive", "true", pack.anchorAlive()));

				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "summon", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));

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

	/**
	 * S2 — sic on an AI zombie with zeroed speed 5 blocks away ends in a GRAB, not a hit: the tongue
	 * commits, the zombie is pinned at the grip anchor for 60..100 ticks without taking a single
	 * point of damage (R1 — the grab is control, not damage), the body itself never walks away from
	 * its anchor, and the hold ends with the throw: the victim is named by the throw flash and
	 * carries a fresh horizontal velocity away from its owner.
	 *
	 * <p><b>Traps avoided.</b> Slowness 100 removes the zombie's self-motion while full AI keeps
	 * physics, so nothing in this arena displaces it except the hold. The throw is asserted on
	 * {@code getDeltaMovement()} — the field the brain writes — rather than on displacement, because
	 * a speed-zeroed body barely converts velocity into ground movement. The release tick is
	 * asserted against the recorded {@code grabEndGameTime}, so a bind break (owner 16+ blocks away
	 * — impossible here) could not pass as the timer. The grab deadline sits far below the windup
	 * plus the shortest legal hold, so no assert can be satisfied by simply waiting.
	 */
	@GameTest(maxTicks = 260)
	public void toadGrabPinsItsTargetWithoutDamage(GameTestHelper helper) {
		String fixture = "toadGrabPinsItsTargetWithoutDamage";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		// A stone ceiling over the arena: a zombie victim dragged out from under a one-block roof
		// burns in daylight, which would silently eat the no-damage asserts below.
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		// AI body with zeroed speed: full AI keeps physics while Slowness 100 removes self-motion,
		// so every displacement observed below belongs to the grab or the throw.
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicReference<Vec3> anchorAtGrab = new AtomicReference<>();
		AtomicReference<Vec3> bodyAtGrab = new AtomicReference<>();
		AtomicLong grabTick = new AtomicLong(-1L);
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				helper.assertTrue(zombie.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "zombie alive", "true", zombie.isAlive()));

				healthBefore.set((double) zombie.getHealth());

				// Same aim math as the Nue scenarios: eye-to-chest ray, corridor is open air.
				TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		for (long tick = SIC_TICK + 1; tick <= HOLD_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
				try {
					helper.assertTrue(bodies.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick, caster.getUUID(),
									"Toad body present", "1", bodies.size()));
					MegumiToadEntity body = bodies.get(0);
					boolean holding = body.isHolding() && zombie.getUUID().equals(body.grabbedUuid());

					if (grabTick.get() < 0) {
						if (!holding) {
							if (pollTick == GRAB_DEADLINE_TICK || pollTick == HOLD_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"grab", pollTick, caster.getUUID(),
										"grab committed after the sic", "holding by tick " + GRAB_DEADLINE_TICK,
										"not holding"));
							}
							return;
						}
						grabTick.set(pollTick);
						anchorAtGrab.set(zombie.position());
						bodyAtGrab.set(body.position());
						helper.assertTrue(!body.grabbedIsPlayer(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
										caster.getUUID(), "mob victim is not flagged as a player",
										"false", body.grabbedIsPlayer()));
						// The mob is pinned to the anchor like a player is: it must hang in front of
						// the body, not stand where the tongue happened to find it. Without this the
						// "held" oracle would only say "somewhere within the 12-block grab range".
						double anchorDistance = Math.hypot(zombie.getX() - body.getX(), zombie.getZ() - body.getZ());
						helper.assertTrue(anchorDistance <= MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
										caster.getUUID(), "victim pinned at the grip anchor",
										"<= " + (MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK),
										anchorDistance));
						helper.assertTrue(zombie.getHealth() == healthBefore.get().doubleValue(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
										caster.getUUID(), "zombie health at the commit (R1: no instant damage)",
										healthBefore.get(), zombie.getHealth()));
						// The hold length is the policy's, not the brain's: the recorded end tick
						// must be the clamp/window the pure policy returns for this exact body.
						// Together with MegumiToadPolicyTest's monotonicity this is what pins
						// "heavier bodies are held shorter" without a second full-hold scenario.
						// The poll observes the commit up to POLL_LAG_TICKS after the brain ran, so
						// the window is [expected - lag, expected], not one number: a hardcoded hold
						// or a policy the brain ignores falls outside it.
						int expectedHold = MegumiToadPolicy.holdTicksFor(zombie.getMaxHealth(),
								zombie.getBbWidth() * zombie.getBbWidth() * zombie.getBbHeight(), false);
						long recordedHold = body.grabEndGameTime() - level.getGameTime();
						helper.assertTrue(recordedHold <= expectedHold && recordedHold >= expectedHold - POLL_LAG_TICKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
										caster.getUUID(), "recorded hold ticks against the policy",
										expectedHold + " down to " + (expectedHold - POLL_LAG_TICKS), recordedHold));
						return;
					}

					if (holding) {
						Vec3 drift = zombie.position().subtract(anchorAtGrab.get());
						double driftHorizontal = Math.hypot(drift.x, drift.z);
						helper.assertTrue(driftHorizontal <= PIN_TOLERANCE_BLOCKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "zombie drift from the grip anchor",
										"<= " + PIN_TOLERANCE_BLOCKS, driftHorizontal));
						Vec3 bodyDrift = body.position().subtract(bodyAtGrab.get());
						double bodyMove = Math.hypot(bodyDrift.x, bodyDrift.z);
						helper.assertTrue(bodyMove <= BODY_PIN_TOLERANCE_BLOCKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "toad stays planted while holding",
										"<= " + BODY_PIN_TOLERANCE_BLOCKS, bodyMove));
						helper.assertTrue(zombie.getHealth() == healthBefore.get().doubleValue(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "zombie health during the hold (the grab deals none)",
										healthBefore.get(), zombie.getHealth()));
						return;
					}

					// Released. The timer, not a bind break, must be what ended the hold: the leash at
					// the release tick is inside the bind range, so the break branch could not have
					// fired. The absolute release tick is deliberately NOT asserted against
					// grabEndGameTime — a poll callback can land several ticks after the brain ran,
					// and that drift is a property of the harness, not of the hold.
					long heldTicks = pollTick - grabTick.get();
					helper.assertTrue(!MegumiToadPolicy.bindBroken(body.distanceTo(caster),
									MegumiShikigamiProfile.TOAD_GRAB_BIND_RANGE),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "leash inside the bind range at the release",
									"<= " + MegumiShikigamiProfile.TOAD_GRAB_BIND_RANGE,
									body.distanceTo(caster)));
					helper.assertTrue(heldTicks >= MIN_HOLD_TICKS,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "hold length", ">= " + MIN_HOLD_TICKS, heldTicks));
					helper.assertTrue(!body.isHolding(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "grab cleared on release", "false", body.isHolding()));
					helper.assertTrue(zombie.getUUID().equals(body.throwFlashUuid()),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "throw flash names the victim",
									zombie.getUUID(), body.throwFlashUuid()));
					helper.assertTrue(body.throwFlashUntil() >= level.getGameTime(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "throw flash is fresh",
									">= " + level.getGameTime(), body.throwFlashUntil()));
					Vec3 velocity = zombie.getDeltaMovement();
					double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
					helper.assertTrue(horizontalSpeed >= THROW_MIN_SPEED,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "throw velocity", ">= " + THROW_MIN_SPEED, horizontalSpeed));
					Vec3 away = zombie.position().subtract(caster.position());
					double awayDot = velocity.x * away.x + velocity.z * away.z;
					helper.assertTrue(awayDot > 0.0,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "throw direction, away from the owner", "> 0", awayDot));
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
	 * S5 (R12) — nothing the held victim does can hurt the body holding it: damage sourced from the
	 * grabbed zombie is refused and leaves the toad's health untouched, while the positive control
	 * in the same scenario shows the very same call path does land damage once the source is not the
	 * held victim. Without that control the refusal assert would also pass on a body that simply
	 * cannot be hurt at all.
	 */
	@GameTest(maxTicks = 120)
	public void heldVictimCannotDamageItsHolder(GameTestHelper helper) {
		String fixture = "heldVictimCannotDamageItsHolder";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				TodoSwapTestFixtures.aimAt(caster, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				zombie.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		for (long tick = SIC_TICK + 1; tick <= GRAB_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
				try {
					if (bodies.size() != 1) {
						return;
					}
					MegumiToadEntity body = bodies.get(0);
					if (!body.isHolding() || !zombie.getUUID().equals(body.grabbedUuid())) {
						return;
					}
					done.set(true);
					double healthBefore = body.getHealth();
					boolean blocked = body.hurtServer(level, level.damageSources().mobAttack(zombie), 3.0f);
					helper.assertTrue(!blocked,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "blocked", pollTick,
									caster.getUUID(), "damage sourced from the held victim",
									"refused (false)", blocked));
					helper.assertTrue(body.getHealth() == healthBefore,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "blocked", pollTick,
									caster.getUUID(), "holder health after the refused hit",
									healthBefore, body.getHealth()));
					boolean control = body.hurtServer(level, level.damageSources().magic(), 1.0f);
					helper.assertTrue(control,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "control", pollTick,
									caster.getUUID(), "same hurtServer path, source is not the held victim",
									"damage lands (true)", control));
					helper.assertTrue(body.getHealth() < healthBefore,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "control", pollTick,
									caster.getUUID(), "holder health after the control hit",
									"< " + healthBefore, body.getHealth()));
					zombie.discard();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				helper.succeed();
			});
		}

		helper.runAtTickTime(HOLD_DEADLINE_TICK, () -> {
			if (done.get()) {
				return;
			}
			zombie.discard();
			MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", helper.getTick(),
					caster.getUUID(), "grab committed", "holding by tick " + GRAB_DEADLINE_TICK, "not holding"));
		});
	}

	/**
	 * S6 (D5/C6) — the toad holds players, not just mobs: a placed SURVIVAL victim pinned in front
	 * of the body carries the shared {@code GRIPPED} marker, stays inside the grip radius for the
	 * whole hold without losing health, and is released by the throw with a velocity pointed away
	 * from its owner. The marker is the same one the client reads to stop fighting the pin — moved
	 * by the server, checked here on the server.
	 */
	@GameTest(maxTicks = 260)
	public void toadHoldsAPlayerWithTheGrippedMarker(GameTestHelper helper) {
		String fixture = "toadHoldsAPlayerWithTheGrippedMarker";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(victimFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		ServerLevel level = helper.getLevel();

		AtomicReference<Vec3> anchorAtGrab = new AtomicReference<>();
		AtomicLong grabTick = new AtomicLong(-1L);
		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));
				helper.assertTrue(bodies.get(0).combatEnabled(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), ownerId,
								"body ACTIVE before sic", "true", bodies.get(0).combatEnabled()));
				TodoSwapTestFixtures.aimAt(caster, victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				CursedSpiritTestFixtures.cleanupVictim(helper, victim);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		for (long tick = SIC_TICK + 1; tick <= HOLD_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
				try {
					helper.assertTrue(bodies.size() == 1,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick, caster.getUUID(),
									"Toad body present", "1", bodies.size()));
					MegumiToadEntity body = bodies.get(0);
					boolean held = HoldSupport.isHeld(victim);

					if (grabTick.get() < 0) {
						if (!held) {
							if (pollTick == GRAB_DEADLINE_TICK || pollTick == HOLD_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"hold", pollTick, caster.getUUID(),
										"GRIPPED marker applied to the player victim",
										"held by tick " + GRAB_DEADLINE_TICK, "not held"));
							}
							return;
						}
						grabTick.set(pollTick);
						anchorAtGrab.set(victim.position());
						helper.assertTrue(body.grabbedIsPlayer(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "victim flagged as a player",
										"true", body.grabbedIsPlayer()));
						helper.assertTrue(victim.getUUID().equals(body.grabbedUuid()),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "held victim is the placed player",
										victim.getUUID(), body.grabbedUuid()));
						double distance = Math.hypot(victim.getX() - body.getX(), victim.getZ() - body.getZ());
						helper.assertTrue(distance <= MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "victim pinned at the grip anchor",
										"<= " + (MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK),
										distance));
						return;
					}

					if (held) {
						Vec3 drift = victim.position().subtract(anchorAtGrab.get());
						double driftHorizontal = Math.hypot(drift.x, drift.z);
						helper.assertTrue(driftHorizontal <= PIN_TOLERANCE_BLOCKS,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "player drift from the grip anchor",
										"<= " + PIN_TOLERANCE_BLOCKS, driftHorizontal));
						helper.assertTrue(victim.getHealth() == victim.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										caster.getUUID(), "held player takes no damage",
										victim.getMaxHealth(), victim.getHealth()));
						return;
					}

					long heldTicks = pollTick - grabTick.get();
					helper.assertTrue(heldTicks >= MIN_HOLD_TICKS,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "hold length", ">= " + MIN_HOLD_TICKS, heldTicks));
					helper.assertTrue(!body.isHolding(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "body cleared its grab on release",
									"false", body.isHolding()));
					Vec3 velocity = victim.getDeltaMovement();
					double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
					helper.assertTrue(horizontalSpeed >= THROW_MIN_SPEED,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "player throw velocity",
									">= " + THROW_MIN_SPEED, horizontalSpeed));
					Vec3 away = victim.position().subtract(caster.position());
					double awayDot = velocity.x * away.x + velocity.z * away.z;
					helper.assertTrue(awayDot > 0.0,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "throw", pollTick,
									caster.getUUID(), "throw direction, away from the owner", "> 0", awayDot));
					done.set(true);
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * S7 — recalling the body while it holds a player lets the player go: the marker is dropped the
	 * moment the recall starts (the hold must never outlive its body), the grab state is cleared,
	 * and once the recall sink finishes no owned body is left in the level. Without the explicit
	 * release this is exactly where a pin would become permanent.
	 */
	@GameTest(maxTicks = 220)
	public void recallReleasesTheHeldPlayer(GameTestHelper helper) {
		String fixture = "recallReleasesTheHeldPlayer";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos victimFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(victimFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, victimFeet);
		ServerLevel level = helper.getLevel();

		AtomicBoolean done = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				TodoSwapTestFixtures.aimAt(caster, victim.position().add(0.0, victim.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				CursedSpiritTestFixtures.cleanupVictim(helper, victim);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		AtomicBoolean recalled = new AtomicBoolean();
		for (long tick = SIC_TICK + 1; tick <= HOLD_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				try {
					if (!recalled.get()) {
						if (!HoldSupport.isHeld(victim)) {
							if (pollTick == GRAB_DEADLINE_TICK) {
								helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
										"hold", pollTick, caster.getUUID(),
										"player held before the recall", "held by tick " + GRAB_DEADLINE_TICK,
										"not held"));
							}
							return;
						}
						recalled.set(true);
						boolean re = MegumiShikigamiRuntime.tryPrimary(caster, false);
						helper.assertTrue(re, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"recall", pollTick, caster.getUUID(),
								"tryPrimary recall while holding", "true", re));
						return;
					}
					// One tick after the recall the marker must already be gone: the release is not
					// deferred to the end of the recall sink.
					if (HoldSupport.isHeld(victim)) {
						return;
					}
					List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
					helper.assertTrue(!HoldSupport.isHeld(victim),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "release", pollTick,
									caster.getUUID(), "GRIPPED marker dropped", "false", HoldSupport.isHeld(victim)));
					helper.assertTrue(bodies.stream().noneMatch(MegumiToadEntity::isHolding),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "release", pollTick,
									caster.getUUID(), "no body still holds a victim", "none", "one holds"));
					done.set(true);
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					helper.succeed();
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					CursedSpiritTestFixtures.cleanupVictim(helper, victim);
					MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
					throw failure;
				}
			});
		}
	}

	/**
	 * S8 — the tag is the gate: an iron golem, a member of
	 * {@code jujutsumod:ungrabbable}, is never held. The scenario runs the same arena, the same
	 * distance and the same aim as S2 (where a zombie <em>is</em> grabbed), so the reach path is
	 * proven non-vacuous by construction — and the cooldown the body arms while committing proves
	 * the commit itself ran and refused, rather than never being reached. Health is asserted
	 * untouched too: a refused grab deals nothing.
	 */
	@GameTest(maxTicks = 120)
	public void ungrabbableTargetsAreRefused(GameTestHelper helper) {
		String fixture = "ungrabbableTargetsAreRefused";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos golemFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(golemFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		IronGolem golem = GameTestFixtures.spawnMob(helper, fixture, EntityType.IRON_GOLEM, golemFeet);
		golem.setPersistenceRequired();
		// Inert body: the scenario is about the grab gate, not about a fight.
		golem.setNoAi(true);
		double golemHealthBefore = golem.getHealth();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				TodoSwapTestFixtures.aimAt(caster, golem.position().add(0.0, golem.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				golem.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
		});

		// Two windups' worth of ticks: long past the commit, still far inside the shortest hold.
		helper.runAtTickTime(SIC_TICK + 40, () -> {
			try {
				List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(),
								caster.getUUID(), "Toad body present", "1", bodies.size()));
				MegumiToadEntity body = bodies.get(0);
				helper.assertTrue(!body.isHolding(),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(),
								caster.getUUID(), "grab on an ungrabbable target", "refused (false)",
								body.isHolding()));
				helper.assertTrue(body.grabbedUuid() == null,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(),
								caster.getUUID(), "no victim recorded", "null", body.grabbedUuid()));
				helper.assertTrue(!body.attackReady(level.getGameTime()),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(),
								caster.getUUID(), "the commit ran and armed its cooldown",
								"cooldown armed at tick " + level.getGameTime(), "still ready"));
				helper.assertTrue(golem.getHealth() == golemHealthBefore,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "refuse", helper.getTick(),
								caster.getUUID(), "golem health (a refused grab deals nothing)",
								golemHealthBefore, golem.getHealth()));
				golem.discard();
			} catch (RuntimeException | AssertionError failure) {
				golem.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
				throw failure;
			}
			MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			helper.succeed();
		});
	}

	/**
	 * S9 (R14) — the grab pins the victim, it does not disarm it: while the toad holds a zombie, the
	 * zombie keeps its AI and still aims at the nearest player, and once the owner steps into the
	 * victim's reach the owner takes the bite. That is the other half of S5's refusal — without it an
	 * implementation that simply made the held body harmless to everyone would still pass S5.
	 *
	 * <p>The owner is a placed SURVIVAL player, not the mock caster used elsewhere in this file: a
	 * mock reads CREATIVE and would swallow the very damage the scenario is about. It is also the
	 * owner who moves to the pinned victim, not the other way round — the body must not be steered
	 * into reach for the assert to pass.
	 *
	 * <p>Two oracles, because a pinned body's swing clock is not the point: the AI/target asserts
	 * prove the held victim was not disarmed, and the damage itself is taken from the very call the
	 * melee goal makes ({@code doHurtTarget}) a fixed 20 ticks into the hold. Waiting for the goal's
	 * own swing inside the hold would test the zombie's cooldown, not the hold.
	 */
	@GameTest(maxTicks = 240)
	public void theHeldVictimStillReachesItsOwner(GameTestHelper helper) {
		String fixture = "theHeldVictimStillReachesItsOwner";
		BlockPos ownerFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(4, 1, 3);
		layStoneFloor(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		laySkyCover(helper);

		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, ownerFeet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);

		AtomicBoolean done = new AtomicBoolean();
		AtomicLong grabTick = new AtomicLong(-1L);

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(owner, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), owner.getUUID(), "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				TodoSwapTestFixtures.aimAt(owner, zombie.position().add(0.0, zombie.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(owner, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), owner.getUUID(), "trySic result", "true", sicced));
			} catch (RuntimeException | AssertionError failure) {
				zombie.discard();
				CursedSpiritTestFixtures.cleanupVictim(helper, owner);
				throw failure;
			}
		});

		for (long tick = SIC_TICK + 1; tick <= HOLD_DEADLINE_TICK; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiToadEntity> bodies = toadOwnedBy(level, owner.getUUID());
				try {
					if (bodies.isEmpty()) {
						return;
					}
					MegumiToadEntity body = bodies.get(0);
					if (!body.isHolding()) {
						if (pollTick == GRAB_DEADLINE_TICK || pollTick == HOLD_DEADLINE_TICK) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"grab", pollTick, owner.getUUID(),
									"grab committed so the owner can be reached", "holding by tick "
											+ GRAB_DEADLINE_TICK, "not holding"));
						}
						return;
					}
					if (grabTick.get() < 0) {
						grabTick.set(pollTick);
						// The hold pins the body; it must not disarm it. No setNoAi on a held mob is
						// the design's own rule.
						helper.assertTrue(!zombie.isNoAi(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "hold", pollTick,
										owner.getUUID(), "held victim keeps its AI", "false", zombie.isNoAi()));
						// Acquisition timing is not the subject here: the victim is aimed at its owner
						// the way its own targeting goal would, and the owner steps into reach. The
						// pinned victim must not be steered instead.
						zombie.setTarget(owner);
						owner.teleportTo(level, zombie.getX() + 1.0, zombie.getY(), zombie.getZ() + 1.0,
								Set.of(), 0.0f, 0.0f, false);
					}
					if (owner.getHealth() < owner.getMaxHealth()) {
						done.set(true);
						zombie.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
						helper.succeed();
						return;
					}
					// The bite lands only when the goal's own clock allows it; the mechanism the
					// scenario is about is the same call that goal makes, so it is asserted directly
					// instead of waiting on swing timing inside a 100-tick hold.
					if (pollTick == grabTick.get() + 20) {
						helper.assertTrue(zombie.getTarget() == owner,
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bite", pollTick,
										owner.getUUID(), "held victim keeps its target on the owner",
										owner.getUUID(), zombie.getTarget()));
						helper.assertTrue(zombie.doHurtTarget(level, owner),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bite", pollTick,
										owner.getUUID(), "a held victim can still hurt its owner",
										"damage lands (true)", false));
						helper.assertTrue(owner.getHealth() < owner.getMaxHealth(),
								MegumiShikigamiTestFixtures.diagnostic(fixture, "bite", pollTick,
										owner.getUUID(), "owner health after the held victim's hit",
										"< " + owner.getMaxHealth(), owner.getHealth()));
						done.set(true);
						zombie.discard();
						CursedSpiritTestFixtures.cleanupVictim(helper, owner);
						helper.succeed();
						return;
					}
					if (pollTick == HOLD_DEADLINE_TICK) {
						helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
								"owner", pollTick, owner.getUUID(),
								"the pinned victim still reaches its owner", "owner damaged during the hold",
								"owner at " + owner.getHealth() + "/" + owner.getMaxHealth()));
					}
				} catch (RuntimeException | AssertionError failure) {
					done.set(true);
					zombie.discard();
					CursedSpiritTestFixtures.cleanupVictim(helper, owner);
					throw failure;
				}
			});
		}
	}

	/**
	 * S3 — pressing the key again while the Toad is out recalls it: PRIMARY reads exactly 240
	 * ticks in the recall tick, the pack record is gone, and once the 12-tick recall sink
	 * finishes no owned body remains in the level.
	 */
	@GameTest(maxTicks = 60)
	public void toadRecallChargesRecallCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "toadRecallChargesRecallCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "second tryPrimary result", "true", recalled));

				// Same-tick read: the cooldown was just armed, so the remaining time is exact.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_RECALL_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"PRIMARY recall cooldown", EXPECTED_RECALL_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "recall", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});

		// The recall sink (TOAD_RECALL_TICKS) plays out after the record is gone; past it +2 the
		// body must have left the level lookup.
		helper.runAtTickTime(RECALL_TICK + MegumiShikigamiProfile.TOAD_RECALL_TICKS + 2, () -> {
			List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
			helper.assertTrue(bodies.isEmpty(), MegumiShikigamiTestFixtures.diagnostic(fixture,
					"gone", helper.getTick(), caster.getUUID(), "owned Toad bodies in level", "0", bodies.size()));
		});
		helper.runAtTickTime(30, () -> helper.succeed());
	}

	/**
	 * S4 — killing the Toad body routes through the death reconciliation: PRIMARY reads exactly
	 * 400 ticks and the pack record is gone. The kill waits past the 16-tick materialization with
	 * an explicit ACTIVE premise, because {@code hurtServer} on the body is gated on combat being
	 * enabled.
	 */
	@GameTest(maxTicks = 80)
	public void toadDeathChargesDeathCooldownAndClearsPack(GameTestHelper helper) {
		String fixture = "toadDeathChargesDeathCooldownAndClearsPack";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layStoneFloor(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
		}));

		helper.runAtTickTime(KILL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiToadEntity> bodies = toadOwnedBy(level, ownerId);
				helper.assertTrue(bodies.size() == 1,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"owned Toad bodies in level", "1", bodies.size()));
				MegumiToadEntity body = bodies.get(0);
				helper.assertTrue(body.combatEnabled(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "body ACTIVE before kill", "true", body.combatEnabled()));

				// The real damage pipeline (AFTER_DEATH -> reconcile -> death cooldown), not die().
				boolean damaged = body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// AFTER_DEATH reconciles synchronously inside hurtServer, so the same-tick read is exact.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining == EXPECTED_DEATH_COOLDOWN_TICKS,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"PRIMARY death cooldown", EXPECTED_DEATH_COOLDOWN_TICKS, remaining));

				MegumiShikigamiTestFixtures.assertNoPack(helper, fixture, "kill", caster);
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}
	/**
	 * R2 — the body grabs on its own authority: with a zombie inside the grab radius and no sic
	 * ever issued, the brain's throttled self-pick still commits to a GRAB by the deadline — the
	 * zombie pinned at the grip anchor with no damage dealt. The sic path is unreachable here by
	 * construction (fresh fixtures, {@code trySic} never called) and the retaliation pass cannot
	 * arm either (the slowed victim can never reach anyone to hit), so a hold proves the
	 * self-pick branch of {@code MegumiToadBrain.pickTarget}. Red-proof: drop the self-pick
	 * branch and the deadline assert fires — nothing ever holds.
	 */
	@GameTest(maxTicks = 160)
	public void toadGrabsNearbyTargetWithoutSicOrder(GameTestHelper helper) {
		String fixture = "toadGrabsNearbyTargetWithoutSicOrder";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos zombieFeet = new BlockPos(6, 1, 5);
		layStoneFloor(helper);
		helper.setBlock(zombieFeet.below(), Blocks.STONE);
		laySkyCover(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie zombie = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, zombieFeet);
		zombie.setPersistenceRequired();
		zombie.setNoAi(false);
		// Same zeroed-speed AI body as S2: full AI keeps physics while Slowness 100 removes
		// self-motion — and guarantees the victim can never land the hit that would arm the
		// retaliation pass, so only the self-pick can name it.
		zombie.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 2400, 100, false, false, false), caster);

		// ACTIVE at ~summon+16, the first self-pick scan on the next brain tick, a 6-tick windup:
		// 100 ticks of margin without approaching the shortest legal hold.
		final long selfPickDeadline = SUMMON_TICK + 100;
		AtomicReference<Double> healthBefore = new AtomicReference<>();
		AtomicBoolean done = new AtomicBoolean();
		AtomicBoolean commitSeen = new AtomicBoolean();

		helper.runAtTickTime(SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"summon", helper.getTick(), ownerId, "tryPrimary result", "true", summoned));
			healthBefore.set((double) zombie.getHealth());
		}));

		// No sic block on purpose: trySic is never called in this scenario.
		for (long tick = SUMMON_TICK + 1; tick <= selfPickDeadline; tick++) {
			final long pollTick = tick;
			helper.runAtTickTime(pollTick, () -> {
				if (done.get()) {
					return;
				}
				List<MegumiToadEntity> bodies = toadOwnedBy(level, caster.getUUID());
				try {
					if (bodies.size() != 1 || !bodies.get(0).combatEnabled()) {
						if (pollTick == selfPickDeadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"grab", pollTick, caster.getUUID(), "body ACTIVE for the self-pick",
									"1 ACTIVE body", bodies.size() + " bodies"));
						}
						return;
					}
					MegumiToadEntity body = bodies.get(0);
					if (!body.isHolding() || !zombie.getUUID().equals(body.grabbedUuid())) {
						if (pollTick == selfPickDeadline) {
							helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
									"grab", pollTick, caster.getUUID(),
									"self-pick grab committed without sic",
									"holding zombie by tick " + selfPickDeadline, "not holding"));
						}
						return;
					}
					// Committed — the same control oracles as S2: a mob victim pinned at the
					// anchor with no damage dealt. The pin lands in the brain tick after the
					// commit, so a poll sampling between the two sees holding=true with the
					// victim still travelling: the pin oracle starts one tick after the commit.
					if (!commitSeen.getAndSet(true)) {
						return;
					}
					helper.assertTrue(!body.grabbedIsPlayer(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
									caster.getUUID(), "mob victim is not flagged as a player",
									"false", body.grabbedIsPlayer()));
					double anchorDistance = Math.hypot(zombie.getX() - body.getX(), zombie.getZ() - body.getZ());
					helper.assertTrue(anchorDistance <= MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK,
							MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
									caster.getUUID(), "victim pinned at the grip anchor",
									"<= " + (MegumiShikigamiProfile.TOAD_GRIP_OFFSET + ANCHOR_SLACK),
									anchorDistance));
					helper.assertTrue(zombie.getHealth() == healthBefore.get().doubleValue(),
							MegumiShikigamiTestFixtures.diagnostic(fixture, "grab", pollTick,
									caster.getUUID(), "zombie health at the commit (no instant damage)",
									healthBefore.get(), zombie.getHealth()));
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
	 * Every live Toad body owned by {@code ownerId} in {@code level}. Owner-filtered, never
	 * bounds-filtered: the world offset is random per run and bodies drift (follow), so a
	 * structure bounds scan could miss a live body or catch a sibling test's. Fresh mock UUIDs
	 * per test make the owner filter exact. Local (not in the shared fixtures) so Block 1 needs
	 * no shared-file edit.
	 */
	private static List<MegumiToadEntity> toadOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiToadEntity> owned = new ArrayList<>();
		for (MegumiToadEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiToadEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/** Floor-supported 3x3 stone pad so the ground placement always finds a safe body spot. */
	private static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 3; dx++) {
			for (int dz = 1; dz <= 3; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}

	/**
	 * A stone ceiling over the whole arena at head height. Zombie victims that burn in daylight would
	 * otherwise lose health during the hold — the grip drags them away from any single roof block —
	 * and every "the grab deals no damage" assert would read the sun instead of the grab.
	 */
	private static void laySkyCover(GameTestHelper helper) {
		for (int dx = 1; dx <= 7; dx++) {
			for (int dz = 1; dz <= 7; dz++) {
				helper.setBlock(new BlockPos(dx, 4, dz), Blocks.STONE);
			}
		}
	}
}
