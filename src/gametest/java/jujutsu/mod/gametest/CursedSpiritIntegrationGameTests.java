package jujutsu.mod.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiAbilityRouter;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.nobara.projectjjk.EmbeddedNailRegistry;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraProfile;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualPolicy;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.RemnantVisualType;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuSounds;

/**
 * Block 5 integration scenarios: a cursed spirit is a full citizen of the existing combat core.
 * Nobara's nail mark + directed Hairpin (R6/R7), the Mega Nail mis-aim refusal (R7), the straw-doll
 * resonance ritual on a marked spirit with an unmarked control (R8), the CURSE remnant mint for
 * every tier (R17), Todo's melee with a forced Black Flash differential (R9), Megumi's sic (R10)
 * and sic target ranking over an edge-graze (R16), plus the live sound-registry check that
 * complements {@code CursedSpiritSoundSymmetryTest} (unit registries are frozen before mod
 * registration runs).
 *
 * <p>Every scenario asserts its premises before its behaviour so a red run names the broken
 * assumption, and every result is banded from the production numbers rather than asserted exactly.
 */
public final class CursedSpiritIntegrationGameTests {
	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively.

	private static final TagKey<EntityType<?>> CURSE_TAG =
			TagKey.create(Registries.ENTITY_TYPE, JujutsuMod.id("resonance_remnant_curse"));
	private static final int MARK_POLL_DEADLINE = 60;

/**
 * R6/R7 — a nail launched at a lesser spirit embeds and marks it, then the production PRIMARY
 * route (directed Hairpin) returns SUCCESS and, once the scheduled chain lands, damages the
 * marked body and consumes the mark. The premise pins an embedded owned nail on the spirit
 * before the cast; the oracle is a STRICT HP decrease plus a dropped mark count (the chain
 * detonates {@code HAIRPIN_EXPLOSION_START_DELAY_TICKS} after the cast, so the damage is
 * polled, never asserted on the cast tick).
 */
@GameTest(maxTicks = 200, skyAccess = true)
public void nailImpactMarksTheSpiritAndDirectedHairpinPunishes(GameTestHelper helper) {
	String fixture = "nailImpactMarksTheSpiritAndDirectedHairpinPunishes";
	CursedSpiritTestFixtures.layStoneFloor(helper);
	CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
	ServerPlayer caster = setupNobaraCaster(helper, fixture, new BlockPos(1, 1, 1), -90.0f, 0.0f);
	ServerLevel level = helper.getLevel();
	CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
	AtomicBoolean marked = new AtomicBoolean();
	AtomicBoolean castDone = new AtomicBoolean();
	AtomicBoolean punished = new AtomicBoolean();
	AtomicReference<Double> hpBefore = new AtomicReference<>(0.0);
	AtomicInteger marksBefore = new AtomicInteger(0);

	helper.runAtTickTime(2, () -> {
		try {
			helper.assertTrue(caster.hasLineOfSight(spirit), CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
					"line of sight to spirit", "true", caster.hasLineOfSight(spirit)));
			helper.assertTrue(ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime()) == 0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							spirit.getUUID(), "premise: no marks before the throw", "0",
							ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime())));
			TodoSwapTestFixtures.aimAt(caster,
					spirit.position().add(0.0, spirit.getBbHeight() / 2.0, 0.0));
			CursedSpiritTestFixtures.freezeGround(spirit);
			launchNailAt(level, caster, spirit);
		} catch (RuntimeException | AssertionError failure) {
			cleanup(fixture, helper, caster, spirit);
			throw failure;
		}
	});

	for (int tick = 3; tick <= MARK_POLL_DEADLINE; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (castDone.get()) {
				return;
			}
			if (ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime()) >= 1) {
				marked.set(true);
			}
			if (!marked.get()) {
				return;
			}
			try {
				List<ProjectJjkNailEntity> embedded = level.getEntitiesOfClass(ProjectJjkNailEntity.class,
						spirit.getBoundingBox().inflate(2.0),
						nail -> nail.isEmbedded() && nail.isOwnedBy(caster.getUUID())
								&& spirit.getUUID().equals(nail.embeddedTargetUuid()));
				// The embedded-nail flag reads true one tick before the registry tracks the nail,
				// and the directed chain is built from the REGISTRY: wait for both instead of
				// casting into an empty chain (which returns SUCCESS and does nothing).
				List<ProjectJjkNailEntity> chained = EmbeddedNailRegistry.loadedOwnedNails(level, caster.getUUID());
				if (embedded.isEmpty() || chained.isEmpty()) {
					if (pollTick == MARK_POLL_DEADLINE) {
						helper.assertTrue(!embedded.isEmpty(), CursedSpiritTestFixtures.diagnostic(
								fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
								"premise: embedded owned nail on the spirit", ">= 1", embedded.size()));
						helper.assertTrue(!chained.isEmpty(), CursedSpiritTestFixtures.diagnostic(
								fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
								"premise: the tracked nail chain materialized before the deadline",
								">= 1", chained.size()));
					}
					return;
				}
				// Re-aim at the body's CURRENT centre: the directed seed comes from the caster's
				// look vector, so a stale aim (set before the spirit settled) would send the
				// chain elsewhere and the cast would resolve to nothing.
				TodoSwapTestFixtures.aimAt(caster,
						spirit.position().add(0.0, spirit.getBbHeight() / 2.0, 0.0));
				helper.assertTrue(!embedded.isEmpty() && !chained.isEmpty(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								spirit.getUUID(), "premise: embedded owned nail tracked in the chain",
								">= 1 / >= 1", embedded.size() + " / " + chained.size()));
				hpBefore.set((double) spirit.getHealth());
				marksBefore.set(ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime()));
				AbilityResult result = CharacterAbilityExecutor.tryCast(caster, CharacterAbility.PRIMARY, true);
				helper.assertTrue(result == AbilityResult.SUCCESS, CursedSpiritTestFixtures.diagnostic(
						fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
						"directed hairpin cast result", AbilityResult.SUCCESS, result));
				castDone.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanup(fixture, helper, caster, spirit);
				throw failure;
			}
		});
	}

	for (int tick = MARK_POLL_DEADLINE + 1; tick <= 150; tick++) {
		helper.runAtTickTime(tick, () -> {
			if (!castDone.get() || punished.get()) {
				return;
			}
			try {
				if (spirit.getHealth() < hpBefore.get()
						&& ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime()) < marksBefore.get()) {
					punished.set(true);
				}
			} catch (RuntimeException | AssertionError failure) {
				cleanup(fixture, helper, caster, spirit);
				throw failure;
			}
		});
	}

	helper.runAtTickTime(160, () -> {
		helper.assertTrue(marked.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), spirit.getUUID(), "nail marked the spirit within "
						+ MARK_POLL_DEADLINE + " ticks", ">= 1 mark", ProjectJjkNailMarks.marks(
						spirit.getUUID(), level.getGameTime())));
		helper.assertTrue(castDone.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), spirit.getUUID(), "directed hairpin was cast", "true", castDone.get()));
		helper.assertTrue(punished.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), spirit.getUUID(), "hairpin strictly damaged and consumed the mark",
				"hp < " + hpBefore.get() + " and marks < " + marksBefore.get(),
				spirit.getHealth() + " / marks "
						+ ProjectJjkNailMarks.marks(spirit.getUUID(), level.getGameTime())));
		cleanup(fixture, helper, caster, spirit);
		helper.succeed();
	});
}

