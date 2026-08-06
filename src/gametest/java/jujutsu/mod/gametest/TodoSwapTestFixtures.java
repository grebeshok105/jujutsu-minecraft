package jujutsu.mod.gametest;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.todo.TodoTransientState;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Shared fixtures for the aimed Boogie Woogie (PRIMARY) server scenarios, issue #21 slice 1.
 * FROZEN contract: the scenario classes (the success/refusal tests and the rollback tests)
 * compile against exactly these members.
 *
 * <p>The Todo caster is {@link GameTestHelper#makeMockServerPlayerInLevel()}: a real registered
 * {@code ServerPlayer} with a loopback {@code EmbeddedChannel} connection and the full
 * {@code PlayerList.placeNewPlayer} lifecycle, so every production side effect (packets, effects,
 * broadcasts) is connection-safe. Cleanup goes through {@code PlayerList.remove} — never
 * {@code discard()}, which would leave the player registered in the player list.
 *
 * <p>Assertions are driven through the helper with the extended diagnostic format
 * ({@link #diagnostic}), so a red run names the fixture, phase, tick and parties in the JUnit
 * report.
 */
public final class TodoSwapTestFixtures {

	/** Position tolerance after an authoritative teleport (setPosRaw is exact; double math slack only). */
	public static final double POSITION_EPSILON = 1.0E-6D;

	private TodoSwapTestFixtures() {}

	/**
	 * Full body state for exact-field asserts. Captured BEFORE the cast; compared AFTER.
	 *
	 * <p>Fall distance is deliberately part of the record even though production does not snapshot
	 * it: the production contract is that a swap (success or rollback) resets it to zero, and the
	 * scenarios assert that contract against this capture.
	 */
	public record BodyState(Vec3 position, Vec3 velocity, float yaw, float pitch, float headYaw,
			double fallDistance, ResourceKey<Level> dimension, boolean alive, boolean removed) {
		public static BodyState capture(LivingEntity body) {
			return new BodyState(body.position(), body.getDeltaMovement(), body.getYRot(), body.getXRot(),
					body.getYHeadRot(), body.fallDistance, body.level().dimension(), body.isAlive(),
					body.isRemoved());
		}
	}

	/** Extended Stage A diagnostic: [fixture/phase @tick N caster=<uuid> target=<uuid>] what: expected <e>, actual <a>. UUIDs may be null pre-setup. */
	public static Component diagnostic(String fixture, String phase, long tick,
			UUID caster, UUID target, String what, Object expected, Object actual) {
		return Component.literal("[" + fixture + "/" + phase + " @tick " + tick
				+ " caster=" + caster + " target=" + target + "] " + what
				+ ": expected <" + expected + ">, actual <" + actual + ">");
	}

	/**
	 * Creates the Todo caster: helper.makeMockServerPlayerInLevel(), teleports it to the ABSOLUTE
	 * position derived from relativeFeet with the given rotation (8-arg teleportTo, Set.of()),
	 * selects TODO via CharacterSelectionManager.select, clears the PRIMARY cooldown (AFTER select),
	 * removes any momentum effect, then asserts preconditions with diagnostics (alive, not spectator,
	 * both hands empty, PRIMARY cooldown 0, no momentum, no transient state for the UUID).
	 *
	 * <p>The teleport lands the body at the centre of the relative block (x + 0.5, z + 0.5), the
	 * canonical "standing in a block" spot that keeps destination geometry — the rollback test's
	 * alcove ring candidates in particular — symmetric around the caster.
	 */
	public static ServerPlayer setupTodoCaster(GameTestHelper helper,
			String fixture, BlockPos relativeFeet, float yaw, float pitch) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos absolute = helper.absolutePos(relativeFeet);
			caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
					Set.of(), yaw, pitch, false);
			// The cooldown key resolves the vessel from the current selection, so select() MUST run first.
			CharacterSelectionManager.select(caster, JujutsuCharacter.TODO);
			CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
			caster.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);

			long tick = helper.getTick();
			helper.assertTrue(caster.isAlive(), diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"caster alive", "true", caster.isAlive()));
			helper.assertTrue(!caster.isSpectator(), diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"caster not spectator", "false", caster.isSpectator()));
			boolean handsEmpty = caster.getMainHandItem().isEmpty() && caster.getOffhandItem().isEmpty();
			helper.assertTrue(handsEmpty, diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"both hands empty", "true", handsEmpty));
			int cooldown = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
			helper.assertTrue(cooldown == 0, diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"PRIMARY cooldown clear", "0", cooldown));
			boolean momentum = caster.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
			helper.assertTrue(!momentum, diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"no momentum effect", "false", momentum));
			boolean transientState = TodoTransientState.owners().contains(caster.getUUID());
			helper.assertTrue(!transientState, diagnostic(fixture, "setup", tick, caster.getUUID(), null,
					"no transient state", "false", transientState));
			return caster;
		} catch (RuntimeException | AssertionError failure) {
			// A partially set-up player must not leak into the shared player list when a precondition
			// assert fails; teardown is best-effort and the original failure is rethrown.
			cleanupCaster(helper, caster);
			throw failure;
		}
	}

	/**
	 * Aims the caster at an exact world point by writing the server-side rotation fields directly.
	 *
	 * <p>NOT {@code forceSetRotation}: the {@code ServerPlayer} override of that method only sends
	 * a {@code ClientboundPlayerRotationPacket} to the client and never touches the authoritative
	 * fields (bytecode-verified) — on a loopback mock the packet goes nowhere and the aim would be
	 * lost. {@code TargetResolver} reads {@code getLookAngle()} = f(xRot, yRot), so those two are
	 * the load-bearing fields; head yaw is set too for the {@code getViewVector} family. The yaw
	 * formula is the inverse of vanilla's getLookAngle convention: {@code yaw = atan2(-d.x, d.z)},
	 * {@code pitch = -atan2(d.y, |d.xz|)}, with {@code d} the unit vector from the caster's eye to
	 * the target point.
	 */
	public static void aimAt(ServerPlayer caster, Vec3 absoluteTargetPoint) {
		Vec3 d = absoluteTargetPoint.subtract(caster.getEyePosition()).normalize();
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		caster.setYRot(yaw);
		caster.setXRot(pitch);
		caster.setYHeadRot(yaw);
	}

	/** THE production invocation: CharacterAbilityExecutor.tryCast(caster, CharacterAbility.PRIMARY, true). */
	public static boolean castPrimary(ServerPlayer caster) {
		return jujutsu.mod.character.CharacterAbilityExecutor.tryCast(caster, CharacterAbility.PRIMARY, true);
	}

	/**
	 * Asserts every BodyState field matches (position within epsilon; exact for the rest),
	 * diagnostics per field with the phase. The asserted body's UUID rides the caster slot of the
	 * diagnostic (the helper checks one body at a time; the bodyName label says which one it is).
	 */
	public static void assertBodyState(GameTestHelper helper, String fixture, String phase,
			String bodyName, LivingEntity body, BodyState expected) {
		long tick = helper.getTick();
		java.util.UUID uuid = body.getUUID();
		Vec3 actualPosition = body.position();
		boolean positionOk = actualPosition.distanceToSqr(expected.position())
				<= POSITION_EPSILON * POSITION_EPSILON;
		helper.assertTrue(positionOk, diagnostic(fixture, phase, tick, uuid, null,
				bodyName + " position", expected.position(), actualPosition));
		helper.assertTrue(body.getDeltaMovement().equals(expected.velocity()),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " velocity", expected.velocity(), body.getDeltaMovement()));
		helper.assertTrue(body.getYRot() == expected.yaw(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " yaw", expected.yaw(), body.getYRot()));
		helper.assertTrue(body.getXRot() == expected.pitch(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " pitch", expected.pitch(), body.getXRot()));
		helper.assertTrue(body.getYHeadRot() == expected.headYaw(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " headYaw", expected.headYaw(), body.getYHeadRot()));
		helper.assertTrue(body.fallDistance == expected.fallDistance(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " fallDistance", expected.fallDistance(), body.fallDistance));
		helper.assertTrue(body.level().dimension().equals(expected.dimension()),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " dimension", expected.dimension(), body.level().dimension()));
		helper.assertTrue(body.isAlive() == expected.alive(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " alive", expected.alive(), body.isAlive()));
		helper.assertTrue(body.isRemoved() == expected.removed(),
				diagnostic(fixture, phase, tick, uuid, null,
						bodyName + " removed", expected.removed(), body.isRemoved()));
	}

	/** Asserts PRIMARY remainingTicks == 0, no momentum effect, and TodoTransientState has no entry for the caster. */
	public static void assertNoPrimaryCharge(GameTestHelper helper, String fixture, String phase,
			ServerPlayer caster) {
		long tick = helper.getTick();
		int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
		helper.assertTrue(remaining == 0, diagnostic(fixture, phase, tick, caster.getUUID(), null,
				"PRIMARY cooldown uncharged", "0", remaining));
		boolean momentum = caster.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
		helper.assertTrue(!momentum, diagnostic(fixture, phase, tick, caster.getUUID(), null,
				"no momentum effect", "false", momentum));
		boolean transientState = TodoTransientState.owners().contains(caster.getUUID());
		helper.assertTrue(!transientState, diagnostic(fixture, phase, tick, caster.getUUID(), null,
				"no transient state", "false", transientState));
	}

	/**
	 * Cleanup (success AND failure paths): clear PRIMARY cooldown, remove momentum,
	 * TodoTransientState.dropAll, PlayerList.remove(caster). Never throws.
	 *
	 * <p>Every step is isolated so a teardown exception cannot mask the failure it is cleaning up
	 * after; the original assert failure is the evidence. PlayerList.remove is the full disconnect
	 * path — it unregisters the player from both the level and the player list, which is what keeps
	 * the mock from ticking or being iterated by later tests in the same server run.
	 */
	public static void cleanupCaster(GameTestHelper helper, ServerPlayer caster) {
		MinecraftServer server = helper.getLevel().getServer();
		safe(() -> CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY));
		safe(() -> caster.removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM));
		safe(() -> TodoTransientState.dropAll(server, caster.getUUID()));
		safe(() -> server.getPlayerList().remove(caster));
	}

	private static void safe(Runnable step) {
		try {
			step.run();
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}
}
