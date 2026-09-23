package jujutsu.mod.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;

import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorRegistry;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraProfile;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkResonanceRemnant;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.RemnantVisualType;
import jujutsu.mod.character.nobara.projectjjk.ResonantMomentum;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;

/** Block 4's server gameplay oracles: extraction, fixed-timeline release, and bounded Momentum. */
public final class NobaraResonanceGameTests {
	private static final String FIXTURE_EXTRACTION = "deeplyAnchoredExtractionMintsRemnantPerTier";
	private static final String FIXTURE_RITUAL = "strawDollRitualBurnsMarkedSpiritAndSparesUnmarked";

	public NobaraResonanceGameTests() {}

	/** D10-migrated scenario: Deeply Anchored + overhead setup mints one curse remnant per tier. */
	@GameTest(maxTicks = 120, skyAccess = true)
	public void deeplyAnchoredExtractionMintsRemnantPerTier(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = setupCaster(helper, FIXTURE_EXTRACTION, new BlockPos(1, 1, 1));
		ServerLevel level = helper.getLevel();
		List<CursedSpiritEntity> spirits = List.of(
				CursedSpiritTestFixtures.spawnSpirit(helper, FIXTURE_EXTRACTION, JujutsuEntities.LESSER_CURSED_SPIRIT,
						new BlockPos(4, 1, 1)),
				CursedSpiritTestFixtures.spawnSpirit(helper, FIXTURE_EXTRACTION, JujutsuEntities.CURSED_SPIRIT,
						new BlockPos(6, 1, 3)),
				CursedSpiritTestFixtures.spawnSpirit(helper, FIXTURE_EXTRACTION, JujutsuEntities.GREATER_CURSED_SPIRIT,
						new BlockPos(5, 1, 5)));
		AtomicBoolean extracted = new AtomicBoolean();

		helper.runAtTickTime(2, () -> {
			try {
				for (CursedSpiritEntity spirit : spirits) {
					CursedSpiritTestFixtures.freezeGround(spirit);
					helper.assertTrue(!ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, spirit),
							diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
									"D3 without overhead interaction mints nothing", false, false));
					ProjectJjkNailEntity nail = createDeepAnchor(level, caster, spirit);
					helper.assertTrue(NailAnchorRegistry.isDeeplyAnchored(level, caster.getUUID(), spirit.getUUID()),
							diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
									"three-depth anchor is the extraction premise", true,
									NailAnchorRegistry.isDeeplyAnchored(level, caster.getUUID(), spirit.getUUID())));
					boolean minted = ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, spirit);
					helper.assertTrue(minted, diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
						"overhead extraction mints a remnant", true, minted));
					helper.assertTrue(!ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, spirit),
							diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
									"duplicate extraction is a no-op", false, false));
					ItemStack remnant = remnantFor(caster, spirit.getUUID());
					helper.assertTrue(remnant != null, diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
						"remnant is bound to the target", "present", remnant));
					helper.assertTrue(remnant.get(JujutsuDataComponents.RESONANCE_REMNANT_VISUAL) == RemnantVisualType.CURSE,
						diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
								"curse tier keeps CURSE visual", RemnantVisualType.CURSE,
								remnant.get(JujutsuDataComponents.RESONANCE_REMNANT_VISUAL)));
					helper.assertTrue(caster.getInventory().contains(new ItemStack(JujutsuItems.HAIRPIN_NAIL)),
						diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
								"extraction does not consume the nail setup", true,
								caster.getInventory().contains(new ItemStack(JujutsuItems.HAIRPIN_NAIL))));
					// Keep a direct reference so the entity stays loaded while the registry is queried.
					helper.assertTrue(nail.isEmbedded(), diagnostic(FIXTURE_EXTRACTION, helper, caster, spirit,
						"setup nail remains embedded", true, nail.isEmbedded()));
				}
				extracted.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanup(helper, caster, spirits.toArray(new CursedSpiritEntity[0]));
				throw failure;
			}
		});

		helper.runAtTickTime(80, () -> {
			helper.assertTrue(extracted.get(), diagnostic(FIXTURE_EXTRACTION, helper, caster, null,
					"all tiers extracted before final oracle", true, extracted.get()));
			cleanup(helper, caster, spirits.toArray(new CursedSpiritEntity[0]));
			helper.succeed();
		});
	}

	/** D10-migrated ritual scenario plus the non-vacuous fixed-tick-rate and ticking-bystander oracles. */
	@GameTest(maxTicks = 180, skyAccess = true)
	public void strawDollRitualBurnsMarkedSpiritAndSparesUnmarked(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		ServerPlayer caster = setupCaster(helper, FIXTURE_RITUAL, new BlockPos(1, 1, 1));
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity marked = CursedSpiritTestFixtures.spawnSpirit(helper, FIXTURE_RITUAL,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(6, 1, 1));
		CursedSpiritEntity control = CursedSpiritTestFixtures.spawnSpirit(helper, FIXTURE_RITUAL,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(3, 1, 4));
		AtomicBoolean extracted = new AtomicBoolean();
		AtomicBoolean started = new AtomicBoolean();
		AtomicReference<Double> markedHpBefore = new AtomicReference<>(0.0);
		AtomicReference<Double> controlHpBefore = new AtomicReference<>(0.0);
		AtomicReference<Integer> controlTicksBefore = new AtomicReference<>(0);
		AtomicReference<Integer> remnantCountBefore = new AtomicReference<>(0);
		AtomicReference<Integer> nailCountBefore = new AtomicReference<>(0);

		helper.runAtTickTime(2, () -> {
			try {
				CursedSpiritTestFixtures.freezeGround(marked);
				CursedSpiritTestFixtures.freezeGround(control);
				createDeepAnchor(level, caster, marked);
				extracted.set(ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, marked));
				helper.assertTrue(extracted.get(), diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"extraction succeeded before ritual sampling", true, extracted.get()));
				ItemStack remnant = remnantFor(caster, marked.getUUID());
				helper.assertTrue(remnant != null, diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"ritual has a live bound remnant", "present", remnant));
				caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
				caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(JujutsuItems.STRAW_DOLL));
				markedHpBefore.set((double) marked.getHealth());
				controlHpBefore.set((double) control.getHealth());
				controlTicksBefore.set(control.tickCount);
				remnantCountBefore.set(countRemnants(caster));
				nailCountBefore.set(countNails(caster));
				AbilityResult result = ProjectJjkStrawDollRuntime.tryStartResonance(caster);
				helper.assertTrue(result == AbilityResult.SUCCESS, diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"straw-doll ritual starts", AbilityResult.SUCCESS, result));
				started.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanup(helper, caster, marked, control);
				throw failure;
			}
		});

		for (int tick = 3; tick <= 70; tick++) {
			final int sample = tick;
			helper.runAtTickTime(sample, () -> {
				if (!started.get()) {
					return;
				}
				float rate = level.getServer().tickRateManager().tickrate();
				helper.assertTrue(rate == 20.0f, diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"server tick rate stays normal during authored ritual (sample " + sample + ")", 20.0f, rate));
			});
		}

		helper.runAtTickTime(42, () -> {
			helper.assertTrue(extracted.get() && started.get(), diagnostic(FIXTURE_RITUAL, helper, caster, marked,
					"ritual was proven running before tick-rate oracle", "extracted + started",
					 extracted.get() + " + " + started.get()));
			helper.assertTrue(marked.getHealth() < markedHpBefore.get(), diagnostic(FIXTURE_RITUAL, helper, caster, marked,
					"release damages the marked spirit", "< " + markedHpBefore.get(), marked.getHealth()));
			helper.assertTrue(control.getHealth() == controlHpBefore.get(), diagnostic(FIXTURE_RITUAL, helper, caster, control,
					"unmarked control stays untouched", controlHpBefore.get(), control.getHealth()));
			helper.assertTrue(control.tickCount > controlTicksBefore.get(), diagnostic(FIXTURE_RITUAL, helper, caster, control,
					"second loaded entity continues ticking", "> " + controlTicksBefore.get(), control.tickCount));
			helper.assertTrue(countRemnants(caster) == remnantCountBefore.get() - 1,
				diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"one remnant consumed at release", remnantCountBefore.get() - 1, countRemnants(caster)));
			helper.assertTrue(countNails(caster) == nailCountBefore.get() - 1,
				diagnostic(FIXTURE_RITUAL, helper, caster, marked,
						"one nail consumed at release", nailCountBefore.get() - 1, countNails(caster)));
			helper.assertTrue(ResonantMomentum.isActive(caster), diagnostic(FIXTURE_RITUAL, helper, caster, marked,
					"release grants execution Momentum", true, ResonantMomentum.isActive(caster)));
		});

		helper.runAtTickTime(150, () -> {
			// Mock players never tick their effect map, so wall-clock expiry is unobservable here;
			// the contract this oracle can prove is that the grant is a FINITE, bounded instance.
			var momentumInstance = caster.getEffect(jujutsu.mod.registry.JujutsuEffects.RESONANT_MOMENTUM);
			helper.assertTrue(momentumInstance != null
							&& momentumInstance.getDuration() <= ProjectJjkNobaraProfile.MOMENTUM_WINDOW_TICKS
							&& momentumInstance.getDuration() > 0,
					diagnostic(FIXTURE_RITUAL, helper, caster, marked,
							"execution Momentum is a bounded finite window", "<= "
									+ ProjectJjkNobaraProfile.MOMENTUM_WINDOW_TICKS,
							momentumInstance == null ? "none" : momentumInstance.getDuration()));
			cleanup(helper, caster, marked, control);
			helper.succeed();
		});
	}

	/** Target loss after t0 must fizzle at release and preserve both resources. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void resonanceTargetLostAfterStartFizzlingConsumesNothing(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		String fixture = "resonanceTargetLostAfterStartFizzlingConsumesNothing";
		ServerPlayer caster = setupCaster(helper, fixture, new BlockPos(1, 1, 1));
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity target = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(6, 1, 1));
		AtomicBoolean started = new AtomicBoolean();
		AtomicReference<Integer> nails = new AtomicReference<>(0);
		AtomicReference<Integer> remnants = new AtomicReference<>(0);

		helper.runAtTickTime(2, () -> {
			try {
				CursedSpiritTestFixtures.freezeGround(target);
				createDeepAnchor(level, caster, target);
				helper.assertTrue(ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, target),
						diagnostic(fixture, helper, caster, target, "extraction premise", true, true));
				caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
				caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(JujutsuItems.STRAW_DOLL));
				nails.set(countNails(caster));
				remnants.set(countRemnants(caster));
				AbilityResult result = ProjectJjkStrawDollRuntime.tryStartResonance(caster);
				helper.assertTrue(result == AbilityResult.SUCCESS, diagnostic(fixture, helper, caster, target,
						"ritual starts before target loss", AbilityResult.SUCCESS, result));
				started.set(true);
			} catch (RuntimeException | AssertionError failure) {
				cleanup(helper, caster, target);
				throw failure;
			}
		});
		helper.runAtTickTime(12, target::discard);
		for (int tick = 13; tick <= 60; tick++) {
			final int sample = tick;
			helper.runAtTickTime(sample, () -> {
				if (!started.get()) return;
				helper.assertTrue(level.getServer().tickRateManager().tickrate() == 20.0f,
						diagnostic(fixture, helper, caster, target,
								"fizzle keeps server ticking normally (sample " + sample + ")", 20.0f,
								level.getServer().tickRateManager().tickrate()));
			});
		}
		helper.runAtTickTime(70, () -> {
			helper.assertTrue(started.get(), diagnostic(fixture, helper, caster, null,
					"target-loss scenario started", true, started.get()));
			helper.assertTrue(countNails(caster) == nails.get(), diagnostic(fixture, helper, caster, null,
					"target loss consumes no nail", nails.get(), countNails(caster)));
			helper.assertTrue(countRemnants(caster) == remnants.get(), diagnostic(fixture, helper, caster, null,
					"target loss consumes no remnant", remnants.get(), countRemnants(caster)));
			cleanup(helper, caster, target);
			helper.succeed();
		});
	}

	/** Same-dimension loaded entities beyond the removed 64-block cutoff remain valid ritual targets. */
	@GameTest(maxTicks = 100, skyAccess = true)
	public void sameDimensionRemoteTargetBeyondLegacyRangeResonates(GameTestHelper helper) {
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		String fixture = "sameDimensionRemoteTargetBeyondLegacyRangeResonates";
		ServerPlayer caster = setupCaster(helper, fixture, new BlockPos(1, 1, 1));
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity target = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.CURSED_SPIRIT, new BlockPos(6, 1, 1));
		AtomicReference<Double> before = new AtomicReference<>(0.0);
		helper.runAtTickTime(2, () -> {
			try {
				CursedSpiritTestFixtures.freezeGround(target);
			target.teleportTo(level, caster.getX() + 70.0, caster.getY(), caster.getZ(), java.util.Set.of(), 0.0f, 0.0f, false);
			// The +70 landing sits outside the arena's force-loaded region; without this the nail's
			// addFreshEntity defers registration and the anchor track silently fails (flake).
			level.getChunkAt(target.blockPosition());
				helper.assertTrue(caster.distanceTo(target) > 64.0, diagnostic(fixture, helper, caster, target,
						"target is beyond the removed range cutoff", "> 64", caster.distanceTo(target)));
				createDeepAnchor(level, caster, target);
				helper.assertTrue(ProjectJjkStrawDollRuntime.tryExtractRemnant(caster, target),
						diagnostic(fixture, helper, caster, target, "remote extraction succeeds", true, true));
				caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
				caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(JujutsuItems.STRAW_DOLL));
				before.set((double) target.getHealth());
				AbilityResult result = ProjectJjkStrawDollRuntime.tryStartResonance(caster);
				helper.assertTrue(result == AbilityResult.SUCCESS,
						diagnostic(fixture, helper, caster, target, "remote ritual starts", AbilityResult.SUCCESS, result));
			} catch (RuntimeException | AssertionError failure) {
				cleanup(helper, caster, target);
				throw failure;
			}
		});
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(target.getHealth() < before.get(), diagnostic(fixture, helper, caster, target,
					"same-dimension remote release damages target", "< " + before.get(), target.getHealth()));
			cleanup(helper, caster, target);
			helper.succeed();
		});
	}

	private static ServerPlayer setupCaster(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		BlockPos absolute = helper.absolutePos(feet);
		caster.teleportTo(helper.getLevel(), absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5,
				java.util.Set.of(), -90.0f, 0.0f, false);
		CharacterSelectionManager.select(caster, JujutsuCharacter.NOBARA);
		caster.getInventory().add(new ItemStack(JujutsuItems.HAIRPIN_NAIL, 4));
		helper.assertTrue(caster.isAlive() && !caster.isSpectator(),
				diagnostic(fixture, helper, caster, null, "Nobara caster is live", "alive", caster.isAlive()));
		return caster;
	}

	private static ProjectJjkNailEntity createDeepAnchor(ServerLevel level, ServerPlayer caster, LivingEntity target) {
		ProjectJjkNailEntity nail = JujutsuEntities.PROJECTJJK_NAIL.create(level, EntitySpawnReason.COMMAND);
		if (nail == null) {
			throw new IllegalStateException("projectjjk_nail entity type did not create an instance");
		}
		Vec3 point = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		nail.prepare(caster, caster.getEyePosition(), point.subtract(caster.getEyePosition()).normalize());
		nail.attachToEntity(target, point);
		if (!level.addFreshEntity(nail)) {
			throw new IllegalStateException("deep anchor nail could not be added to the test level");
		}
		if (!nail.deepen() || !nail.deepen()) {
			throw new IllegalStateException("test nail could not reach depth three");
		}
		if (!NailAnchorRegistry.track(level, nail)) {
			throw new IllegalStateException("deep anchor nail was rejected by NailAnchorRegistry");
		}
		NailAnchorRegistry.updateDepth(level, nail.getUUID(), 3);
		return nail;
	}

	private static ItemStack remnantFor(ServerPlayer caster, UUID targetId) {
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			ProjectJjkResonanceRemnant binding = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
			if (stack.is(JujutsuItems.RESONANCE_REMNANT) && binding != null && targetId.equals(binding.targetId())) {
				return stack;
			}
		}
		return null;
	}

	private static int countRemnants(ServerPlayer caster) {
		int count = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			if (caster.getInventory().getItem(slot).is(JujutsuItems.RESONANCE_REMNANT)) count++;
		}
		return count;
	}

	private static int countNails(ServerPlayer caster) {
		int count = 0;
		for (int slot = 0; slot < caster.getInventory().getContainerSize(); slot++) {
			ItemStack stack = caster.getInventory().getItem(slot);
			if (stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL)) count += stack.getCount();
		}
		return count;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer caster, CursedSpiritEntity... spirits) {
		ProjectJjkStrawDollRuntime.resetCaster(caster.getUUID());
		NobaraHammerCombatRuntime.clearPlayer(caster.getUUID());
		for (CursedSpiritEntity spirit : spirits) {
			if (spirit != null) spirit.discard();
		}
		for (ProjectJjkNailEntity nail : helper.getLevel().getEntitiesOfClass(ProjectJjkNailEntity.class,
				caster.getBoundingBox().inflate(128.0), entity -> entity.isOwnedBy(caster.getUUID()))) {
			nail.discard();
		}
		CursedSpiritTestFixtures.cleanupVictim(helper, caster);
	}

	private static net.minecraft.network.chat.Component diagnostic(String fixture, GameTestHelper helper,
			ServerPlayer caster, LivingEntity target, String what, Object expected, Object actual) {
		return CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), caster.getUUID(),
				target == null ? null : target.getUUID(), what, expected, actual);
	}
}