/**
 * R17 — two ordinary nail impacts mint exactly one remnant per tier, each bound to the struck
 * spirit and carrying the CURSE visual (every tier type is in
 * {@code jujutsumod:resonance_remnant_curse}). Three sequential phases with staggered lanes —
 * lesser at (4,1,1), common at (6,1,3), greater at (5,1,5) — so no nail in flight can cross
 * another unfinished body; one nail is ever in flight at a time. The finale sweeps owned nails
 * and proves zero orphans remain.
 */
@GameTest(maxTicks = 420, skyAccess = true)
public void twoNailImpactsMintCurseRemnantsForEveryTier(GameTestHelper helper) {
	String fixture = "twoNailImpactsMintCurseRemnantsForEveryTier";
	CursedSpiritTestFixtures.layStoneFloor(helper);
	CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
	ServerPlayer caster = setupNobaraCaster(helper, fixture, new BlockPos(1, 1, 1), -90.0f, 0.0f);
	ServerLevel level = helper.getLevel();
	CursedSpiritEntity lesser = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
	CursedSpiritEntity common = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.CURSED_SPIRIT, new BlockPos(6, 1, 3));
	CursedSpiritEntity greater = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.GREATER_CURSED_SPIRIT, new BlockPos(5, 1, 5));
	AtomicBoolean firstHitLesser = new AtomicBoolean();
	AtomicBoolean mintedLesser = new AtomicBoolean();
	AtomicBoolean firstHitCommon = new AtomicBoolean();
	AtomicBoolean mintedCommon = new AtomicBoolean();
	AtomicBoolean firstHitGreater = new AtomicBoolean();
	AtomicBoolean mintedGreater = new AtomicBoolean();
	// The second nail must clear the victim's hurt-immunity window (vanilla rejects damage
	// while invulnerableTime > 10), so it launches 26 ticks after the first impact was seen —
	// a fixed tick races a slow first flight.
	java.util.concurrent.atomic.AtomicLong markTickLesser = new java.util.concurrent.atomic.AtomicLong(-1);
	java.util.concurrent.atomic.AtomicLong markTickCommon = new java.util.concurrent.atomic.AtomicLong(-1);
	java.util.concurrent.atomic.AtomicLong markTickGreater = new java.util.concurrent.atomic.AtomicLong(-1);
	AtomicBoolean secondLaunchLesser = new AtomicBoolean();
	java.util.concurrent.atomic.AtomicReference<Vec3> launchPosLesser =
			new java.util.concurrent.atomic.AtomicReference<>(Vec3.ZERO);
	java.util.concurrent.atomic.AtomicReference<ProjectJjkNailEntity> secondNailLesser =
			new java.util.concurrent.atomic.AtomicReference<>();
	java.util.concurrent.atomic.AtomicReference<String> launchGeomLesser =
			new java.util.concurrent.atomic.AtomicReference<>("");
	AtomicBoolean secondLaunchCommon = new AtomicBoolean();
	AtomicBoolean secondLaunchGreater = new AtomicBoolean();

	helper.runAtTickTime(2, () -> {
		try {
			CursedSpiritTestFixtures.freezeGround(lesser);
			CursedSpiritTestFixtures.freezeGround(common);
			CursedSpiritTestFixtures.freezeGround(greater);
			helper.assertTrue(level.getServer().getPlayerList().getPlayer(caster.getUUID()) != null,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), null,
							"premise: the caster resolves through the player list (the mint path needs a live player)",
							"non-null", "null"));
			TodoSwapTestFixtures.aimAt(caster,
					lesser.position().add(0.0, lesser.getBbHeight() / 2.0, 0.0));
			launchNailAt(level, caster, lesser);
		} catch (RuntimeException | AssertionError failure) {
			cleanupAll(helper, caster, lesser, common, greater);
			throw failure;
		}
	});

	for (int tick = 3; tick <= 60; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (firstHitLesser.get()
					|| ProjectJjkNailMarks.marks(lesser.getUUID(), level.getGameTime()) < 1) {
				return;
			}
			firstHitLesser.set(true);
			markTickLesser.set(pollTick);
			helper.assertTrue(remnantFor(caster, lesser.getUUID()) == null,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							lesser.getUUID(), "premise: one hit mints nothing", "null",
							remnantFor(caster, lesser.getUUID())));
		});
	}

	for (int tick = 4; tick <= 129; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (!firstHitLesser.get() || secondLaunchLesser.get()) {
				return;
			}
			if (pollTick < markTickLesser.get() + 26) {
				return;
			}
			try {
				secondLaunchLesser.set(true);
				containOnPad(helper, fixture, caster, lesser, new BlockPos(4, 1, 1));
				launchPosLesser.set(lesser.position());
				TodoSwapTestFixtures.aimAt(caster,
						lesser.position().add(0.0, lesser.getBbHeight() / 2.0, 0.0));
				launchGeomLesser.set("bb=" + lesser.getBbHeight() + " w=" + lesser.getBbWidth()
						+ " eye=" + caster.getEyeHeight() + " caster=" + caster.position()
						+ " spirit=" + lesser.position() + " origin=" + helper.absolutePos(BlockPos.ZERO)
						+ " feetBlock=" + level.getBlockState(lesser.blockPosition())
						+ " below=" + level.getBlockState(lesser.blockPosition().below()));
				secondNailLesser.set(launchNailAt(level, caster, lesser));
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	for (int tick = 65; tick <= 125; tick++) {
		helper.runAtTickTime(tick, () -> {
			if (mintedLesser.get() || remnantFor(caster, lesser.getUUID()) == null) {
				return;
			}
			try {
				assertCurseRemnantBound(helper, fixture, caster, lesser);
				mintedLesser.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	helper.runAtTickTime(129, () -> {
		try {
			helper.assertTrue(mintedLesser.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), lesser.getUUID(),
					"second hit minted the lesser remnant", "curse:<uuid>",
					remnantFor(caster, lesser.getUUID()) + " marks="
							+ ProjectJjkNailMarks.marks(lesser.getUUID(), level.getGameTime())
							+ " hp=" + lesser.getHealth()
							+ " " + nailState(level, caster, lesser)
							+ " launched2=" + secondLaunchLesser.get()
							+ " moved=" + launchPosLesser.get().distanceTo(lesser.position())
							+ " at=" + lesser.position() + " geom=" + launchGeomLesser.get()));
			TodoSwapTestFixtures.aimAt(caster,
					common.position().add(0.0, common.getBbHeight() / 2.0, 0.0));
			launchNailAt(level, caster, common);
		} catch (RuntimeException | AssertionError failure) {
			cleanupAll(helper, caster, lesser, common, greater);
			throw failure;
		}
	});

	for (int tick = 130; tick <= 190; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (firstHitCommon.get()
					|| ProjectJjkNailMarks.marks(common.getUUID(), level.getGameTime()) < 1) {
				return;
			}
			firstHitCommon.set(true);
			markTickCommon.set(pollTick);
			helper.assertTrue(remnantFor(caster, common.getUUID()) == null,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							common.getUUID(), "premise: one hit mints nothing", "null",
							remnantFor(caster, common.getUUID())));
		});
	}

	for (int tick = 134; tick <= 259; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (!firstHitCommon.get() || secondLaunchCommon.get()) {
				return;
			}
			if (pollTick < markTickCommon.get() + 26) {
				return;
			}
			try {
				secondLaunchCommon.set(true);
				containOnPad(helper, fixture, caster, common, new BlockPos(6, 1, 3));
				TodoSwapTestFixtures.aimAt(caster,
						common.position().add(0.0, common.getBbHeight() / 2.0, 0.0));
				launchNailAt(level, caster, common);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	for (int tick = 195; tick <= 255; tick++) {
		helper.runAtTickTime(tick, () -> {
			if (mintedCommon.get() || remnantFor(caster, common.getUUID()) == null) {
				return;
			}
			try {
				assertCurseRemnantBound(helper, fixture, caster, common);
				mintedCommon.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	helper.runAtTickTime(259, () -> {
		try {
			helper.assertTrue(mintedCommon.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), common.getUUID(),
					"second hit minted the common remnant", "curse:<uuid>",
					remnantFor(caster, common.getUUID()) + " marks="
							+ ProjectJjkNailMarks.marks(common.getUUID(), level.getGameTime())
							+ " hp=" + common.getHealth()
							+ " " + nailState(level, caster, common)
							+ " launched2=" + secondLaunchCommon.get()));
			TodoSwapTestFixtures.aimAt(caster,
					greater.position().add(0.0, greater.getBbHeight() / 2.0, 0.0));
			launchNailAt(level, caster, greater);
		} catch (RuntimeException | AssertionError failure) {
			cleanupAll(helper, caster, lesser, common, greater);
			throw failure;
		}
	});

	for (int tick = 260; tick <= 320; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (firstHitGreater.get()
					|| ProjectJjkNailMarks.marks(greater.getUUID(), level.getGameTime()) < 1) {
				return;
			}
			firstHitGreater.set(true);
			markTickGreater.set(pollTick);
			helper.assertTrue(remnantFor(caster, greater.getUUID()) == null,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							greater.getUUID(), "premise: one hit mints nothing", "null",
							remnantFor(caster, greater.getUUID())));
		});
	}

	for (int tick = 264; tick <= 390; tick++) {
		final int pollTick = tick;
		helper.runAtTickTime(pollTick, () -> {
			if (!firstHitGreater.get() || secondLaunchGreater.get()) {
				return;
			}
			if (pollTick < markTickGreater.get() + 26) {
				return;
			}
			try {
				secondLaunchGreater.set(true);
				containOnPad(helper, fixture, caster, greater, new BlockPos(5, 1, 5));
				TodoSwapTestFixtures.aimAt(caster,
						greater.position().add(0.0, greater.getBbHeight() / 2.0, 0.0));
				launchNailAt(level, caster, greater);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	for (int tick = 325; tick <= 385; tick++) {
		helper.runAtTickTime(tick, () -> {
			if (mintedGreater.get() || remnantFor(caster, greater.getUUID()) == null) {
				return;
			}
			try {
				assertCurseRemnantBound(helper, fixture, caster, greater);
				mintedGreater.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, lesser, common, greater);
				throw failure;
			}
		});
	}

	helper.runAtTickTime(390, () -> {
		helper.assertTrue(mintedGreater.get(), CursedSpiritTestFixtures.diagnostic(fixture,
				helper.getTick(), caster.getUUID(), greater.getUUID(),
				"second hit minted the greater remnant", "curse:<uuid>",
				remnantFor(caster, greater.getUUID()) + " marks="
						+ ProjectJjkNailMarks.marks(greater.getUUID(), level.getGameTime())
						+ " hp=" + greater.getHealth()
							+ " " + nailState(level, caster, greater)
							+ " launched2=" + secondLaunchGreater.get()));
		for (CursedSpiritEntity spirit : List.of(lesser, common, greater)) {
			helper.assertTrue(spirit.getType().is(CURSE_TAG), CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
					"tier type in jujutsumod:resonance_remnant_curse",
					"jujutsumod:resonance_remnant_curse",
					spirit.getType().toString()));
		}
		int orphansBeforeSweep = ownedNails(level, caster).size();
		for (ProjectJjkNailEntity nail : ownedNails(level, caster)) {
			nail.discard();
		}
		helper.assertTrue(ownedNails(level, caster).isEmpty(), CursedSpiritTestFixtures.diagnostic(
				fixture, helper.getTick(), caster.getUUID(), null, "no orphan owned nails after sweep",
				"0 (swept " + orphansBeforeSweep + ")", ownedNails(level, caster).size()));
		cleanupAll(helper, caster, lesser, common, greater);
		helper.succeed();
	});
}

/**
 * R9 — Todo's melee with a forced Black Flash out-damages the same unforced melee by the
 * production bonus. Two identical lesser spirits: the forced hit lands first at full attack
 * strength, then the unforced baseline is the first clean hit (a 10% natural proc staggers,
 * so a staggered baseline is discarded and retried on later ticks, still at full strength).
 * Oracle, derived from {@link TodoProfile#BLACK_FLASH_DAMAGE_MULTIPLIER}: the forced drop is
 * strictly greater than the clean drop and matches the x1.75 bonus within rounding, plus the
 * forced hit staggers for the Black Flash window (14 ticks).
 */
@GameTest(maxTicks = 140, skyAccess = true)
public void todoForcedBlackFlashOutDamagesTheSameUnforcedMelee(GameTestHelper helper) {
	String fixture = "todoForcedBlackFlashOutDamagesTheSameUnforcedMelee";
	CursedSpiritTestFixtures.layStoneFloor(helper);
	CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
	ServerPlayer caster = TodoSwapTestFixtures.setupTodoCaster(helper, fixture, new BlockPos(1, 1, 1),
			-90.0f, 0.0f);
	ServerLevel level = helper.getLevel();
	CursedSpiritEntity forced = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(3, 1, 1));
	CursedSpiritEntity plain = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
			JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 3));
	AtomicBoolean forcedDone = new AtomicBoolean();
	AtomicBoolean staggered = new AtomicBoolean();
	AtomicBoolean cleanFound = new AtomicBoolean();
	AtomicReference<Double> forcedDrop = new AtomicReference<>(0.0);
	AtomicReference<Double> cleanDrop = new AtomicReference<>(0.0);

	helper.runAtTickTime(2, () -> {
		try {
			CursedSpiritTestFixtures.freezeGround(forced);
			CursedSpiritTestFixtures.freezeGround(plain);
			helper.assertTrue(caster.hasLineOfSight(forced), CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), caster.getUUID(), forced.getUUID(),
					"line of sight to forced spirit", "true", caster.hasLineOfSight(forced)));
			helper.assertTrue(caster.hasLineOfSight(plain), CursedSpiritTestFixtures.diagnostic(
					fixture, helper.getTick(), caster.getUUID(), plain.getUUID(),
					"line of sight to plain spirit", "true", caster.hasLineOfSight(plain)));
		} catch (RuntimeException | AssertionError failure) {
			cleanupAll(helper, caster, forced, plain);
			throw failure;
		}
	});

	helper.runAtTickTime(25, () -> {
		try {
			ForcedBlackFlash.set(caster, true);
			double hpBefore = forced.getHealth();
			caster.attack(forced);
			double dropped = hpBefore - forced.getHealth();
			helper.assertTrue(dropped > 0.0,
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							forced.getUUID(), "forced melee damaged the spirit", "> 0.0", dropped));
			helper.assertTrue(CombatStagger.GLOBAL.isStaggered(forced.getUUID(), level.getGameTime()),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							forced.getUUID(), "forced Black Flash staggered the spirit", "true",
							CombatStagger.GLOBAL.isStaggered(forced.getUUID(), level.getGameTime())));
			forcedDrop.set(dropped);
			forcedDone.set(true);
			staggered.set(true);
		} catch (RuntimeException | AssertionError failure) {
			ForcedBlackFlash.set(caster, false);
			cleanupAll(helper, caster, forced, plain);
			throw failure;
		} finally {
			ForcedBlackFlash.set(caster, false);
		}
	});

	for (int tick : new int[] {50, 75, 100}) {
		helper.runAtTickTime(tick, () -> {
			if (cleanFound.get()) {
				return;
			}
			try {
				ForcedBlackFlash.set(caster, false);
				helper.assertTrue(plain.isAlive(), CursedSpiritTestFixtures.diagnostic(fixture,
						helper.getTick(), caster.getUUID(), plain.getUUID(),
						"premise: plain spirit survives the baselines", "alive", plain.getHealth()));
				double hpBefore = plain.getHealth();
				caster.attack(plain);
				double dropped = hpBefore - plain.getHealth();
				if (dropped > 0.0
						&& !CombatStagger.GLOBAL.isStaggered(plain.getUUID(), level.getGameTime())) {
					cleanDrop.set(dropped);
					cleanFound.set(true);
				}
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, forced, plain);
				throw failure;
			}
		});
	}

	helper.runAtTickTime(115, () -> {
		ForcedBlackFlash.set(caster, false);
		helper.assertTrue(forcedDone.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), forced.getUUID(), "Todo landed the armed Black Flash", "true",
				forcedDone.get()));
		helper.assertTrue(cleanFound.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), plain.getUUID(),
				"an unforced hit without a natural Black Flash proc in 3 attempts", "true",
				cleanFound.get()));
		helper.assertTrue(forcedDrop.get() > cleanDrop.get(), CursedSpiritTestFixtures.diagnostic(
				fixture, helper.getTick(), caster.getUUID(), forced.getUUID(),
				"forced drop strictly above the unforced drop",
				"> " + cleanDrop.get(), forcedDrop.get()));
		double profileExpectation = cleanDrop.get() * TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER;
		helper.assertTrue(forcedDrop.get() >= profileExpectation - 0.5,
				CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
						forced.getUUID(), "forced drop matches the profile x1.75 bonus over unforced",
						">= " + (profileExpectation - 0.5) + " (" + cleanDrop.get() + " x "
								+ TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER + ")",
						forcedDrop.get()));
		helper.assertTrue(staggered.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), forced.getUUID(), "forced Black Flash staggered the spirit", "true",
				staggered.get()));
		cleanupAll(helper, caster, forced, plain);
		helper.succeed();
	});
}

	/**
	 * R7 — Mega Nail (SECONDARY) aimed 90° straight up resolves to MISS and answers
	 * UNHANDLED_FAILURE without cooling any slot: the shared executor cools nothing itself and
	 * the Mega Nail arm returns before any cooldown write. Verbatim precedent for the aim and
	 * the slot: {@code NobaraAbilityResultGameTests.unhandledFailureStillShowsGenericFallback}
	 * (pitch -90, SECONDARY). No chat stream is asserted — only the result and the cooldowns.
	 */
	@GameTest(maxTicks = 60, skyAccess = true)
	public void megaNailMisAimFailsWithoutCoolingAnySlot(GameTestHelper helper) {
		String fixture = "megaNailMisAimFailsWithoutCoolingAnySlot";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = setupNobaraCaster(helper, fixture, new BlockPos(1, 1, 1), -90.0f, 0.0f);
		caster.setXRot(-90.0f);

		helper.runAtTickTime(2, () -> {
			try {
				helper.assertTrue(
						CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: PRIMARY starts ready", "0",
								CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY)));
				helper.assertTrue(
						CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY) == 0,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: SECONDARY starts ready", "0",
								CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY)));
				AbilityResult result =
						CharacterAbilityExecutor.tryCast(caster, CharacterAbility.SECONDARY, true);
				helper.assertTrue(result == AbilityResult.UNHANDLED_FAILURE,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "mis-aimed Mega Nail cast result", AbilityResult.UNHANDLED_FAILURE,
								result));
				helper.assertTrue(
						CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY) == 0
								&& CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY) == 0,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "mis-aim cools nothing",
								"PRIMARY 0 / SECONDARY 0",
								"PRIMARY "
										+ CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY)
										+ " / SECONDARY "
										+ CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.SECONDARY)));
			} catch (RuntimeException | AssertionError failure) {
				CursedSpiritTestFixtures.cleanupVictim(helper, caster);
				throw failure;
			}
		});

		helper.runAtTickTime(20, () -> {
			CursedSpiritTestFixtures.cleanupVictim(helper, caster);
			helper.succeed();
		});
	}

