package jujutsu.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.character.todo.TodoTransientState;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Aimed Boogie Woogie (PRIMARY) server scenarios, issue #21 slice 1 — the successful swap and the
 * plain no-target refusal — exercised through the production server route.
 *
 * <p><b>Production invocation.</b> Both scenarios cast through
 * {@code CharacterAbilityExecutor.tryCast(player, CharacterAbility.PRIMARY, true)} — the exact
 * server-side call the C2S receiver {@code JujutsuNetworking.handleCharacterAbility} makes for the
 * PRIMARY input. The receiver itself cannot be invoked from a GameTest: it is private and only the
 * Fabric packet pipeline reaches it, which needs a live client connection. The executor is the next
 * hop after the receiver's vessel-claim gate (a claim every test satisfies by selecting Todo
 * server-side), so the cast exercises hops 1-3 of the real chain — executor gates, TodoDefinition,
 * TodoAbilityRouter, TodoBoogieWoogieRuntime — and the swap itself is fully synchronous inside it.
 *
 * <p><b>Scenario 1</b> proves a successful player↔mob swap: both bodies exchange exact positions,
 * keep their own pre-cast rotations and velocity, get their fall distance reset, the PRIMARY
 * cooldown starts (within its 60-tick window), and the momentum window opens on the caster only.
 * <b>Scenario 2</b> proves the no-target path is a plain behavioral refusal: the real
 * TargetResolver runs against the real world — the caster aims straight up into skyAccess air, the
 * 20-block ray resolves to MISS — the cast returns false, and no state changes: no cooldown, no
 * momentum, no transient state, and the caster's own body untouched. The arena spawns nothing else,
 * so the only entity the ray could have hit is the caster himself, who is not an eligible target;
 * no level entity scan inside the structure bounds is therefore needed — the caster's own state is
 * the complete observable surface.
 *
 * <p><b>No mark fallback.</b> Scenario 2 asserts the refusal contract itself (boolean false plus
 * zero state change) because there is no fallback behaviour to assert against: the old mark/item
 * fallback system was removed in #57, so an unaimed swap is a plain refusal and nothing else.
 * Refusal message keys are deliberately not asserted — they are actionbar text, unobservable on a
 * mock player with no live client.
 *
 * <p>Determinism and hygiene: a fresh mock player per test (random UUID isolates the static
 * cooldown/transient maps), every test cleans up on success AND failure (the tick-2 callback body
 * is wrapped in try/finally calling {@link TodoSwapTestFixtures#cleanupCaster}), all geometry sits
 * inside the default 8x8x8 structure with {@code skyAccess} so aim rays and LOS stay clear, and all
 * positions are helper-relative, converted exactly once.
 */
public final class TodoAimedSwapGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	/**
	 * A successful aimed swap through the production runtime: caster and pig exchange exact
	 * positions, each body keeps its own pre-cast rotation and velocity, fall distance is reset,
	 * the PRIMARY cooldown starts, and the momentum window opens on the caster only — with no
	 * transient state created.
	 *
	 * <p>Swap distance ≈ 5.7 blocks (stations at (1,1,1) and (5,1,5)) — inside the 20-block
	 * production range and inside the structure. The pig spawns AI-less (Stage A
	 * {@code spawnWithNoFreeWill}) and the stations have solid floors, so neither body moves
	 * between capture and assert; the whole swap is synchronous inside one {@code tryCast}, so
	 * capture, cast and asserts share a single scheduled callback.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void successfulSwapExchangesBodiesAndGrantsWindow(GameTestHelper helper) {
		String fixture = "successfulSwapExchangesBodiesAndGrantsWindow";
		BlockPos casterFeet = new BlockPos(1, 1, 1);
		BlockPos pigFeet = new BlockPos(5, 1, 5);
		helper.setBlock(casterFeet.below(), Blocks.STONE);
		helper.setBlock(pigFeet.below(), Blocks.STONE);

		// -45 degrees yaw faces the pig station from (1,1,1); aimAt re-aims at tick 2 anyway.
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, casterFeet, -45.0f, 0.0f);
		Pig pig = GameTestFixtures.spawnMob(helper, fixture, EntityType.PIG, pigFeet);

		helper.runAtTickTime(2, () -> {
			try {
				TodoSwapTestFixtures.BodyState pigBefore = TodoSwapTestFixtures.BodyState.capture(pig);
				// Aim at the centre of the pig's bounding box, keeping the corridor clear of blocks.
				TodoSwapTestFixtures.aimAt(caster, pig.position().add(0.0, pig.getBbHeight() / 2.0, 0.0));
				// Caster state is captured AFTER aiming: production's Snapshot.capture runs inside
				// tryCast with the aim already applied, and the success path restores exactly that
				// snapshot. Aiming only rotates, so position/velocity/fallDistance are unaffected.
				TodoSwapTestFixtures.BodyState casterBefore = TodoSwapTestFixtures.BodyState.capture(caster);
				// Minimal resolve precondition: LOS is implied by the open geometry, but pin it anyway
				// so a bad arena fails here instead of as a confusing resolve miss.
				helper.assertTrue(caster.hasLineOfSight(pig), TodoSwapTestFixtures.diagnostic(fixture,
						"resolve", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"line of sight to pig", "true", caster.hasLineOfSight(pig)));


				boolean swapped = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(swapped, TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"aimed swap cast result", "true", swapped));

				// Exchange happened EXACTLY once: each body now stands where the other stood.
				helper.assertTrue(caster.position().distanceToSqr(pigBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "caster at pig's pre-cast position",
								pigBefore.position(), caster.position()));
				helper.assertTrue(pig.position().distanceToSqr(casterBefore.position())
								<= TodoSwapTestFixtures.POSITION_EPSILON * TodoSwapTestFixtures.POSITION_EPSILON,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "pig at caster's pre-cast position",
								casterBefore.position(), pig.position()));

				// Both bodies stayed in the same dimension.
				helper.assertTrue(caster.level().dimension().equals(pigBefore.dimension()),
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "caster dimension unchanged",
								pigBefore.dimension(), caster.level().dimension()));
				helper.assertTrue(pig.level().dimension().equals(casterBefore.dimension()),
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "pig dimension unchanged",
								casterBefore.dimension(), pig.level().dimension()));

				// Production restores each body to its OWN pre-cast rotation (forceSetRotation +
				// setYHeadRot) and velocity, so the asserts are exact.
				helper.assertTrue(caster.getYRot() == casterBefore.yaw(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"caster yaw restored", casterBefore.yaw(), caster.getYRot()));
				helper.assertTrue(caster.getXRot() == casterBefore.pitch(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"caster pitch restored", casterBefore.pitch(), caster.getXRot()));
				helper.assertTrue(caster.getYHeadRot() == casterBefore.headYaw(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"caster headYaw restored", casterBefore.headYaw(), caster.getYHeadRot()));
				helper.assertTrue(pig.getYRot() == pigBefore.yaw(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"pig yaw restored", pigBefore.yaw(), pig.getYRot()));
				helper.assertTrue(pig.getXRot() == pigBefore.pitch(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"pig pitch restored", pigBefore.pitch(), pig.getXRot()));
				helper.assertTrue(pig.getYHeadRot() == pigBefore.headYaw(), TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"pig headYaw restored", pigBefore.headYaw(), pig.getYHeadRot()));
				helper.assertTrue(caster.getDeltaMovement().equals(casterBefore.velocity()),
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "caster velocity preserved",
								casterBefore.velocity(), caster.getDeltaMovement()));
				helper.assertTrue(pig.getDeltaMovement().equals(pigBefore.velocity()),
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "pig velocity preserved",
								pigBefore.velocity(), pig.getDeltaMovement()));

				// Production resets fall distance on both bodies.
				helper.assertTrue(caster.fallDistance == 0.0F, TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"caster fall distance reset", "0.0", caster.fallDistance));
				helper.assertTrue(pig.fallDistance == 0.0F, TodoSwapTestFixtures.diagnostic(fixture,
						"commit", helper.getTick(), caster.getUUID(), pig.getUUID(),
						"pig fall distance reset", "0.0", pig.fallDistance));

				// The PRIMARY cooldown started (within its 60-tick window) and momentum opened
				// on the caster only — the pig is a bystander and must not carry the effect.
				int remaining = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(remaining > 0 && remaining <= TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS,
						TodoSwapTestFixtures.diagnostic(fixture, "commit", helper.getTick(),
								caster.getUUID(), pig.getUUID(), "PRIMARY cooldown started",
								"in (0, " + TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS + "]", remaining));
				boolean momentum = caster.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
				helper.assertTrue(momentum, TodoSwapTestFixtures.diagnostic(fixture, "commit",
						helper.getTick(), caster.getUUID(), pig.getUUID(),
						"caster momentum window open", "true", momentum));
				boolean pigMomentum = pig.hasEffect(JujutsuEffects.TODO_SWAP_MOMENTUM);
				helper.assertTrue(!pigMomentum, TodoSwapTestFixtures.diagnostic(fixture, "commit",
						helper.getTick(), caster.getUUID(), pig.getUUID(),
						"pig without momentum", "false", pigMomentum));

				// The swap creates no transient state.
				boolean transientState = TodoTransientState.owners().contains(caster.getUUID());
				helper.assertTrue(!transientState, TodoSwapTestFixtures.diagnostic(fixture, "commit",
						helper.getTick(), caster.getUUID(), pig.getUUID(),
						"no transient state", "false", transientState));
			} finally {
				// Success AND failure: an assert throw has already failed the test; cleanup is
				// best-effort so the leaked-player vector is closed even on red runs.
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
			}
		});
		// Pig teardown: discard at tick 6, absence verified at tick 16 (Stage A idiom). The
		// caster's cleanup already ran inside the tick-2 callback's finally.
		GameTestFixtures.removeAndVerifyGone(helper, fixture, pig, EntityType.PIG, pigFeet, 6);
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * Aiming into empty sky is a plain refusal: the real TargetResolver runs (the ray from the
	 * caster's eye at pitch -90 exits skyward through 20 blocks of skyAccess air and resolves to
	 * MISS), {@code tryCast} returns false, and nothing changes — the caster's body is untouched
	 * field for field, and no cooldown, momentum or transient state is created.
	 *
	 * <p>The arena spawns nothing else, so the only possible target was the caster himself, who is
	 * never eligible; the caster's own state is therefore the complete observable surface and no
	 * structure-bounds entity scan is needed. Refusals never charge the cooldown (production starts
	 * it only past the last {@code return false}), which this scenario pins.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void noTargetRefusalChargesNothing(GameTestHelper helper) {
		String fixture = "noTargetRefusalChargesNothing";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		helper.setBlock(casterFeet.below(), Blocks.STONE);

		// Pitch -90 aims straight up: the 20-block resolver ray stays in skyAccess air.
		ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, casterFeet, 0.0f, -90.0f);

		helper.runAtTickTime(2, () -> {
			try {
				TodoSwapTestFixtures.BodyState before = TodoSwapTestFixtures.BodyState.capture(caster);
				boolean cast = TodoSwapTestFixtures.castPrimary(caster);
				helper.assertTrue(!cast, TodoSwapTestFixtures.diagnostic(fixture,
						"resolve", helper.getTick(), caster.getUUID(), null,
						"no-target cast result", "false", cast));
				// Exact-field proof that the refusal moved nothing.
				TodoSwapTestFixtures.assertBodyState(helper, fixture, "resolve", "caster", caster, before);
				// Refusal contract: no cooldown, no momentum, no transient state.
				TodoSwapTestFixtures.assertNoPrimaryCharge(helper, fixture, "resolve", caster);
			} finally {
				TodoSwapTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}
}
