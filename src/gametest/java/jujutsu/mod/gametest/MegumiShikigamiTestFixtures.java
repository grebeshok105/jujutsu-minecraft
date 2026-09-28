package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.entity.EntityTypeTest;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;

/**
 * Shared fixtures for the Nue (Ten Shadows selection layer) server scenarios, block B5.
 *
 * <p>Mirrors {@link TodoSwapTestFixtures}: the caster is
 * {@link GameTestHelper#makeMockServerPlayerInLevel()} teleported to an absolute spot derived from
 * a helper-relative block, selected as MEGUMI with cleared cooldowns. Cleanup runs on success AND
 * failure and additionally tears down both Megumi pack runtimes plus the static shikigami
 * selection — the selection map is static and would otherwise leak one test's NUE into the next
 * test's fresh UUID... and worse, a leftover pack record would make a later summon resolve as a
 * recall or a swap instead of a clean summon.
 */
public final class MegumiShikigamiTestFixtures {

	private MegumiShikigamiTestFixtures() {}

	/** Extended Stage A diagnostic: [fixture/phase @tick N caster=<uuid>] what: expected <e>, actual <a>. */
	public static Component diagnostic(String fixture, String phase, long tick,
			UUID caster, String what, Object expected, Object actual) {
		return Component.literal("[" + fixture + "/" + phase + " @tick " + tick
				+ " caster=" + caster + "] " + what
				+ ": expected <" + expected + ">, actual <" + actual + ">");
	}