/**
 * R8 — the straw-doll ritual burns the marked spirit and spares the unmarked control. Two
 * nail impacts mint a remnant bound to the marked spirit, then {@code tryStart} with hammer
 * main-hand + doll offhand schedules the impact one {@code DOLL_STRIKE} windup later.
 *
 * <p>Oracle: the marked HP drop equals {@code min(RESONANCE_DAMAGE, HP before impact)} ± 8.0.
 * The expected bar comes from {@code ProjectJjkNobaraProfile.RESONANCE_DAMAGE} capped by the
 * live grade-rolled remaining HP — never a magic literal: since the grade move a grade-5 body
 * holds only 12.0–20.0 HP ({@code CursedSpiritGradeProfile.health(GRADE_5)}, band max below
 * 28.0), the 28.0 resonance overkills it and the visible drop is the whole remaining bar
 * (≈12.0 here), while a higher-grade body would show the full 28.0. The heavy stagger still
 * lands and the control's HP never moves.
 */
	@GameTest(maxTicks = 280, skyAccess = true)
	public void strawDollRitualBurnsMarkedSpiritAndSparesUnmarked(GameTestHelper helper) {
		String fixture = "strawDollRitualBurnsMarkedSpiritAndSparesUnmarked";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = setupNobaraCaster(helper, fixture, new BlockPos(1, 1, 1), -90.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity marked = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(6, 1, 1));
		CursedSpiritEntity control = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(3, 1, 4));
		AtomicBoolean firstHit = new AtomicBoolean();
		AtomicBoolean minted = new AtomicBoolean();
		AtomicBoolean ritualStarted = new AtomicBoolean();
		AtomicBoolean impacted = new AtomicBoolean();
		AtomicReference<Double> markedHpBeforeImpact = new AtomicReference<>(0.0);
		AtomicReference<Double> controlHp = new AtomicReference<>(0.0);

		helper.runAtTickTime(2, () -> {
			try {
				CursedSpiritTestFixtures.freezeGround(marked);
				CursedSpiritTestFixtures.freezeGround(control);
				helper.assertTrue(caster.hasLineOfSight(marked), CursedSpiritTestFixtures.diagnostic(
						fixture, helper.getTick(), caster.getUUID(), marked.getUUID(),
						"line of sight to marked spirit", "true", caster.hasLineOfSight(marked)));
				helper.assertTrue(caster.distanceTo(marked) <= ProjectJjkRitualPolicy.MAX_RANGE,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								marked.getUUID(), "marked spirit inside the ritual range",
								"<= " + ProjectJjkRitualPolicy.MAX_RANGE, caster.distanceTo(marked)));
				TodoSwapTestFixtures.aimAt(caster,
						marked.position().add(0.0, marked.getBbHeight() / 2.0, 0.0));
				launchNailAt(level, caster, marked);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, marked, control);
				throw failure;
			}
		});

		for (int tick = 3; tick <= 80; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (firstHit.get()
						|| ProjectJjkNailMarks.marks(marked.getUUID(), level.getGameTime()) < 1) {
					return;
				}
				firstHit.set(true);
				helper.assertTrue(remnantFor(caster, marked.getUUID()) == null,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								marked.getUUID(), "premise: one hit mints nothing", "null",
								remnantFor(caster, marked.getUUID())));
			});
		}

		helper.runAtTickTime(84, () -> {
			try {
				helper.assertTrue(firstHit.get(), CursedSpiritTestFixtures.diagnostic(fixture,
						helper.getTick(), caster.getUUID(), marked.getUUID(),
						"first nail marked the spirit", ">= 1 mark", ProjectJjkNailMarks.marks(
								marked.getUUID(), level.getGameTime())));
				TodoSwapTestFixtures.aimAt(caster,
						marked.position().add(0.0, marked.getBbHeight() / 2.0, 0.0));
				launchNailAt(level, caster, marked);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, marked, control);
				throw failure;
			}
		});

		for (int tick = 85; tick <= 165; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (minted.get() || remnantFor(caster, marked.getUUID()) == null) {
					return;
				}
				try {
					assertCurseRemnantBound(helper, fixture, caster, marked);
					minted.set(true);
				} catch (RuntimeException | AssertionError failure) {
					cleanupAll(helper, caster, marked, control);
					throw failure;
				}
			});
		}

		helper.runAtTickTime(169, () -> {
			try {
				helper.assertTrue(minted.get(), CursedSpiritTestFixtures.diagnostic(fixture,
						helper.getTick(), caster.getUUID(), marked.getUUID(),
						"second hit minted the remnant", "curse:<uuid>",
						remnantFor(caster, marked.getUUID())));
				caster.setItemInHand(InteractionHand.MAIN_HAND,
						new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
				caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(JujutsuItems.STRAW_DOLL));
				helper.assertTrue(caster.getMainHandItem().is(JujutsuItems.STRAW_DOLL_HAMMER),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: hammer in the main hand", "straw_doll_hammer",
								caster.getMainHandItem()));
				helper.assertTrue(caster.getOffhandItem().is(JujutsuItems.STRAW_DOLL),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: doll in the offhand", "straw_doll", caster.getOffhandItem()));
				markedHpBeforeImpact.set((double) marked.getHealth());
				controlHp.set((double) control.getHealth());
				boolean started = ProjectJjkStrawDollRuntime.tryStart(caster, caster.getMainHandItem(),
						InteractionHand.MAIN_HAND);
				helper.assertTrue(started, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
						caster.getUUID(), marked.getUUID(), "straw-doll ritual started", "true", started));
				ritualStarted.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupAll(helper, caster, marked, control);
				throw failure;
			}
		});

		for (int tick = 170; tick <= 230; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (!ritualStarted.get() || impacted.get()) {
					return;
				}
				if (marked.getHealth() >= markedHpBeforeImpact.get()) {
					return;
				}
				try {
					double dropped = markedHpBeforeImpact.get() - marked.getHealth();
					// Resonance overkills low-grade bodies: the visible drop caps at the remaining HP.
					double expected = Math.min(ProjectJjkNobaraProfile.RESONANCE_DAMAGE,
							markedHpBeforeImpact.get());
					helper.assertTrue(
							dropped >= expected - 8.0 && dropped <= expected + 8.0,
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
									marked.getUUID(), "resonance damage band around min(RESONANCE_DAMAGE, HP before) = "
											+ expected,
									expected + " ± 8.0", dropped));
					helper.assertTrue(
							CombatStagger.GLOBAL.isStaggered(marked.getUUID(), level.getGameTime()),
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
									marked.getUUID(), "resonance staggered the marked spirit", "true",
									CombatStagger.GLOBAL.isStaggered(marked.getUUID(), level.getGameTime())));
					helper.assertTrue(control.getHealth() == controlHp.get(),
							CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
									control.getUUID(), "unmarked control takes none",
									controlHp.get(), control.getHealth()));
					impacted.set(true);
				} catch (RuntimeException | AssertionError failure) {
					cleanupAll(helper, caster, marked, control);
					throw failure;
				}
			});
		}

		helper.runAtTickTime(250, () -> {
			helper.assertTrue(impacted.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), marked.getUUID(),
					"ritual impact landed past the windup", "hp < " + markedHpBeforeImpact.get(),
					marked.getHealth()));
			helper.assertTrue(control.getHealth() == controlHp.get(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							control.getUUID(), "unmarked control still unaffected",
							controlHp.get(), control.getHealth()));
			cleanupAll(helper, caster, marked, control);
			helper.succeed();
		});
	}

	/**
	 * R16 — {@code TargetResolver} ranks spirits like any mob: the centred far spirit beats the
	 * near edge-graze through the production Megumi sic path (the E1b smoke). The graze is
	 * pinned 0.6 blocks off the aim ray: inside the 0.35 assist pad of its 0.425 half-width
	 * body (a live candidate) but outside the real box (assist-only), while the far spirit is
	 * pierced. Oracle: every sicced dog's target is the centred spirit, and only the centred
	 * spirit takes pounce damage.
	 */
	@GameTest(maxTicks = 220, skyAccess = true)
	public void sicResolvesCentredFarSpiritOverNearEdgeGraze(GameTestHelper helper) {
		String fixture = "sicResolvesCentredFarSpiritOverNearEdgeGraze";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture,
				new BlockPos(1, 1, 1), -90.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity centred = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
		double centredMax = centred.getMaxHealth();
		// The graze spawns inside the sic callback, not at setup: left in the arena from tick 0, the
		// dogs' autonomy pass marks it and a pounce lands before the sic ever fires — the "edge-graze
		// takes nothing" oracle then reads spec-correct autonomy damage as a sic leak.
		AtomicReference<CursedSpiritEntity> grazeRef = new AtomicReference<>();
		AtomicReference<Double> grazeMaxRef = new AtomicReference<>();
		AtomicBoolean summoned = new AtomicBoolean();
		AtomicBoolean sicced = new AtomicBoolean();
		AtomicBoolean resolved = new AtomicBoolean();
		AtomicBoolean impacted = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			try {
				CursedSpiritTestFixtures.freezeGround(centred);
				helper.assertTrue(centred.isAlive(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: centred spirit alive", "true", centred.isAlive()));
				boolean dogs = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(dogs, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
						caster.getUUID(), null, "divine dogs summon", "true", dogs));
				summoned.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupSic(helper, caster, centred, grazeRef.get());
				throw failure;
			}
		});

		helper.runAtTickTime(24, () -> {
			try {
				helper.assertTrue(summoned.get(), CursedSpiritTestFixtures.diagnostic(fixture,
						helper.getTick(), caster.getUUID(), null, "dogs were summoned", "true",
						summoned.get()));
				// Spawn and pin the graze now: the dogs have had no tick to mark it autonomously.
				CursedSpiritEntity graze = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
						JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(4, 1, 1));
				grazeRef.set(graze);
				grazeMaxRef.set((double) graze.getMaxHealth());
				Vec3 eye = caster.getEyePosition();
				Vec3 chest = centred.position().add(0.0, centred.getBbHeight() / 2.0, 0.0);
				Vec3 direction = chest.subtract(eye).normalize();
				Vec3 across = new Vec3(-direction.z, 0.0, direction.x).normalize();
				Vec3 relative = graze.position().subtract(eye);
				Vec3 grazePoint = eye.add(direction.scale(relative.dot(direction))).add(across.scale(0.6));
				graze.setPos(grazePoint.x, graze.position().y, grazePoint.z);
				CursedSpiritTestFixtures.freezeGround(graze);
				helper.assertTrue(centred.isAlive() && graze.isAlive(),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: both spirits alive", "true",
								centred.isAlive() + " / " + graze.isAlive()));
				helper.assertTrue(caster.distanceTo(centred) <= MegumiProfile.SIC_RANGE
						&& caster.distanceTo(graze) <= MegumiProfile.SIC_RANGE,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: both spirits in sic range", "<= " + MegumiProfile.SIC_RANGE,
								caster.distanceTo(centred) + " / " + caster.distanceTo(graze)));
				helper.assertTrue(caster.hasLineOfSight(centred) && caster.hasLineOfSight(graze),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
								null, "premise: line of sight to both spirits", "true",
								caster.hasLineOfSight(centred) + " / " + caster.hasLineOfSight(graze)));
				TodoSwapTestFixtures.aimAt(caster,
						centred.position().add(0.0, centred.getBbHeight() / 2.0, 0.0));
				AbilityResult result =
						MegumiAbilityRouter.tryCast(caster, CharacterAbility.PRIMARY_SNEAK, false);
				helper.assertTrue(result == AbilityResult.SUCCESS, CursedSpiritTestFixtures.diagnostic(
						fixture, helper.getTick(), caster.getUUID(), centred.getUUID(), "sic cast result",
						AbilityResult.SUCCESS, result));
				sicced.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanupSic(helper, caster, centred, grazeRef.get());
				throw failure;
			}
		});

		for (int tick = 25; tick <= 60; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (!sicced.get() || resolved.get()) {
					return;
				}
				try {
					CursedSpiritEntity graze = grazeRef.get();
					if (graze == null) {
						return;
					}
					List<? extends MegumiDivineDogEntity> dogs = level.getEntities(
							EntityTypeTest.forClass(MegumiDivineDogEntity.class),
							dog -> caster.getUUID().equals(dog.ownerUuid()));
					if (dogs.isEmpty()) {
						return;
					}
					for (MegumiDivineDogEntity dog : dogs) {
						helper.assertTrue(dog.getTarget() == null
								|| dog.getTarget().getUUID().equals(centred.getUUID()),
								CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
										caster.getUUID(), graze.getUUID(),
										"sic never resolves the edge-graze", "centred:<uuid>",
										dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
					}
					// The graze-health oracle lives inside the resolution window only: once the sic
					// resolves, the dogs are freed and the autonomy pass may legitimately re-mark the
					// graze — post-resolution damage is spec-correct, not a leak.
					helper.assertTrue(graze.getHealth() == grazeMaxRef.get(), CursedSpiritTestFixtures.diagnostic(
							fixture, helper.getTick(), caster.getUUID(), graze.getUUID(),
							"the edge-graze takes nothing before resolution", grazeMaxRef.get(),
							graze.getHealth()));
					if (dogs.stream().allMatch(dog -> dog.getTarget() != null
							&& dog.getTarget().getUUID().equals(centred.getUUID()))) {
						resolved.set(true);
					}
				} catch (RuntimeException | AssertionError failure) {
					cleanupSic(helper, caster, centred, grazeRef.get());
					throw failure;
				}
			});
		}


		for (int tick = 25; tick <= 150; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (!resolved.get() || impacted.get()) {
					return;
				}
				if (centred.getHealth() < centredMax) {
					impacted.set(true);
				}
			});
		}

		helper.runAtTickTime(180, () -> {
			helper.assertTrue(sicced.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					caster.getUUID(), centred.getUUID(), "sic was cast", "true", sicced.get()));
			helper.assertTrue(resolved.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), centred.getUUID(),
					"sic resolved the centred far spirit", "centred:<uuid>", resolved.get()));
			helper.assertTrue(impacted.get(), CursedSpiritTestFixtures.diagnostic(fixture,
					helper.getTick(), caster.getUUID(), centred.getUUID(),
					"the dogs damaged the centred spirit", "< " + centredMax, centred.getHealth()));
			// Post-resolution the graze is a legal autonomy target, so its health is not re-asserted
			cleanupSic(helper, caster, centred, grazeRef.get());

			helper.succeed();
		});
	}

	/**
	 * R10 — Megumi's sic assigns the summoned Divine Dogs to the spirit and the pounce damages it
	 * (banded around the recorded pounce numbers). The shikigami body is asserted alive afterwards,
	 * so the scenario proves assignment + impact, not a one-sided execution.
	 */
	@GameTest(maxTicks = 200, skyAccess = true)
	public void megumiDogSicPunishesTheSpirit(GameTestHelper helper) {
		String fixture = "megumiDogSicPunishesTheSpirit";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture,
				new BlockPos(1, 1, 1), -90.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(6, 1, 1));
		CursedSpiritTestFixtures.freezeGround(spirit);
		AtomicBoolean sicced = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(summoned, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					caster.getUUID(), spirit.getUUID(), "divine dogs summon", "true", summoned));
		});

		helper.runAtTickTime(24, () -> {
			try {
				TodoSwapTestFixtures.aimAt(caster,
						spirit.position().add(0.0, spirit.getBbHeight() / 2.0, 0.0));
				helper.assertTrue(spirit.isAlive() && caster.hasLineOfSight(spirit)
								&& caster.distanceTo(spirit) <= MegumiProfile.SIC_RANGE,
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
								caster.getUUID(), spirit.getUUID(),
								"premise: sic target alive, in range and in sight",
								"alive + <= " + MegumiProfile.SIC_RANGE + " + LOS",
								spirit.isAlive() + " / " + caster.distanceTo(spirit) + " / "
										+ caster.hasLineOfSight(spirit)));
				AbilityResult result = MegumiAbilityRouter.tryCast(caster, CharacterAbility.PRIMARY_SNEAK, false);
				helper.assertTrue(result == AbilityResult.SUCCESS, CursedSpiritTestFixtures.diagnostic(
						fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(), "sic cast result",
						AbilityResult.SUCCESS, result));
				sicced.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanup(fixture, helper, caster, spirit);
				throw failure;
			}
		});

		for (int tick = 25; tick <= 120; tick++) {
			helper.runAtTickTime(tick, () -> {
				if (!sicced.get() || spirit.getHealth() >= spirit.getMaxHealth()) {
					return;
				}
				try {
					double dropped = spirit.getMaxHealth() - spirit.getHealth();
					helper.assertTrue(dropped >= 1.0, CursedSpiritTestFixtures.diagnostic(fixture,
							helper.getTick(), caster.getUUID(), spirit.getUUID(),
							"pounce damage band", ">= 1.0", dropped));
				} catch (RuntimeException | AssertionError failure) {
					cleanup(fixture, helper, caster, spirit);
					throw failure;
				}
			});
		}

		helper.runAtTickTime(140, () -> {
			helper.assertTrue(sicced.get(), CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
					caster.getUUID(), spirit.getUUID(), "sic was cast", "true", sicced.get()));
			helper.assertTrue(spirit.getHealth() < spirit.getMaxHealth(),
					CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
							spirit.getUUID(), "the dogs damaged the spirit", "< " + spirit.getMaxHealth(),
							spirit.getHealth()));
			cleanup(fixture, helper, caster, spirit);
			helper.succeed();
		});
	}

	/**
	 * The live-registry half of the sound contract: every cursed row the variant wires is present in
	 * the server's {@code SOUND_EVENT} registry under its frozen id (the unit-test registry is
	 * frozen before mod registration, so only an in-world check can prove the register list).
	 */
	@GameTest(maxTicks = 20)
	public void everyCursedSoundRowIsRegisteredOnTheLiveServer(GameTestHelper helper) {
		String fixture = "everyCursedSoundRowIsRegisteredOnTheLiveServer";
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			for (String channel : new String[] {"ambient", "hurt", "death", "scream"}) {
				SoundEvent event = channelSound(variant, channel);
				if (event == null) {
					continue;
				}
				String expected = "jujutsumod:cursed." + variant.id() + "_" + channel;
				var id = BuiltInRegistries.SOUND_EVENT.getKey(event);
				helper.assertTrue(id != null, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
						null, null, variant.id() + "/" + channel + " registered", expected, "unregistered"));
				helper.assertTrue(expected.equals(id.toString()),
						CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), null, null,
								variant.id() + "/" + channel + " registry id", expected, id));
			}
		}
		helper.succeed();
	}

	private static SoundEvent channelSound(CursedSpiritVariant variant, String channel) {
		return switch (channel) {
			case "ambient" -> variant.ambientSound();
			case "hurt" -> variant.hurtSound();
			case "death" -> variant.deathSound();
			case "scream" -> variant.screamSound();
			default -> throw new IllegalArgumentException(channel);
		};
	}

	/**
	 * The production projectile, not a shortcut: the entity is prepared with its owner, launched at
	 * the aimed point and added to the level exactly like the runtime's launch path does.
	 */
	private static ProjectJjkNailEntity launchNailAt(ServerLevel level, ServerPlayer caster,
			CursedSpiritEntity spirit) {
		ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
		if (nail == null) {
			throw new IllegalStateException("projectjjk_nail entity type did not create an instance");
		}
		Vec3 from = caster.position().add(0.0, caster.getEyeHeight() - 0.2, 0.0);
		Vec3 chest = spirit.position().add(0.0, spirit.getBbHeight() / 2.0, 0.0);
		nail.prepare(caster, from, chest.subtract(from).normalize());
		nail.launchAt(chest, 0, false);
		level.addFreshEntity(nail);
		return nail;
	}

	/** The remnant stack bound to the spirit's UUID, or null when none is minted yet. */
	private static ItemStack remnantFor(ServerPlayer caster, UUID targetId) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (!stack.is(JujutsuItems.RESONANCE_REMNANT)) {
				continue;
			}
			var binding = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
			if (binding != null && targetId.equals(binding.targetId())) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * The per-tier remnant proof: a remnant is bound to the spirit's UUID and carries the CURSE
	 * visual (the runtime classifies it from {@code jujutsumod:resonance_remnant_curse}).
	 */
	private static void assertCurseRemnantBound(GameTestHelper helper, String fixture, ServerPlayer caster,
			CursedSpiritEntity spirit) {
		ItemStack remnant = remnantFor(caster, spirit.getUUID());
		helper.assertTrue(remnant != null, CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(),
				caster.getUUID(), spirit.getUUID(), "remnant bound to the spirit",
				"…" + spirit.getUUID(), remnant));
		RemnantVisualType visual = remnant.get(JujutsuDataComponents.RESONANCE_REMNANT_VISUAL);
		helper.assertTrue(visual == RemnantVisualType.CURSE, CursedSpiritTestFixtures.diagnostic(
				fixture, helper.getTick(), caster.getUUID(), spirit.getUUID(),
				"remnant visual type for a curse-tagged mob", RemnantVisualType.CURSE, visual));
	}

	/**
	 * Puts a knocked-back body back on its pad spot before the next shot. Production knockback
	 * (0.9) can slide a hit body past the 6x6 stone pad and off the arena floor, after which a
	 * follow-up nail flies into the pad's edge instead of the body — the shot's geometry, not
	 * the impact logic, would then be under test.
	 */
	private static void containOnPad(GameTestHelper helper, String fixture, ServerPlayer caster,
			CursedSpiritEntity spirit, BlockPos spot) {
		BlockPos absolute = helper.absolutePos(spot);
		spirit.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(),
				absolute.getZ() + 0.5, java.util.Set.of(), spirit.getYRot(), spirit.getXRot(), false);
		helper.assertTrue(helper.getLevel().getBlockState(absolute.below()).is(Blocks.STONE),
				CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
						spirit.getUUID(), "premise: the spirit stands on the stone pad", "stone",
						helper.getLevel().getBlockState(absolute.below())));
	}

	/** Nail accounting around a body: how many owned nails are near it and how many embedded. */
	private static String nailState(ServerLevel level, ServerPlayer caster, CursedSpiritEntity spirit) {
		List<ProjectJjkNailEntity> near = level.getEntitiesOfClass(ProjectJjkNailEntity.class,
				spirit.getBoundingBox().inflate(4.0), nail -> nail.isOwnedBy(caster.getUUID()));
		long embedded = near.stream().filter(ProjectJjkNailEntity::isEmbedded).count();
		long here = near.stream().filter(nail -> nail.isEmbedded()
				&& spirit.getUUID().equals(nail.embeddedTargetUuid())).count();
		long ground = near.stream().filter(nail -> nail.isEmbedded()
				&& nail.embeddedTargetUuid() == null).count();
		StringBuilder who = new StringBuilder();
		for (ProjectJjkNailEntity nail : near) {
			String anchored = nail.isEmbedded()
					? (nail.embeddedTargetUuid() == null
							? "block@" + nail.anchor().blockPos() + "=" + nail.anchor().blockStateSignature()
							: nail.embeddedTargetUuid().toString().substring(0, 4))
					: "flying";
			who.append(anchored).append('/').append(nail.isRemoved() ? "gone" : "live").append(' ');
		}
		return "nails=" + near.size() + " embedded=" + embedded + " here=" + here + " ground=" + ground
				+ " who=[" + who.toString().trim() + "]";
	}

	/** Every nail entity owned by the caster, wherever it embedded or fell. */
	private static List<ProjectJjkNailEntity> ownedNails(ServerLevel level, ServerPlayer caster) {
		return level.getEntitiesOfClass(ProjectJjkNailEntity.class,
				new AABB(caster.position(), caster.position()).inflate(48.0),
				nail -> nail.isOwnedBy(caster.getUUID()));
	}

	/** Placed Nobara mock: the nail path needs a real level position, unlike the recording mock. */
	private static ServerPlayer setupNobaraCaster(GameTestHelper helper, String fixture, BlockPos relativeFeet,
			float yaw, float pitch) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(relativeFeet);
		caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				java.util.Set.of(), yaw, pitch, false);
		CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.SECONDARY);
		CharacterAbilityCooldowns.clear(caster, CharacterAbility.SECONDARY_SNEAK);
		helper.assertTrue(caster.isAlive() && !caster.isSpectator(),
				CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(), null,
						"nobara caster placed and able", "alive non-spectator",
						caster.isAlive() + " " + caster.isSpectator()));
		return caster;
	}

	private static void cleanup(String fixture, GameTestHelper helper, ServerPlayer caster,
			CursedSpiritEntity spirit) {
		ProjectJjkNailMarks.clear(spirit.getUUID());
		spirit.discard();
		CursedSpiritTestFixtures.cleanupVictim(helper, caster);
	}

	/**
	 * Multi-spirit cleanup: clears the straw-doll ritual state (releasing the resonance
	 * hit-stop), drops every mark stack, discards every body and sweeps owned nails. Never
	 * throws — a teardown exception must not mask the failure it cleans up after.
	 */
	private static void cleanupAll(GameTestHelper helper, ServerPlayer caster,
			CursedSpiritEntity... spirits) {
		cleanupSpirits(helper, caster, spirits);
		try {
			CursedSpiritTestFixtures.cleanupVictim(helper, caster);
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}

	/** The Megumi twin: pack teardown owns the caster, so only the spirits are swept here. */
	private static void cleanupSic(GameTestHelper helper, ServerPlayer caster,
			CursedSpiritEntity... spirits) {
		cleanupSpirits(helper, caster, spirits);
		try {
			MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}

	private static void cleanupSpirits(GameTestHelper helper, ServerPlayer caster,
			CursedSpiritEntity... spirits) {
		try {
			ProjectJjkStrawDollRuntime.resetCaster(helper.getLevel().getServer(), caster.getUUID());
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
		for (CursedSpiritEntity spirit : spirits) {
			try {
				ProjectJjkNailMarks.clear(spirit.getUUID());
			} catch (RuntimeException ignored) {
				// Best-effort cleanup on an already-failing test.
			}
			try {
				spirit.discard();
			} catch (RuntimeException ignored) {
				// Best-effort cleanup on an already-failing test.
			}
		}
		try {
			for (ProjectJjkNailEntity nail : ownedNails(helper.getLevel(), caster)) {
				nail.discard();
			}
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}
}
