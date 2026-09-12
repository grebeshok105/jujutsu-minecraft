package jujutsu.mod.gametest;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;

/**
 * Shared fixtures for the cursed-spirit server scenarios (Block 2, Step 10).
 *
	 * acquires players): non-spectator, non-creative, non-invulnerable, inside follow range with line
	 * of sight. Mobile attackers are spawned WITH AI ({@code spawnWithNoFreeWill} plus
	 * {@code setNoAi(false)} — a NoAI body never moves, so displacement/velocity oracles would be
	 * vacuous); stationary bodies use AI plus Slowness 100 (full AI keeps physics while self-motion
	 * is removed). Difficulty is forced NORMAL per scenario: on PEACEFUL, {@code Monster} bodies
	 * discard themselves.
	 */
	public final class CursedSpiritTestFixtures {
		private CursedSpiritTestFixtures() {}

	/**
	 * Failure message naming the fixture, tick, and expected vs actual state. The
	 * {@code context} slot (usually a UUID, may be null) mirrors the Todo fixture shape so
	 * call sites read the same.
	 */
	public static Component diagnostic(String fixture, long tick, Object context, String what,
			Object expected, Object actual) {
		return Component.literal("[" + fixture + " @tick " + tick + " ctx=" + context + "] " + what
				+ ": expected <" + expected + ">, actual <" + actual + ">");
	}

	/** Same without a context slot. */
	public static Component diagnostic(String fixture, long tick, String what, Object expected,
			Object actual) {
		return diagnostic(fixture, tick, (Object) null, what, expected, actual);
	}

	/** Todo-shaped call sites (caster/target pair) shared by the integration scenarios. */
	public static Component diagnostic(String fixture, long tick, java.util.UUID caster,
			java.util.UUID target, String what, Object expected, Object actual) {
		return diagnostic(fixture, tick, (Object) ("caster=" + caster + " target=" + target), what,
				expected, actual);
	}
	public static void layStoneFloor(GameTestHelper helper) {
		for (int dx = 1; dx <= 6; dx++) {
			for (int dz = 1; dz <= 6; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}

	/** Peaceful discards Monster bodies; the scenarios need them hunting. Idempotent. */
	public static void ensureHostileDifficulty(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		if (helper.getLevel().getDifficulty() == Difficulty.PEACEFUL) {
			server.setDifficulty(Difficulty.NORMAL, true);
		}
	}

	/**
	 * Creates the victim: a mock player teleported to the centre of the relative block, forced to
	 * SURVIVAL with a full health bar, then premise-asserted (alive, neither spectator nor
	 * creative, abilities not invulnerable).
	 */
	public static ServerPlayer setupVictim(GameTestHelper helper, String fixture, BlockPos relativeFeet) {
		ServerPlayer victim = helper.makeMockServerPlayerInLevel();
		try {
			victim.teleportTo(helper.getLevel(), helper.absolutePos(relativeFeet).getX() + 0.5,
					helper.absolutePos(relativeFeet).getY(), helper.absolutePos(relativeFeet).getZ() + 0.5,
					Set.of(), 0.0f, 0.0f, false);
		// Mocks arrive CREATIVE and ServerPlayer.setGameMode is a no-op on them (returns true,
		// gameMode() stays CREATIVE — observed). Flip through the public interaction manager
		// instead: acquisition excludes creative players, so the victim must read SURVIVAL.
		victim.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		victim.getAbilities().invulnerable = false;
		victim.getAbilities().mayfly = false;
		victim.setHealth(victim.getMaxHealth());
		long tick = helper.getTick();
		helper.assertTrue(victim.isAlive(), diagnostic(fixture, tick,
				"victim alive", "true", victim.isAlive()));
		helper.assertTrue(!victim.isSpectator(), diagnostic(fixture, tick,
				"victim not spectator", "false", victim.isSpectator()));
		helper.assertTrue(!victim.isCreative(), diagnostic(fixture, tick,
				"victim reads SURVIVAL", "false", victim.isCreative()));
		helper.assertTrue(!victim.getAbilities().invulnerable, diagnostic(fixture, tick,
				"victim not invulnerable", "false", victim.getAbilities().invulnerable));
		helper.assertTrue(helper.getLevel().getDifficulty() != Difficulty.PEACEFUL,
				diagnostic(fixture, tick, "difficulty hostile", "!= PEACEFUL",
						helper.getLevel().getDifficulty()));
			return victim;
		} catch (RuntimeException | AssertionError failure) {
			cleanupVictim(helper, victim);
			throw failure;
		}
	}

	/** Removes a mock victim from the player list. Never throws. */
	public static void cleanupVictim(GameTestHelper helper, ServerPlayer victim) {
		try {
			helper.getLevel().getServer().getPlayerList().remove(victim);
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}

	/**
	 * Spawns a spirit WITH AI (re-enables it after the deterministic spawn): the mobile-attacker
	 * scenarios must never use a NoAI body. Persists through the scenario (no peaceful-despawn,
	 * no distance-despawn mid-arena).
	 */
	public static CursedSpiritEntity spawnSpirit(GameTestHelper helper, String fixture,
			EntityType<CursedSpiritEntity> type, BlockPos relativePos) {
		CursedSpiritEntity spirit =
				GameTestFixtures.spawnMob(helper, fixture, type, relativePos);
		spirit.setPersistenceRequired();
		spirit.setNoAi(false);
		helper.assertTrue(spirit.variant().tier() != null, diagnostic(fixture, helper.getTick(),
				"spawned variant tier-valid", "non-null tier", spirit.variant()));
		return spirit;
	}

	/** AI body with zeroed self-motion: goals still tick and strike, but the body holds position. */
	public static void freezeGround(Mob mob) {
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10000, 100, false, false, false));
	}
}
