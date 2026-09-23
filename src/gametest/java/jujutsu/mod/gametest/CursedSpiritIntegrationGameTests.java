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
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailMarks;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;
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
			ProjectJjkStrawDollRuntime.resetCaster(caster.getUUID());
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
			for (ProjectJjkNailEntity nail : helper.getLevel().getEntitiesOfClass(ProjectJjkNailEntity.class,
					new AABB(caster.position(), caster.position()).inflate(48.0),
					nail -> nail.isOwnedBy(caster.getUUID()))) {
				nail.discard();
			}
		} catch (RuntimeException ignored) {
			// Best-effort cleanup on an already-failing test.
		}
	}
}
