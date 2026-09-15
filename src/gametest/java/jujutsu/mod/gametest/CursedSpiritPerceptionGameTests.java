package jujutsu.mod.gametest;

import net.minecraft.network.chat.Component;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Issue #80 server oracles: no target, no damage either way, no swing connect, no push, no
 * tracking and no VFX audience for non-perceiving players; mages and non-players unaffected.
 */
public final class CursedSpiritPerceptionGameTests {
	private static final long AGRO_WATCH_TICKS = 200L;

	private static Component diag(String fixture, long tick, String what, Object expected, Object actual) {
		return Component.literal(fixture + "@" + tick + " " + what + " expected=" + expected + " actual=" + actual);
	}

	/** Step 3 — a NONE victim next to the spirit is never acquired. */
	@GameTest(maxTicks = 240, skyAccess = true)
	public void noneVictimIsNeverTargeted(GameTestHelper helper) {
		String fixture = "noneVictimIsNeverTargeted";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		for (long tick = 10; tick <= AGRO_WATCH_TICKS; tick += 10) {
			final long poll = tick;
			helper.runAtTickTime(poll, () -> helper.assertTrue(spirit.getTarget() == null,
					diag(fixture, poll, "none not targeted", "null", spirit.getTarget())));
		}
		helper.runAtTickTime(AGRO_WATCH_TICKS, () -> {
			cleanup(helper, spirit, victim);
			helper.succeed();
		});
	}

