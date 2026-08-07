package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.combat.CombatStagger;

/**
 * The tri-state ability-result contract (issue #19), exercised through the production server route.
 *
 * <p><b>What is under test.</b> Nobara's runtime messages must survive her router: a runtime that
 * already told the player why (HANDLED_FAILURE) must never be overwritten by the generic no-target
 * fallback, while a genuinely silent failure (UNHANDLED_FAILURE) must still produce exactly that
 * fallback. Five scenarios pin the message stream for the five distinguishable failure routes:
 * the two trap refusals, the self-resonance no-link refusal, the generic fallback, and the
 * cooldown-before-stagger gate ordering.
 *
 * <p><b>Message capture.</b> The scenarios use {@link MessageRecordingPlayer}, a real
 * {@code ServerPlayer} whose {@link #displayClientMessage(Component, boolean)} override records the
 * translation key of everything the production code displays instead of sending it to a client.
 * Messages are asserted as the exact recorded list — one write, the specific key, never a second
 * no-target line — so "the router adds nothing" is measured, not assumed.
 *
 * <p><b>Why no player-list registration.</b> The mock is constructed directly (same constructor
 * shape as {@code GameTestHelper$2}, bytecode-verified) and never placed:
 * {@link CharacterSelectionManager#select} broadcasts only to player-list members (safe), and every
 * scenario exercises a refusal path whose only side effect is {@code displayClientMessage} — no
 * packet, VFX cue, sound or entity is produced on these routes, so no connection is needed. The
 * player does not tick (not in the level's entity list), so position and rotation set at setup stay
 * exact. Cleanup clears the cooldown entries the scenarios seeded; everything else the casts could
 * touch (stagger, transient maps) is UUID-scoped and never written on these refusal paths.
 */