	/**
	 * Creates the Megumi caster: mock player teleported to the centre of the relative block,
	 * selected as MEGUMI, PRIMARY + PRIMARY_SNEAK cooldowns cleared AFTER select (the cooldown key
	 * resolves the vessel from the current selection), and the static shikigami selection cleared
	 * so every scenario starts from the DOGS default.
	 */
	public static ServerPlayer setupMegumiCaster(GameTestHelper helper,
			String fixture, BlockPos relativeFeet, float yaw, float pitch) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos absolute = helper.absolutePos(relativeFeet);
			caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
					Set.of(), yaw, pitch, false);
			// The cooldown key resolves the vessel from the current selection, so select() MUST run first.
			CharacterSelectionManager.select(caster, JujutsuCharacter.MEGUMI);
			CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
			CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY_SNEAK);
			MegumiShikigamiSelection.clear(caster.getUUID());

			long tick = helper.getTick();
			helper.assertTrue(caster.isAlive(), diagnostic(fixture, "setup", tick, caster.getUUID(),
					"caster alive", "true", caster.isAlive()));
			helper.assertTrue(!caster.isSpectator(), diagnostic(fixture, "setup", tick, caster.getUUID(),
					"caster not spectator", "false", caster.isSpectator()));
			int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
			helper.assertTrue(primary == 0, diagnostic(fixture, "setup", tick, caster.getUUID(),
					"PRIMARY cooldown clear", "0", primary));
			int sneak = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY_SNEAK);
			helper.assertTrue(sneak == 0, diagnostic(fixture, "setup", tick, caster.getUUID(),
					"PRIMARY_SNEAK cooldown clear", "0", sneak));
			MegumiShikigami selected = MegumiShikigamiSelection.selected(caster.getUUID());
			helper.assertTrue(selected == MegumiShikigami.DOGS, diagnostic(fixture, "setup", tick, caster.getUUID(),
					"shikigami selection defaults to DOGS", MegumiShikigami.DOGS, selected));
			return caster;
		} catch (RuntimeException | AssertionError failure) {
			cleanupCaster(helper, caster);
			throw failure;
		}
	}

	/**
	 * Every live Nue body owned by {@code ownerId} in {@code level}. Owner-filtered, never
	 * bounds-filtered: the world offset is random per run and bodies drift (hover), so a structure
	 * bounds scan could miss a live body or catch a sibling test's. Fresh mock UUIDs per test make
	 * the owner filter exact.
	 */
	public static List<MegumiNueEntity> nueOwnedBy(ServerLevel level, UUID ownerId) {
		List<MegumiNueEntity> owned = new ArrayList<>();
		for (MegumiNueEntity body : level.getEntities(
				EntityTypeTest.forClass(MegumiNueEntity.class), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/**
	 * Generic form of {@link #nueOwnedBy} for the new roster types: every live body of
	 * {@code bodyClass} owned by {@code ownerId}, owner-filtered — never bounds-filtered (the
	 * world offset is random per run).
	 */
	public static <T extends jujutsu.mod.character.megumi.MegumiShikigamiEntity> List<T> ownedBy(
			ServerLevel level, UUID ownerId, Class<T> bodyClass) {
		List<T> owned = new ArrayList<>();
		for (T body : level.getEntities(EntityTypeTest.forClass(bodyClass), candidate -> true)) {
			if (ownerId.equals(body.ownerUuid())) {
				owned.add(body);
			}
		}
		return owned;
	}

	/**
	 * Cleanup (success AND failure paths): tear down both pack runtimes with the cooldown-free
	 * FIXTURE_RESET reason, clear both cooldown slots while still selected as MEGUMI (the key
	 * resolves the vessel), clear the static shikigami selection, then PlayerList.remove — never
	 * {@code discard()}, which would leave the player registered. Never throws.
	 */
	public static void cleanupCaster(GameTestHelper helper, ServerPlayer caster) {
		MinecraftServer server = helper.getLevel().getServer();
		UUID ownerId = caster.getUUID();
		safe(() -> MegumiShikigamiRuntime.teardown(server, ownerId,
				MegumiShikigamiRuntime.TeardownReason.FIXTURE_RESET));
		safe(() -> MegumiSummonRuntime.teardown(server, ownerId,
				MegumiSummonRuntime.TeardownReason.FIXTURE_RESET));
		safe(() -> CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY));
		safe(() -> CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY_SNEAK));
		safe(() -> MegumiSummonCooldowns.clear(ownerId));
		safe(() -> MegumiShikigamiSelection.clear(ownerId));
		safe(() -> server.getPlayerList().remove(caster));
	}

	/**
	 * Intermediate-callback guard for multi-tick scenarios: runs {@code step}, and when it throws,
	 * cleans up before rethrowing so a red intermediate tick leaves no pack, cooldown or selection
	 * behind. Later callbacks never run once the test has failed, so the final callback owns the
	 * unconditional cleanup instead.
	 */
	public static void runGuarded(GameTestHelper helper, ServerPlayer caster, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException | AssertionError failure) {
			cleanupCaster(helper, caster);
			throw failure;
		}
	}

	/** Asserts the caster currently has no shikigami pack record (both runtimes). */
	public static void assertNoPack(GameTestHelper helper, String fixture, String phase, ServerPlayer caster) {
		long tick = helper.getTick();
		MinecraftServer server = helper.getLevel().getServer();
		// Issue #107: one record per type, so "no pack" is an empty per-type row — not a single read.
		int shikigamiPacks = MegumiShikigamiRuntime.packViews(server, caster.getUUID()).size();
		helper.assertTrue(shikigamiPacks == 0, diagnostic(fixture, phase, tick, caster.getUUID(),
				"shikigami pack records gone", "0", shikigamiPacks));
		boolean dogsGone = MegumiSummonRuntime.packView(server, caster.getUUID()).isEmpty();
		helper.assertTrue(dogsGone, diagnostic(fixture, phase, tick, caster.getUUID(),
				"divine dog pack record gone", "empty", dogsGone ? "empty" : "present"));
	}

	/** The owner's live shikigami pack types, in enum order — the coexistence reading (issue #107). */
	public static List<String> shikigamiPackTypes(MinecraftServer server, UUID ownerId) {
		return MegumiShikigamiRuntime.packViews(server, ownerId).stream()
				.map(MegumiShikigamiRuntime.PackView::type)
				.toList();
	}

	/** Whether the owner's row holds the pack of exactly this type. */
	public static boolean hasPack(MinecraftServer server, UUID ownerId, MegumiShikigami type) {
		return shikigamiPackTypes(server, ownerId).contains(type.id());
	}

	private static void safe(Runnable step) {
		try {
			step.run();
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}
}