	/** Step 3 — a MEGUMI victim is acquired (the gate is the flag, not proximity). */
	@GameTest(maxTicks = 150, skyAccess = true)
	public void mageVictimIsTargeted(GameTestHelper helper) {
		String fixture = "mageVictimIsTargeted";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		CharacterSelectionManager.select(victim, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		AtomicBoolean done = new AtomicBoolean();
		for (long tick = 2; tick <= 60; tick++) {
			long poll = tick;
			helper.runAtTickTime(poll, () -> {
				if (done.get()) {
					return;
				}
				if (spirit.getTarget() == victim) {
					done.set(true);
					cleanup(helper, spirit, victim);
					helper.succeed();
					return;
				}
				if (poll == 60) {
					try {
						helper.assertTrue(false, diag(fixture, poll, "mage acquired before combat can end",
								victim.getUUID(), "target=" + spirit.getTarget()
										+ " victimAlive=" + victim.isAlive()
										+ " victimHealth=" + victim.getHealth()));
					} finally {
						cleanup(helper, spirit, victim);
					}
				}
			});
		}
	}

	/** Step 4 — NONE in the crater keeps full HP while the mage, a cow and a 2nd spirit are hurt. */
	@GameTest(maxTicks = 220, skyAccess = true)
	public void craterSparesNoneButHitsOthers(GameTestHelper helper) {
		String fixture = "craterSparesNoneButHitsOthers";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(3, 1, 2));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(2, 1, 4));
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.GREATER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritEntity second = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(1, 1, 2));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 1));
		CursedSpiritTestFixtures.freezeGround(spirit);
		CursedSpiritTestFixtures.freezeGround(second);
		CursedSpiritTestFixtures.freezeGround(cow);
		double noneMax = none.getHealth();
		double secondMax = second.getHealth();
		double cowMax = cow.getHealth();
		helper.runAtTickTime(200, () -> {
			try {
				helper.assertTrue(mage.getHealth() < mage.getMaxHealth(),
						diag(fixture, 200, "mage hurt", "<max", mage.getHealth()));
				helper.assertTrue(none.getHealth() == noneMax,
						diag(fixture, 200, "none unhurt", noneMax, none.getHealth()));
				helper.assertTrue(second.getHealth() < secondMax,
						diag(fixture, 200, "second spirit hurt", "<" + secondMax, second.getHealth()));
				helper.assertTrue(cow.getHealth() < cowMax,
						diag(fixture, 200, "cow hurt", "<" + cowMax, cow.getHealth()));
			} finally {
				cow.discard();
				second.discard();
				cleanup(helper, spirit, mage);
				CursedSpiritTestFixtures.cleanupVictim(helper, none);
			}
			helper.succeed();
		});
	}

	/** Step 5 — the swing gate answers FAIL for NONE and PASS for a mage, without HP asserts. */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void swingGateAnswersByFlag(GameTestHelper helper) {
		String fixture = "swingGateAnswersByFlag";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 4));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(5, () -> {
			try {
				InteractionResult noneAnswer = AttackEntityCallback.EVENT.invoker()
						.interact(none, level, InteractionHand.MAIN_HAND, spirit, null);
				InteractionResult mageAnswer = AttackEntityCallback.EVENT.invoker()
						.interact(mage, level, InteractionHand.MAIN_HAND, spirit, null);
				helper.assertTrue(noneAnswer == InteractionResult.FAIL,
						diag(fixture, 5, "none swing fails", "FAIL", noneAnswer));
				helper.assertTrue(mageAnswer == InteractionResult.PASS,
						diag(fixture, 5, "mage swing passes", "PASS", mageAnswer));
			} finally {
				cleanup(helper, spirit, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
			}
			helper.succeed();
		});
	}

	/** Step 6 — overlapping NONE keeps position while an overlapping mage is shoved (relative). */
	@GameTest(maxTicks = 80, skyAccess = true)
	public void overlapMovesMageButNotNone(GameTestHelper helper) {
		String fixture = "overlapMovesMageButNotNone";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(3, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(1, 1, 2));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		CursedSpiritTestFixtures.freezeGround(spirit);
		// The gate lives in the shared Entity.push sink — the exact call the vanilla proximity
		// loop makes per pair per tick. Stationary headless pairs never reach that loop on their
		// own, so the scenario drives the sink directly: real method, real mixin HEAD. The spirit
		// is the observed body (a plain Mob integrates pushes server-side; frozen bodies still
		// integrate external impulses — proven by knockbackDisplacementOrdersByTier). Red-proof:
		// with the mixin off, the NONE push moves the spirit and the second oracle fails.
		AtomicReference<Vec3> beforePush = new AtomicReference<>();
		AtomicReference<Vec3> afterMagePush = new AtomicReference<>();
		helper.runAtTickTime(5, () -> {
			beforePush.set(spirit.position());
			spirit.push(mage);
		});
		helper.runAtTickTime(25, () -> {
			afterMagePush.set(spirit.position());
			double mageShove = Math.sqrt(afterMagePush.get().distanceToSqr(beforePush.get()));
			helper.assertTrue(mageShove > 0.05,
					diag(fixture, 25, "mage pair pushes spirit", ">0.05", mageShove));
			spirit.push(none);
		});
		helper.runAtTickTime(35, () -> {
			try {
				double noneShove =
						Math.sqrt(spirit.position().distanceToSqr(afterMagePush.get()));
				helper.assertTrue(noneShove <= 0.05,
						diag(fixture, 35, "none pair blocked", "<=0.05", noneShove));
			} finally {
				cleanup(helper, spirit, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
			}
			helper.succeed();
		});
	}

	/** Step 7 — NONE is absent from the tracker's seenBy, mage present, switch flips in ticks. */
	@GameTest(maxTicks = 120, skyAccess = true)
	public void trackerHidesSubjectFromNone(GameTestHelper helper) {
		String fixture = "trackerHidesSubjectFromNone";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 4));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.assertTrue(CursePerception.isSubject(spirit), diag(fixture, 0, "spirit subject", "true", false));
		helper.assertTrue(!CursePerception.isSubject(none), diag(fixture, 0, "victim not subject", "false", true));
		helper.runAtTickTime(40, () -> {
			// Premises that separate "gate cuts a perceiver" from "body not in the world".
			helper.assertTrue(CharacterSelectionManager.selected(mage) == JujutsuCharacter.MEGUMI,
					diag(fixture, 40, "mage selected", JujutsuCharacter.MEGUMI,
							CharacterSelectionManager.selected(mage)));
			helper.assertTrue(CursePerception.perceives(mage),
					diag(fixture, 40, "mage perceives", "true", CursePerception.perceives(mage)));
			driveTracking(spirit, none, mage);
			helper.assertTrue(!isTrackedBy(spirit, none), diag(fixture, 40, "none untracked", "false", true));
			helper.assertTrue(isTrackedBy(spirit, mage), diag(fixture, 40, "mage tracked", "true", false));
			CharacterSelectionManager.select(none, JujutsuCharacter.MEGUMI);
		});
		helper.runAtTickTime(45, () -> {
			try {
				driveTracking(spirit, none);
				helper.assertTrue(isTrackedBy(spirit, none),
						diag(fixture, 45, "flipped victim tracked<=5t", "true", false));
			} finally {
				cleanup(helper, spirit, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
			}
			helper.succeed();
		});
	}

	/** Step 9 — the VFX audience is perceivers only; unfiltered it is everyone in radius. */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void vfxAudienceIsPerceiversOnly(GameTestHelper helper) {
		String fixture = "vfxAudienceIsPerceiversOnly";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 4));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		helper.runAtTickTime(5, () -> {
			try {
				List<ServerPlayer> filtered = JujutsuNetworking.recipients(level,
						mage.position(), 6.0, CursePerception::perceives);
				List<ServerPlayer> open = JujutsuNetworking.recipients(level,
						mage.position(), 6.0, player -> true);
				helper.assertTrue(filtered.size() == 1 && filtered.contains(mage),
						diag(fixture, 5, "audience=[mage]", "[mage]", filtered));
				helper.assertTrue(open.contains(none) && open.contains(mage),
						diag(fixture, 5, "open has both", "both", open.size()));
		} finally {
			CursedSpiritTestFixtures.cleanupVictim(helper, none);
			CursedSpiritTestFixtures.cleanupVictim(helper, mage);
		}
		helper.succeed();
	});
	}
	/**
	 * Runs the REAL tracking chain synchronously for the given players. Headless victims never
	 * move on their own, so the vanilla chain would never re-evaluate them and the oracles would
	 * observe chain stasis instead of the gate. Every step below is a genuine vanilla call:
	 * {@code ChunkMap.move} refreshes the chunk view through the real path, the headless
	 * sender's unacknowledged stall is cleared the way a client ack would, pending chunks flush
	 * now, and finally {@code TrackedEntity.updatePlayer} — the exact method the tracking mixin
	 * injects into — runs for real. Red-proof: with the mixin off, the NONE drive lands in
	 * {@code seenBy} and the negative oracle fails.
	 */
	private static void driveTracking(CursedSpiritEntity spirit, ServerPlayer... players) {
		ServerLevel level = (ServerLevel) spirit.level();
		for (ServerPlayer player : players) {
			level.getChunkSource().chunkMap.move(player);
			player.connection.chunkSender.onChunkBatchReceivedByClient(64.0f);
			player.connection.chunkSender.sendNextChunks(player);
			updatePlayerReflective(spirit, player);
		}
	}

	@SuppressWarnings("unchecked")
	private static void updatePlayerReflective(CursedSpiritEntity spirit, ServerPlayer player) {
		try {
			Object chunkMap = ((ServerLevel) spirit.level()).getChunkSource().chunkMap;
			Field entityMapField = chunkMap.getClass().getDeclaredField("entityMap");
			entityMapField.setAccessible(true);
			Object entityMap = entityMapField.get(chunkMap);
			Object tracked = ((it.unimi.dsi.fastutil.ints.Int2ObjectMap<Object>) entityMap).get(spirit.getId());
			if (tracked == null) {
				throw new IllegalStateException("spirit has no TrackedEntity at all");
			}
			java.lang.reflect.Method updatePlayer =
					tracked.getClass().getMethod("updatePlayer", ServerPlayer.class);
			updatePlayer.setAccessible(true);
			updatePlayer.invoke(tracked, player);
		} catch (ReflectiveOperationException failure) {
			// InvocationTargetException included: it extends ReflectiveOperationException.
			throw new IllegalStateException("tracker drive reflection broke", failure);
		}
	}

	/**
	 * Post-merge review F1 — the shared sound sink: a server-side {@code playSound} (the
	 * funnel for footsteps, swims, falls AND voices) reaches the mage's connection as a
	 * {@code ClientboundSoundPacket} and sends nothing to the NONE victim. The oracle reads
	 * the REAL outbound packet queue of each victim's loopback {@code EmbeddedChannel} —
	 * red-proof: without the {@code playSound} override the vanilla broadcast lands in both
	 * queues and the negative arm fails.
	 */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void spiritSoundsReachPerceiversOnly(GameTestHelper helper) {
		String fixture = "spiritSoundsReachPerceiversOnly";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 4));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(5, () -> {
			try {
				// Flush login/placement traffic so only the probe sound is observed.
				drainSoundPackets(none);
				drainSoundPackets(mage);
				int mageSentBefore = sentCount(mage);
				int noneSentBefore = sentCount(none);
				spirit.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, 1.0f, 1.0f);
				int mageSentDelta = sentCount(mage) - mageSentBefore;
				int noneSentDelta = sentCount(none) - noneSentBefore;
				String aud = "aud[players=" + ((ServerLevel) spirit.level()).players().size()
						+ " perceives(mage)=" + jujutsu.mod.cursedspirit.perception.CursePerception.perceives(mage)
						+ " perceives(none)=" + jujutsu.mod.cursedspirit.perception.CursePerception.perceives(none)
						+ " sel=" + CharacterSelectionManager.selected(mage)
						+ " distSqr=" + mage.distanceToSqr(spirit.getX(), spirit.getY(), spirit.getZ())
						+ " spiritPos=" + spirit.position() + " magePos=" + mage.position() + "]";
				// Control probe: a packet we send through the same connection.send path —
				// separates "spirit send never queued" from "loopback read is blind".
				mage.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
						net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT),
						net.minecraft.sounds.SoundSource.HOSTILE, 9999.0, 9999.0, 9999.0,
						1.0f, 1.0f, 0L));
				List<Object> noneRaw = drainSoundPackets(none);
				List<Object> mageRaw = drainSoundPackets(mage);
				List<net.minecraft.network.protocol.game.ClientboundSoundPacket> noneHeard =
						soundPacketsAt(noneRaw, spirit);
				List<net.minecraft.network.protocol.game.ClientboundSoundPacket> mageHeard =
						soundPacketsAt(mageRaw, spirit);
				helper.assertTrue(noneHeard.isEmpty(),
						diag(fixture, 5, "none hears nothing", "[]",
								noneHeard + " raw=" + outboundSummary(noneRaw)
										+ " " + connectionDiag(none)));
				helper.assertTrue(!mageHeard.isEmpty(),
						diag(fixture, 5, "mage hears the sound", ">=1",
								mageHeard.size() + " raw=" + outboundSummary(mageRaw)
										+ " sentDelta=" + mageSentDelta
										+ "/" + noneSentDelta + " " + aud + " " + connectionDiag(mage)));
			} finally {
				cleanup(helper, spirit, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
			}
			helper.succeed();
		});
	}

	/**
	 * Post-merge review F3 — projectile owner chain: an arrow owned by a NONE victim (a)
	 * is refused by the damage gate even when the damage source names only the arrow as
	 * the direct entity (causing entity absent — the owner chain must recover the player),
	 * and (b) is not even a hit candidate ({@code canHitEntity} answers false through the
	 * projectile mixin), so it cannot stick into or bounce off the invisible body. The
	 * mage's arrow passes both arms — red-proof: with the mixin/gate off, the NONE arrow
	 * hits and the first two asserts fail.
	 */
	@GameTest(maxTicks = 40, skyAccess = true)
	public void nonPerceiverProjectileCannotHitSpirit(GameTestHelper helper) {
		String fixture = "nonPerceiverProjectileCannotHitSpirit";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer none = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
		ServerPlayer mage = CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 4));
		CharacterSelectionManager.select(mage, JujutsuCharacter.MEGUMI);
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
		helper.runAtTickTime(5, () -> {
			try {
				net.minecraft.world.entity.projectile.Arrow noneArrow =
						helper.spawn(EntityType.ARROW, new BlockPos(3, 1, 3));
				noneArrow.setOwner(none);
				net.minecraft.world.entity.projectile.Arrow mageArrow =
						helper.spawn(EntityType.ARROW, new BlockPos(3, 1, 4));
				mageArrow.setOwner(mage);
				helper.assertTrue(!canHitEntity(noneArrow, spirit),
						diag(fixture, 5, "none arrow is no hit candidate", "false", true));
				helper.assertTrue(canHitEntity(mageArrow, spirit),
						diag(fixture, 5, "mage arrow stays a candidate", "true", false));
				// Causing entity deliberately absent: the gate must walk arrow → owner.
				net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> arrowType =
						level.registryAccess()
								.lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
								.getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW);
				boolean noneAllowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
						.allowDamage(spirit, new DamageSource(arrowType, noneArrow), 5.0f);
				boolean mageAllowed = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
						.allowDamage(spirit, new DamageSource(arrowType, mageArrow), 5.0f);
				helper.assertTrue(!noneAllowed,
						diag(fixture, 5, "none-owned arrow damage refused", "false", noneAllowed));
				helper.assertTrue(mageAllowed,
						diag(fixture, 5, "mage-owned arrow damage allowed", "true", mageAllowed));
				noneArrow.discard();
				mageArrow.discard();
			} finally {
				cleanup(helper, spirit, none);
				CursedSpiritTestFixtures.cleanupVictim(helper, mage);
			}
			helper.succeed();
		});
	}

	/** Invokes the real (mixin-injected) {@code Projectile.canHitEntity} reflectively. */
	private static boolean canHitEntity(net.minecraft.world.entity.projectile.Projectile projectile,
			net.minecraft.world.entity.Entity target) {
		try {
			java.lang.reflect.Method canHit = net.minecraft.world.entity.projectile.Projectile.class
					.getDeclaredMethod("canHitEntity", net.minecraft.world.entity.Entity.class);
			canHit.setAccessible(true);
			return (boolean) canHit.invoke(projectile, target);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("canHitEntity reflection broke", failure);
		}
	}

	/**
	 * Drains the victim's loopback channel and returns every queued
	 * {@code ClientboundSoundPacket}. The chain is the same one {@code send} walks:
	 * {@code ServerPlayer.connection} (the listener) → its protected {@code connection}
	 * field → the netty {@code channel}; the fixture builds a real {@code EmbeddedChannel},
	 * so written packets queue as outbound messages.
	 *
	 * <p>Two EmbeddedChannel realities had to be learned at runtime: {@code Connection.send}
	 * called off the event loop (the GameTest thread) does NOT write immediately — it
	 * schedules the write on the channel's event loop, and an {@code EmbeddedChannel}'s
	 * embedded loop only runs queued tasks inside {@code runPendingTasks()} /
	 * {@code runScheduledPendingTasks()}. So the drain must pump both first, or every queue
	 * reads empty regardless of what was sent. Second: with only the {@code Connection}
	 * handler installed the pipeline holds no encoder, so outbound entries are the raw
	 * packet objects — no unwrapping needed.
	 */
	private static List<Object> drainSoundPackets(
			ServerPlayer player) {
		try {
			Field connectionField = net.minecraft.server.network.ServerCommonPacketListenerImpl.class
					.getDeclaredField("connection");
			connectionField.setAccessible(true);
			Object connection = connectionField.get(player.connection);
			Field channelField = net.minecraft.network.Connection.class.getDeclaredField("channel");
			channelField.setAccessible(true);
			io.netty.channel.embedded.EmbeddedChannel channel =
					(io.netty.channel.embedded.EmbeddedChannel) channelField.get(connection);
			// Pump the embedded loop so writes scheduled by send() from this thread land in
			// the outbound queue before we read it.
			channel.runPendingTasks();
			channel.runScheduledPendingTasks();
			channel.runPendingTasks();
			channel.flush();
			List<Object> heard = new ArrayList<>();
			Object outbound;
			while ((outbound = channel.readOutbound()) != null) {
				heard.add(outbound);
			}
			Object queued;
			java.util.Queue<Object> leftovers = channel.outboundMessages();
			while ((queued = leftovers.poll()) != null) {
				heard.add(queued);
			}
			return heard;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("packet-queue reflection broke", failure);
		}
	}

	/**
	 * Keeps only packets emitted at the spirit's position (isolates the probe sound).
	 * {@code ClientboundSoundPacket} stores x/y/z as <b>floats</b> — at gametest arena
	 * magnitudes (~5.5M blocks) float granularity is 0.5, so exact {@code ==} against the
	 * double position can never match. A 1.0-per-axis tolerance covers the quantization
	 * and still isolates the probe: the control packet sits at 9999.
	 */
	private static List<net.minecraft.network.protocol.game.ClientboundSoundPacket> soundPacketsAt(
			List<Object> packets, CursedSpiritEntity spirit) {
		List<net.minecraft.network.protocol.game.ClientboundSoundPacket> at = new ArrayList<>();
		for (Object outbound : packets) {
			if (outbound instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket packet
					&& Math.abs(packet.getX() - spirit.getX()) < 1.0
					&& Math.abs(packet.getY() - spirit.getY()) < 1.0
					&& Math.abs(packet.getZ() - spirit.getZ()) < 1.0) {
				at.add(packet);
			}
		}
		return at;
	}

	/** Class-name histogram of a raw outbound drain, for failure diagnostics. */
	private static String outboundSummary(List<Object> raw) {
		java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		for (Object outbound : raw) {
			String name = outbound == null ? "null" : outbound.getClass().getName();
			if (outbound instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) {
				name += "@" + p.getX() + "," + p.getY() + "," + p.getZ();
			}
			counts.merge(name, 1, Integer::sum);
		}
		return counts.toString();
	}

	/** {@code Connection.sentPackets} — bumped in {@code sendPacket} before loop dispatch. */
	private static int sentCount(ServerPlayer player) {
		try {
			Field connectionField = net.minecraft.server.network.ServerCommonPacketListenerImpl.class
					.getDeclaredField("connection");
			connectionField.setAccessible(true);
			Object connection = connectionField.get(player.connection);
			Field sentField = net.minecraft.network.Connection.class.getDeclaredField("sentPackets");
			sentField.setAccessible(true);
			return (int) sentField.get(connection);
		} catch (ReflectiveOperationException failure) {
			return -1;
		}
	}

	/**
	 * Send-path diagnostic: {@code Connection.sentPackets} is incremented in
	 * {@code sendPacket} BEFORE the event-loop dispatch, so it separates "send never
	 * fired" (counter still) from "fired but the loopback queue stayed empty". Also
	 * dumps channel state and pipeline handler names.
	 */
	private static String connectionDiag(ServerPlayer player) {
		try {
			Field connectionField = net.minecraft.server.network.ServerCommonPacketListenerImpl.class
					.getDeclaredField("connection");
			connectionField.setAccessible(true);
			Object connection = connectionField.get(player.connection);
			Field channelField = net.minecraft.network.Connection.class.getDeclaredField("channel");
			channelField.setAccessible(true);
			io.netty.channel.embedded.EmbeddedChannel channel =
					(io.netty.channel.embedded.EmbeddedChannel) channelField.get(connection);
			Field sentField = net.minecraft.network.Connection.class.getDeclaredField("sentPackets");
			sentField.setAccessible(true);
			java.util.List<String> handlers = new ArrayList<>();
			for (java.util.Map.Entry<String, io.netty.channel.ChannelHandler> e : channel.pipeline()) {
				handlers.add(e.getKey() + ":" + e.getValue().getClass().getSimpleName());
			}
			return "conn[sent=" + sentField.get(connection) + " open=" + channel.isOpen()
					+ " active=" + channel.isActive() + " registered=" + channel.isRegistered()
					+ " pipeline=" + handlers + "]";
		} catch (ReflectiveOperationException failure) {
			return "conn[diag broke: " + failure + "]";
		}
	}

	private static void cleanup(GameTestHelper helper, CursedSpiritEntity spirit, ServerPlayer victim) {
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, victim);
	}

	/** Server oracle for Step 7: is the victim's connection in the spirit's tracker seenBy. */
	@SuppressWarnings("unchecked")
	private static boolean isTrackedBy(CursedSpiritEntity spirit, ServerPlayer victim) {
		try {
			Object chunkMap = ((ServerLevel) spirit.level()).getChunkSource().chunkMap;
			Field entityMapField = chunkMap.getClass().getDeclaredField("entityMap");
			entityMapField.setAccessible(true);
			Object entityMap = entityMapField.get(chunkMap);
			Object tracked = ((it.unimi.dsi.fastutil.ints.Int2ObjectMap<Object>) entityMap).get(spirit.getId());
			if (tracked == null) {
				return false;
			}
			Field seenByField = tracked.getClass().getDeclaredField("seenBy");
			seenByField.setAccessible(true);
			Set<Object> seenBy = (Set<Object>) seenByField.get(tracked);
			return seenBy.contains(victim.connection);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("tracker oracle reflection broke", failure);
		}
	}
}