public final class NobaraAbilityResultGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	/** The trap refusal that means "the aim ray found no surface". */
	private static final String TRAP_NO_GROUND = "message.jujutsumod.nobara.trap.no_ground";
	/** The trap refusal that means "not enough hairpin nails in the inventory". */
	private static final String TRAP_NO_NAILS = "message.jujutsumod.nobara.trap.no_nails";
	/** The self-resonance refusal that means "no curse link to resonate". */
	private static final String RESONANCE_NO_LINK = "message.jujutsumod.nobara.self_resonance.no_link";
	/** The router's generic fallback, gated on UNHANDLED_FAILURE. */
	private static final String NO_TARGET = "message.jujutsumod.nobara.action.no_target";
	/** The shared executor's cooldown refusal, shown before any vessel code runs. */
	private static final String COOLDOWN = "message.jujutsumod.character.action.cooldown";

	/**
	 * Aiming into empty sky (pitch -90) is a trap placement refusal: the 8-block placement ray exits
	 * through skyAccess air, {@code NailTrapRuntime.tryPlace} fails with {@code trap.no_ground} and
	 * returns HANDLED_FAILURE, and the router must NOT add no_target on top. The recorded stream is
	 * exactly the one runtime message.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void trapNoGroundMessageSurvivesRouter(GameTestHelper helper) {
		String fixture = "trapNoGroundMessageSurvivesRouter";
		BlockPos feet = new BlockPos(2, 1, 2);
		helper.setBlock(feet.below(), Blocks.STONE);
		MessageRecordingPlayer nobara = setupNobara(helper, fixture, feet, 0.0f, -90.0f);

		helper.runAtTickTime(2, () -> {
			try {
				AbilityResult result = cast(nobara, CharacterAbility.SECONDARY_SNEAK);
				helper.assertTrue(result == AbilityResult.HANDLED_FAILURE, diagnostic(fixture, "place",
						helper, nobara, "no-ground trap cast result", "HANDLED_FAILURE", result));
				assertRecordedExactly(helper, fixture, "place", nobara, List.of(TRAP_NO_GROUND));
			} finally {
				cleanup(nobara);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * With a valid floor under the aim but no nails (the starter kit is stripped after select),
	 * {@code tryPlace} reaches the {@code trap.no_nails} refusal — proving the message survives the
	 * router when the cast got past the aim gate. The arena is a flat stone floor covering the three
	 * placement points and the caster's support; the aim lands at the floor top inside the structure.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void trapNoNailsMessageSurvivesRouter(GameTestHelper helper) {
		String fixture = "trapNoNailsMessageSurvivesRouter";
		BlockPos feet = new BlockPos(2, 1, 2);
		helper.setBlock(feet.below(), Blocks.STONE);
		// The trap's three placement rays drop vertically from the hit point (radius 1.15, prism 3)
		// and must each land on a floor top; a 3x3 floor covers every support and keeps the aim
		// corridor open.
		for (int x = 1; x <= 3; x++) {
			for (int z = 5; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		MessageRecordingPlayer nobara = setupNobara(helper, fixture, feet, 0.0f, 0.0f);
		// The starter kit (hammer, doll, nails) was granted by select(); strip it so the cast
		// deterministically reaches the no-nails refusal instead of arming a real trap.
		nobara.getInventory().clearContent();

		helper.runAtTickTime(2, () -> {
			try {
				// Aim at the floor top of the block two beyond the placement ring: the center ray
				// hits the stone floor, and the ring around that hit stays on the 3x3 floor.
				BlockPos aim = helper.absolutePos(new BlockPos(2, 0, 6));
				TodoSwapTestFixtures.aimAt(nobara, new Vec3(aim.getX() + 0.5, aim.getY() + 1.0, aim.getZ() + 0.5));
				AbilityResult result = cast(nobara, CharacterAbility.SECONDARY_SNEAK);
				helper.assertTrue(result == AbilityResult.HANDLED_FAILURE, diagnostic(fixture, "place",
						helper, nobara, "no-nails trap cast result", "HANDLED_FAILURE", result));
				assertRecordedExactly(helper, fixture, "place", nobara, List.of(TRAP_NO_NAILS));
			} finally {
				cleanup(nobara);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * A fresh player has zero curse links, so self resonance resolves to NONE and refuses with
	 * {@code self_resonance.no_link} (HANDLED_FAILURE). The router must not overwrite it with the
	 * generic fallback — this is the exact E10 overwrite the tri-state result was introduced to kill.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void selfResonanceNoLinkMessageSurvivesRouter(GameTestHelper helper) {
		String fixture = "selfResonanceNoLinkMessageSurvivesRouter";
		BlockPos feet = new BlockPos(2, 1, 2);
		helper.setBlock(feet.below(), Blocks.STONE);
		MessageRecordingPlayer nobara = setupNobara(helper, fixture, feet, 0.0f, 0.0f);

		helper.runAtTickTime(2, () -> {
			try {
				AbilityResult result = cast(nobara, CharacterAbility.PRIMARY_SNEAK);
				helper.assertTrue(result == AbilityResult.HANDLED_FAILURE, diagnostic(fixture, "resonate",
						helper, nobara, "no-link resonance cast result", "HANDLED_FAILURE", result));
				assertRecordedExactly(helper, fixture, "resonate", nobara, List.of(RESONANCE_NO_LINK));
			} finally {
				cleanup(nobara);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * A genuinely silent failure must still show the generic fallback, exactly once. Mega Nail aimed
	 * into empty sky resolves to MISS ({@code ProjectJjkMegaNailRuntime.start} returns
	 * UNHANDLED_FAILURE — nothing was said), so the router's no_target line is the one message.
	 *
	 * <p>PRIMARY is deliberately not used here: with no nails and no target,
	 * {@code startDirectedHairpin} finds no seed and returns SUCCESS (the snap cue is the original
	 * boolean-true behavior preserved by the frozen mapping), so PRIMARY cannot produce an
	 * UNHANDLED_FAILURE for a fresh player — only the explosive lock can, and it is private static
	 * state no test can seed. SECONDARY exercises the same fallback gate deterministically.
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void unhandledFailureStillShowsGenericFallback(GameTestHelper helper) {
		String fixture = "unhandledFailureStillShowsGenericFallback";
		BlockPos feet = new BlockPos(2, 1, 2);
		helper.setBlock(feet.below(), Blocks.STONE);
		MessageRecordingPlayer nobara = setupNobara(helper, fixture, feet, 0.0f, -90.0f);

		helper.runAtTickTime(2, () -> {
			try {
				AbilityResult result = cast(nobara, CharacterAbility.SECONDARY);
				helper.assertTrue(result == AbilityResult.UNHANDLED_FAILURE, diagnostic(fixture, "cast",
						helper, nobara, "unhandled cast result", "UNHANDLED_FAILURE", result));
				assertRecordedExactly(helper, fixture, "cast", nobara, List.of(NO_TARGET));
			} finally {
				cleanup(nobara);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	/**
	 * The recorded gate order: selection, then cooldown, then the vessel's own gates. Both a stagger
	 * and a PRIMARY cooldown are seeded; the shared executor's cooldown gate fires before Nobara's
	 * router is ever reached, so the recorded stream is exactly the cooldown message and the stagger
	 * gate never runs (its silent UNHANDLED_FAILURE would have shown no_target if ordering drifted).
	 */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void cooldownAndStaggerOrderingIsRecorded(GameTestHelper helper) {
		String fixture = "cooldownAndStaggerOrderingIsRecorded";
		BlockPos feet = new BlockPos(2, 1, 2);
		helper.setBlock(feet.below(), Blocks.STONE);
		MessageRecordingPlayer nobara = setupNobara(helper, fixture, feet, 0.0f, 0.0f);

		helper.runAtTickTime(2, () -> {
			try {
				long gameTime = nobara.level().getGameTime();
				// Seed both gates for the same slot: whichever runs first decides the outcome.
				CombatStagger.GLOBAL.apply(nobara.getUUID(), gameTime, 100);
				CharacterAbilityCooldowns.start(nobara, CharacterAbility.PRIMARY, 100);
				AbilityResult result = cast(nobara, CharacterAbility.PRIMARY);
				helper.assertTrue(result == AbilityResult.UNHANDLED_FAILURE, diagnostic(fixture, "cast",
						helper, nobara, "cooldown-gated cast result", "UNHANDLED_FAILURE", result));
				assertRecordedExactly(helper, fixture, "cast", nobara, List.of(COOLDOWN));
			} finally {
				cleanup(nobara);
			}
		});
		helper.runAtTickTime(20, () -> helper.succeed());
	}

	// -- Fixtures ------------------------------------------------------------------------------

	/**
	 * Builds the recording Nobara: a fresh {@link MessageRecordingPlayer} at the given relative feet
	 * (absolute position, block-centre x/z, feet on the given y), NOBARA selected, and the cooldown
	 * slots cleared (after select — the cooldown key resolves the vessel from the live selection).
	 */
	private static MessageRecordingPlayer setupNobara(GameTestHelper helper, String fixture,
			BlockPos relativeFeet, float yaw, float pitch) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "nobara-ability-result-mock");
		MessageRecordingPlayer nobara = new MessageRecordingPlayer(server, level, profile,
				ClientInformation.createDefault());
		BlockPos absolute = helper.absolutePos(relativeFeet);
		nobara.setPosRaw(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		nobara.setYRot(yaw);
		nobara.setXRot(pitch);
		nobara.setYHeadRot(yaw);
		CharacterSelectionManager.select(nobara, JujutsuCharacter.NOBARA);
		CharacterAbilityCooldowns.clear(nobara, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(nobara, CharacterAbility.SECONDARY);
		CharacterAbilityCooldowns.clear(nobara, CharacterAbility.SECONDARY_SNEAK);
		return nobara;
	}

	/** THE production invocation, notify=true exactly like the C2S receiver makes it. */
	private static AbilityResult cast(MessageRecordingPlayer nobara, CharacterAbility slot) {
		return CharacterAbilityExecutor.tryCast(nobara, slot, true);
	}

	/** Asserts the recorded stream is EXACTLY the expected keys, in order. */
	private static void assertRecordedExactly(GameTestHelper helper, String fixture, String phase,
			MessageRecordingPlayer nobara, List<String> expected) {
		helper.assertTrue(nobara.recorded().equals(expected), diagnostic(fixture, phase, helper, nobara,
				"recorded messages", expected, nobara.recorded()));
	}

	private static Component diagnostic(String fixture, String phase, GameTestHelper helper,
			MessageRecordingPlayer nobara, String what, Object expected, Object actual) {
		return Component.literal("[" + fixture + "/" + phase + " @tick " + helper.getTick()
				+ " caster=" + nobara.getUUID() + "] " + what
				+ ": expected <" + expected + ">, actual <" + actual + ">");
	}

	/**
	 * Best-effort cleanup (success AND failure paths): clear every cooldown slot the scenarios seed
	 * or cast. The player was never placed in the player list, so there is nothing to remove there;
	 * no Todo transient state is created by Nobara casts; the seeded stagger is UUID-scoped and
	 * self-expiring. Never throws — a teardown exception must not mask the failure it cleans up after.
	 */
	private static void cleanup(MessageRecordingPlayer nobara) {
		safe(() -> CharacterAbilityCooldowns.clear(nobara, CharacterAbility.PRIMARY));
		safe(() -> CharacterAbilityCooldowns.clear(nobara, CharacterAbility.SECONDARY));
		safe(() -> CharacterAbilityCooldowns.clear(nobara, CharacterAbility.SECONDARY_SNEAK));
	}

	private static void safe(Runnable step) {
		try {
			step.run();
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}

	/**
	 * A {@code ServerPlayer} that records every {@link #displayClientMessage} it receives instead of
	 * sending it to a client, as the list of translation keys in display order.
	 *
	 * <p>Constructed with the same {@code (MinecraftServer, ServerLevel, GameProfile,
	 * ClientInformation)} shape {@code GameTestHelper$2} uses (bytecode-verified); the player is
	 * deliberately never placed into the player list — see the class javadoc.
	 */
	private static final class MessageRecordingPlayer extends ServerPlayer {
		private final List<String> recorded = new ArrayList<>();

		MessageRecordingPlayer(MinecraftServer server, ServerLevel level, GameProfile profile,
				ClientInformation clientInformation) {
			super(server, level, profile, clientInformation);
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (message.getContents() instanceof TranslatableContents contents) {
				recorded.add(contents.getKey());
			} else {
				recorded.add(message.getString());
			}
		}

		/** The recorded translation keys, in display order. */
		List<String> recorded() {
			return List.copyOf(recorded);
		}
	}
}
