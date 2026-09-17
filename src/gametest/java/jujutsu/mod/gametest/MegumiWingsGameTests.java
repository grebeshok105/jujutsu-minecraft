package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.megumi.MegumiNueWings;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Nue's partial wings (issue #108), block B3 server scenarios: the glide is vanilla fall-flying
 * granted by {@code MEGUMI_NUE_WINGS}, so these cases pin the physics contract (§21-§23) rather than
 * any custom velocity code — no vertical thrust (R25), a strictly slower sink than free fall (R26),
 * and the fall-damage waiver with its manual-off restore (R28), plus the server-side
 * {@code isFallFlying()} premise the whole feature rests on.
 *
 * <p><b>Harness facts these cases depend on.</b> Server players run their own movement
 * ({@code Player.canSimulateMovement}/{@code isEffectiveAi} are true server-side), so a mock player
 * really glides and really falls. A mock player never receives a client load, and
 * {@code ServerPlayer.isInvulnerableTo} denies everything until {@code hasClientLoaded()}, which
 * makes the mock the damage-free half of the R26 comparison; the R28 damage oracle uses a real
 * {@code setupVictim} player instead, exactly as the gameplay path does.
 *
 * <p><b>Traps avoided.</b> The arena is the 8³ default template, so the drop column starts above
 * the template bounds — allowed for teleports and required for a 24-block fall; nothing asserts an
 * absolute position (the world offset is random per run). {@code fallDistance} is driven by hand
 * through {@code Entity.doCheckFallDamage}: a clientless player never accumulates it, and the
 * packet-path primitive is the same hop the real fall takes. That check also skips air below the
 * feet, so the damage oracle runs with the body standing on the pad. R25's monotonic window starts
 * after the entry transient on purpose — vanilla glide damps an existing fast fall, so only the
 * post-equilibrium window answers "is there upward thrust".
 *
 * <p>This class is registered by the integration owner ({@code fabric.mod.json}
 * {@code fabric-gametest} entrypoint list); {@link MegumiNueWings#register()} is called here so the
 * event wiring is under test even before the production hook entry ships — both listeners are
 * stateless and agree, so the duplicate registration is inert.
 */
public final class MegumiWingsGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	/** Pad top surface sits at relative y = 1; the drop column starts 24 blocks above it. */
	private static final BlockPos PAD_BLOCK = new BlockPos(2, 0, 2);
	private static final int PAD_TOP_Y = 1;
	private static final int DROP_HEIGHT_BLOCKS = 24;
	private static final BlockPos GLIDER_FEET = new BlockPos(2, PAD_TOP_Y + DROP_HEIGHT_BLOCKS, 2);
	private static final BlockPos CONTROL_FEET = new BlockPos(4, PAD_TOP_Y + DROP_HEIGHT_BLOCKS, 4);

	private static final int ACTIVATE_TICK = 2;
	private static final int OBSERVATION_TICKS = 40;
	/** Equilibrium window: the glide recurrence has settled, so "never rises" is a real claim. */
	private static final int EQUILIBRIUM_TICK = 20;
	private static final int LAST_SAMPLE_TICK = ACTIVATE_TICK + OBSERVATION_TICKS;
	private static final double VELOCITY_EPSILON = 1.0e-6;
	/** Free fall covers ~24 blocks in well under 40 ticks; the glide must stay far above the pad. */
	private static final double WINGS_HEIGHT_ADVANTAGE_BLOCKS = 6.0;
	private static final int EFFECT_TICKS = 400;
	/** 12 blocks of fall: (12 - 3) = 9 damage, survivable for a 20-HP player. */
	private static final double FALL_BLOCKS = 12.0;

	/**
	 * R25 — wings are a glider, not flight: over the post-equilibrium window {@code
	 * deltaMovement().y} never rises and never turns positive, while the body keeps sinking. The
	 * effect is applied before the drop and the velocity is zeroed at the flap, so the start is
	 * pinned instead of inherited.
	 */
	@GameTest(maxTicks = 100)
	public void wingsGlideNeverAddsUpwardThrust(GameTestHelper helper) {
		String fixture = "wingsGlideNeverAddsUpwardThrust";
		MegumiNueWings.register();
		helper.setBlock(PAD_BLOCK, Blocks.STONE);

		ServerPlayer glider = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, PAD_BLOCK.above(), 0.0f, 0.0f);
		List<Double> velocities = new ArrayList<>();
		List<Double> heights = new ArrayList<>();

		helper.runAtTickTime(ACTIVATE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, glider, () -> {
			dropInto(helper, glider, GLIDER_FEET);
			grantWings(glider);
			helper.assertTrue(!glider.getAbilities().flying, diagnostic(fixture, "activate", helper, glider,
					"creative flight is not involved", "false", glider.getAbilities().flying));
			helper.assertTrue(glider.tryToStartFallFlying(), diagnostic(fixture, "activate", helper, glider,
					"tryToStartFallFlying result", "true", "false"));
			helper.assertTrue(glider.isFallFlying(), diagnostic(fixture, "activate", helper, glider,
					"isFallFlying server-side", "true", glider.isFallFlying()));
			velocities.add(glider.getDeltaMovement().y);
			heights.add(glider.getY());
		}));

		for (int tick = ACTIVATE_TICK + 1; tick <= LAST_SAMPLE_TICK; tick++) {
			helper.runAtTickTime(tick, () -> MegumiShikigamiTestFixtures.runGuarded(helper, glider, () -> {
				velocities.add(glider.getDeltaMovement().y);
				heights.add(glider.getY());
			}));
		}

		helper.runAtTickTime(LAST_SAMPLE_TICK + 1, () -> {
			try {
				int samples = velocities.size();
				helper.assertTrue(samples == OBSERVATION_TICKS + 1, diagnostic(fixture, "window", helper, glider,
						"velocity samples collected", OBSERVATION_TICKS + 1, samples));
				helper.assertTrue(glider.isFallFlying(), diagnostic(fixture, "window", helper, glider,
						"still gliding at the end of the window", "true", glider.isFallFlying()));

				for (int index = EQUILIBRIUM_TICK; index < samples; index++) {
					double previous = velocities.get(index - 1);
					double current = velocities.get(index);
					helper.assertTrue(current <= previous + VELOCITY_EPSILON,
							diagnostic(fixture, "window", helper, glider,
									"deltaMovement.y must never rise (sample " + index + ")",
									"<= " + previous, current));
					helper.assertTrue(current <= 0.0, diagnostic(fixture, "window", helper, glider,
							"deltaMovement.y stays non-positive (sample " + index + ")", "<= 0", current));
				}

				double lastVelocity = velocities.get(samples - 1);
				double lastHeight = heights.get(samples - 1);
				double startHeight = heights.get(0);
				helper.assertTrue(lastVelocity < 0.0, diagnostic(fixture, "window", helper, glider,
						"the glide still sinks (never hovers)", "< 0", lastVelocity));
				helper.assertTrue(lastHeight < startHeight - 1.0, diagnostic(fixture, "window", helper, glider,
						"altitude lost over the window", "< " + (startHeight - 1.0), lastHeight));
				helper.assertTrue(lastHeight > helper.absolutePos(PAD_BLOCK).getY() + PAD_TOP_Y + 2.0,
						diagnostic(fixture, "window", helper, glider, "still airborne at the end of the window",
								"> " + (PAD_TOP_Y + 2.0), lastHeight - helper.absolutePos(PAD_BLOCK).getY()));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, glider);
			}
			helper.succeed();
		});
	}

	/**
	 * R26 — identical start, same tick, same height: after 40 ticks the glider is strictly higher
	 * than the free-falling control, and the control is on the pad (so the comparison cannot pass
	 * because nothing moved).
	 */
	@GameTest(maxTicks = 100)
	public void wingsReduceSinkVersusFreeFall(GameTestHelper helper) {
		String fixture = "wingsReduceSinkVersusFreeFall";
		MegumiNueWings.register();
		for (int dx = 1; dx <= 5; dx++) {
			for (int dz = 1; dz <= 5; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}

		ServerPlayer glider = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, PAD_BLOCK.above(), 0.0f, 0.0f);
		ServerPlayer control = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, PAD_BLOCK.above(), 0.0f, 0.0f);

		helper.runAtTickTime(ACTIVATE_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, glider, () -> {
			dropInto(helper, glider, GLIDER_FEET);
			dropInto(helper, control, CONTROL_FEET);
			helper.assertTrue(Math.abs(glider.getY() - control.getY()) < 1.0e-9,
					diagnostic(fixture, "activate", helper, glider, "identical start height",
							control.getY(), glider.getY()));
			grantWings(glider);
			helper.assertTrue(glider.tryToStartFallFlying(), diagnostic(fixture, "activate", helper, glider,
					"glider tryToStartFallFlying result", "true", "false"));
			helper.assertTrue(!control.tryToStartFallFlying(), diagnostic(fixture, "activate", helper, control,
					"control tryToStartFallFlying result (no wings)", "false", "true"));
		}));

		helper.runAtTickTime(LAST_SAMPLE_TICK, () -> {
			try {
				double gliderY = glider.getY();
				double controlY = control.getY();
				double padTopY = helper.absolutePos(PAD_BLOCK).getY() + PAD_TOP_Y;
				helper.assertTrue(glider.isFallFlying(), diagnostic(fixture, "compare", helper, glider,
						"glider still fall flying", "true", glider.isFallFlying()));
				helper.assertTrue(!control.isFallFlying(), diagnostic(fixture, "compare", helper, control,
						"control never fall flying", "false", control.isFallFlying()));
				helper.assertTrue(gliderY > controlY + WINGS_HEIGHT_ADVANTAGE_BLOCKS,
						diagnostic(fixture, "compare", helper, glider,
								"glider height over control", "> " + WINGS_HEIGHT_ADVANTAGE_BLOCKS,
								gliderY - controlY));
				helper.assertTrue(controlY <= padTopY + 0.01, diagnostic(fixture, "compare", helper, control,
						"control reached the pad", "<= " + padTopY, controlY));
				helper.assertTrue(gliderY > padTopY + WINGS_HEIGHT_ADVANTAGE_BLOCKS,
						diagnostic(fixture, "compare", helper, glider,
								"glide sinks strictly less than free fall",
								"> " + (padTopY + WINGS_HEIGHT_ADVANTAGE_BLOCKS), gliderY));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, glider);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, control);
			}
			helper.succeed();
		});
	}

	/**
	 * R28 — the fall-damage waiver is bound to the marker: standing on the pad, the packet-path
	 * fall check leaves health untouched with the wings on and bites once they are removed; then,
	 * airborne, the real player cannot start the glide without the marker and can with it.
	 */
	@GameTest(maxTicks = 100)
	public void fallDamageWaivedWhileWingsActiveAndRestoredWhenOff(GameTestHelper helper) {
		String fixture = "fallDamageWaivedWhileWingsActiveAndRestoredWhenOff";
		MegumiNueWings.register();
		helper.setBlock(PAD_BLOCK, Blocks.STONE);

		ServerPlayer victim = CursedSpiritTestFixtures.setupVictim(helper, fixture, PAD_BLOCK.above());
		try {
			// Wings on: the ALLOW_DAMAGE veto swallows the fall whole.
			grantWings(victim);
			helper.assertTrue(victim.getHealth() == victim.getMaxHealth(), diagnostic(fixture, "waiver", helper,
					victim, "full health before the waived fall", victim.getMaxHealth(), victim.getHealth()));
			victim.fallDistance = FALL_BLOCKS;
			victim.doCheckFallDamage(0.0, -1.0, 0.0, true);
			helper.assertTrue(victim.getHealth() == victim.getMaxHealth(),
					diagnostic(fixture, "waiver", helper, victim, "health after the wings-on fall",
							victim.getMaxHealth(), victim.getHealth()));
			helper.assertTrue(victim.isAlive(), diagnostic(fixture, "waiver", helper, victim,
					"victim alive after the waived fall", "true", victim.isAlive()));

			// Wings off mid-air: the same fall bites.
			victim.removeEffect(JujutsuEffects.MEGUMI_NUE_WINGS);
			victim.invulnerableTime = 0;
			victim.fallDistance = FALL_BLOCKS;
			victim.doCheckFallDamage(0.0, -1.0, 0.0, true);
			helper.assertTrue(victim.getHealth() < victim.getMaxHealth(),
					diagnostic(fixture, "restore", helper, victim, "health after the wings-off fall",
							"< " + victim.getMaxHealth(), victim.getHealth()));
			helper.assertTrue(victim.isAlive(), diagnostic(fixture, "restore", helper, victim,
					"victim survives the restore oracle", "true", victim.isAlive()));

			// The glide grant itself: airborne and wingless the flap is refused, with the marker it is not.
			victim.setHealth(victim.getMaxHealth());
			dropInto(helper, victim, GLIDER_FEET);
			helper.assertTrue(!victim.tryToStartFallFlying(), diagnostic(fixture, "glide", helper, victim,
					"tryToStartFallFlying without wings (airborne)", "false", "true"));
			helper.assertTrue(!victim.isFallFlying(), diagnostic(fixture, "glide", helper, victim,
					"isFallFlying without wings", "false", victim.isFallFlying()));
			grantWings(victim);
			helper.assertTrue(victim.tryToStartFallFlying(), diagnostic(fixture, "glide", helper, victim,
					"real player tryToStartFallFlying with wings", "true", "false"));
			helper.assertTrue(victim.isFallFlying(), diagnostic(fixture, "glide", helper, victim,
					"real player isFallFlying server-side", "true", victim.isFallFlying()));
		} finally {
			CursedSpiritTestFixtures.cleanupVictim(helper, victim);
		}
		helper.succeed();
	}

	/** Places the body in the drop column: airborne latch cleared and velocity pinned to zero. */
	private static void dropInto(GameTestHelper helper, ServerPlayer player, BlockPos relativeFeet) {
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(relativeFeet);
		player.teleportTo(level, absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				Set.of(), 0.0f, 0.0f, false);
		player.setDeltaMovement(Vec3.ZERO);
		// A teleport keeps the old on-ground latch; canGlide() reads it, so the flap needs it cleared.
		player.setOnGroundWithMovement(false, Vec3.ZERO);
	}

	private static void grantWings(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(JujutsuEffects.MEGUMI_NUE_WINGS, EFFECT_TICKS, 0, false, false, false), player);
	}

	private static Component diagnostic(String fixture, String phase, GameTestHelper helper,
			ServerPlayer player, String what, Object expected, Object actual) {
		return MegumiShikigamiTestFixtures.diagnostic(fixture, phase, helper.getTick(), player.getUUID(),
				what, expected, actual);
	}
}
