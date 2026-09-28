package jujutsu.mod.gametest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiFailureMemory;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiRabbitEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiRuntime;
import jujutsu.mod.character.megumi.MegumiShikigamiSelection;
import jujutsu.mod.character.megumi.MegumiSummonCooldowns;
import jujutsu.mod.character.megumi.MegumiSummonRuntime;

/**
 * Coexistence scenarios (issue #107 D1), block B1: several of Megumi's packs out at once, per-type
 * recall prices, the global sic command and its cancel reading, a manual order outliving the
 * owner's attacker, and the owner isolation that keeps two casters from sharing a field — exercised
 * through the production runtime calls {@code tryPrimary} / {@code trySic} that the vessel router
 * reaches for the PRIMARY / PRIMARY_SNEAK slots.
 *
 * <p><b>Traps avoided.</b> World offset is random per run, so nothing asserts absolute positions:
 * bodies are found by owner-UUID scan ({@link MegumiShikigamiTestFixtures#nueOwnedBy}) or by the
 * owner's own living list, never by bounds. Each summon step sits on its own tick — the runtime
 * drops same-tick duplicate technique presses per type — and every sic waits past the 16-tick
 * materialization ({@code acceptsSicCommand} is inert before it). The caster faces +Z and the pad
 * covers the whole arena, so both ground packs settle on the deliberate spots the placement search
 * tries first — {@code owner ± right * 1.5}, i.e. the ±X axis — while every mark sits on the +Z
 * axis: the aim never crosses a friendly body, and a body on the line would resolve as the aim's
 * own target and be refused as an own-summon mark. Static state (selection map, both pack maps,
 * both cooldown slots, the per-type summon map) is cleared in setup and on every success/failure
 * path.
 */
public final class MegumiCoexistenceGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required (private would fail entrypoint load).

	private static final int FIRST_SUMMON_TICK = 2;
	private static final int SECOND_SUMMON_TICK = 4;
	private static final int THIRD_SUMMON_TICK = 6;
	/** Past {@code DOG_MATERIALIZATION_TICKS} / {@code NUE_MATERIALIZE_TICKS}: every body is ACTIVE. */
	private static final int SIC_TICK = 26;
	/** Past {@code OX_MATERIALIZE_TICKS} on a tick-4 summon: the ox takes orders by ~27. */
	private static final int NEW_BODY_SIC_TICK = 30;
	private static final int RECALL_TICK = 30;

	/**
	 * C1 — all three groups stand together: the Divine Dogs, Nue and the Toad are out at once, each
	 * with its own pack record, and the technique key never charged the shared PRIMARY slot for any
	 * of them. This is the shape issue #107 exists for; before it, the second summon swapped the
	 * first one off the field.
	 */
	@GameTest(maxTicks = 80)
	public void dogsNueAndToadStandTogether(GameTestHelper helper) {
		String fixture = "dogsNueAndToadStandTogether";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layPad(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean nue = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(nue, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"nue", helper.getTick(), ownerId, "nue tryPrimary result", "true", nue));
		}));

		helper.runAtTickTime(THIRD_SUMMON_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
				boolean summoned = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(summoned, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"toad", helper.getTick(), ownerId, "toad tryPrimary result", "true", summoned));

				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.equals(List.of(MegumiShikigami.NUE.id(), MegumiShikigami.TOAD.id())),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "coexist", helper.getTick(), ownerId,
								"shikigami pack types (enum order)", List.of("nue", "toad"), types));
				boolean dogsOut = MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent();
				helper.assertTrue(dogsOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "dog pack present", "present", "absent"));

				int bodies = MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId).size();
				helper.assertTrue(bodies == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "living shikigami bodies (nue + toad)", "2", bodies));
				int dogs = MegumiSummonRuntime.livingDogs(level.getServer(), ownerId).size();
				helper.assertTrue(dogs == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "living dogs", "2", dogs));

				int primary = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY);
				helper.assertTrue(primary == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "PRIMARY slot (summons arm nothing)", "0", primary));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(40, () -> helper.succeed());
	}

	/**
	 * C1b — the issue #107 ceiling, exercised: every pack Megumi can field is out at once — the
	 * Divine Dogs on their own runtime plus all eight shikigami types on the per-type map. Each
	 * pack record is a distinct row, every body stands under the same owner, and no summon
	 * corrupted a sibling's record.
	 */
	@GameTest(maxTicks = 100)
	public void allNineTypesCoexistWithoutCorruption(GameTestHelper helper) {
		String fixture = "allNineTypesCoexistWithoutCorruption";
		BlockPos casterFeet = new BlockPos(3, 1, 3);
		layPad(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		// One press per tick — the runtime drops same-tick duplicate technique presses per type.
		MegumiShikigami[] shikigami = {MegumiShikigami.NUE, MegumiShikigami.TOAD,
				MegumiShikigami.RABBITS, MegumiShikigami.ELEPHANT, MegumiShikigami.SERPENT,
				MegumiShikigami.DEER, MegumiShikigami.OX, MegumiShikigami.TIGER};
		for (int i = 0; i < shikigami.length; i++) {
			final MegumiShikigami type = shikigami[i];
			helper.runAtTickTime(SECOND_SUMMON_TICK + 2 * i,
					() -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
						MegumiShikigamiSelection.set(caster.getUUID(), type);
						boolean out = MegumiShikigamiRuntime.tryPrimary(caster, false);
						helper.assertTrue(out, MegumiShikigamiTestFixtures.diagnostic(fixture,
								type.id(), helper.getTick(), caster.getUUID(),
								type.id() + " tryPrimary result", "true", out));
					}));
		}

		helper.runAtTickTime(50, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<String> expected = shikigamiPackTypes(shikigami);
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.equals(expected), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId,
						"all eight shikigami pack types (enum order)", expected, types));
				boolean dogsOut = MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent();
				helper.assertTrue(dogsOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "dog pack present", "present", "absent"));

				List<MegumiShikigamiEntity> bodies =
						MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId);
				helper.assertTrue(bodies.size() >= 8, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId,
						"living shikigami bodies (>= 1 per type)", ">=8", bodies.size()));
				int dogs = MegumiSummonRuntime.livingDogs(level.getServer(), ownerId).size();
				helper.assertTrue(dogs == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"coexist", helper.getTick(), ownerId, "living dogs", "2", dogs));

				// No corruption: no body mistook a sibling pack body for an enemy mark.
				for (MegumiShikigamiEntity body : bodies) {
					helper.assertTrue(body.getTarget() == null, MegumiShikigamiTestFixtures.diagnostic(
							fixture, "coexist", helper.getTick(), ownerId,
							"no shikigami body carries a mark", "null",
							String.valueOf(body.getTarget())));
				}
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(60, () -> helper.succeed());
	}

	/**
	 * C2 — pressing the key on a type that is already out recalls exactly that type: the Nue pack
	 * goes, the dogs and the Toad keep fighting, and only the Nue rows of the per-type summon map
	 * carry a deadline. Under the shared-slot model this press used to either recall everything or
	 * leave the other types waiting behind one deadline.
	 */
	@GameTest(maxTicks = 80)
	public void recallingOneTypeLeavesTheOtherPacksAlone(GameTestHelper helper) {
		String fixture = "recallingOneTypeLeavesTheOtherPacksAlone";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layPad(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean nue = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(nue, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"nue", helper.getTick(), ownerId, "nue tryPrimary result", "true", nue));
		}));

		helper.runAtTickTime(THIRD_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean toad = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(toad, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"toad", helper.getTick(), ownerId, "toad tryPrimary result", "true", toad));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
				boolean recalled = MegumiShikigamiRuntime.tryPrimary(caster, false);
				helper.assertTrue(recalled, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "nue recall result", "true", recalled));

				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.equals(List.of(MegumiShikigami.TOAD.id())),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "recall", helper.getTick(), ownerId,
								"shikigami packs after the nue recall", List.of("toad"), types));
				boolean dogsOut = MegumiSummonRuntime.packView(level.getServer(), ownerId).isPresent();
				helper.assertTrue(dogsOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "dog pack still out", "present", "absent"));

				long gameTime = level.getGameTime();
				long nueCooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime);
				helper.assertTrue(nueCooldown > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "nue summon cooldown", "> 0", nueCooldown));
				long toadCooldown = MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.TOAD, gameTime);
				helper.assertTrue(toadCooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "toad summon cooldown", "0", toadCooldown));
				long dogCooldown =
						MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.DOGS, gameTime);
				helper.assertTrue(dogCooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"recall", helper.getTick(), ownerId, "dog summon cooldown", "0", dogCooldown));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(50, () -> helper.succeed());
	}

	/**
	 * C3 — one sic command, both families: the aim resolves once and lands on every living body the
	 * owner has, dogs and shikigami alike. The key used to reach only the family of the current
	 * selection, so a coexisting pack stood still while its sibling charged.
	 */
	@GameTest(maxTicks = 120)
	public void oneSicCommandReachesDogsAndShikigami(GameTestHelper helper) {
		String fixture = "oneSicCommandReachesDogsAndShikigami";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos markFeet = new BlockPos(2, 1, 5);
		layPad(helper);
		helper.setBlock(markFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, markFeet);
		mark.setPersistenceRequired();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean toad = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(toad, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"toad", helper.getTick(), ownerId, "toad tryPrimary result", "true", toad));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiDivineDogEntity> dogs = MegumiSummonRuntime.livingDogs(level.getServer(), ownerId);
				List<MegumiShikigamiEntity> bodies =
						MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId);
				helper.assertTrue(dogs.size() == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "living dogs before the sic", "2", dogs.size()));
				helper.assertTrue(bodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "living shikigami before the sic", "1", bodies.size()));
				helper.assertTrue(mark.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "mark alive", "true", mark.isAlive()));

				TodoSwapTestFixtures.aimAt(caster, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));

				for (MegumiDivineDogEntity dog : dogs) {
					helper.assertTrue(dog.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), ownerId, "dog mark", mark.getUUID(),
							dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
				}
				for (MegumiShikigamiEntity body : bodies) {
					helper.assertTrue(body.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), ownerId, "shikigami mark", mark.getUUID(),
							body.getTarget() == null ? "null" : body.getTarget().getUUID()));
				}
				int sneak = CharacterAbilityCooldowns.remainingTicks(caster, CharacterAbility.PRIMARY_SNEAK);
				helper.assertTrue(sneak > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "PRIMARY_SNEAK cooldown (sic price)", "> 0", sneak));
			} finally {
				mark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 4, () -> helper.succeed());
	}

	/**
	 * C3b — the same global sic reaches the new-roster bodies: the dogs AND a post-#107 type (the
	 * ox) are out, the aim resolves once, and both families take the mark — the ox's own row pins
	 * the same getTarget/chargeTargetUuid reading.
	 */
	@GameTest(maxTicks = 120)
	public void globalSicMarksNewBodiesAlongsideOld(GameTestHelper helper) {
		String fixture = "globalSicMarksNewBodiesAlongsideOld";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos markFeet = new BlockPos(2, 1, 5);
		layPad(helper);
		helper.setBlock(markFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, markFeet);
		mark.setPersistenceRequired();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.OX);
			boolean ox = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(ox, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"ox", helper.getTick(), ownerId, "ox tryPrimary result", "true", ox));
		}));

		helper.runAtTickTime(NEW_BODY_SIC_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				List<MegumiDivineDogEntity> dogs = MegumiSummonRuntime.livingDogs(level.getServer(), ownerId);
				List<MegumiOxEntity> oxen = MegumiShikigamiTestFixtures.ownedBy(
						level, ownerId, MegumiOxEntity.class);
				helper.assertTrue(dogs.size() == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "living dogs before the sic", "2", dogs.size()));
				helper.assertTrue(oxen.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "living oxen before the sic", "1", oxen.size()));
				helper.assertTrue(mark.isAlive(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "mark alive", "true", mark.isAlive()));

				TodoSwapTestFixtures.aimAt(caster, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
				boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));

				for (MegumiDivineDogEntity dog : dogs) {
					helper.assertTrue(dog.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"sic", helper.getTick(), ownerId, "dog mark", mark.getUUID(),
							dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
				}
				MegumiOxEntity ox = oxen.get(0);
				boolean oxMarked = ox.getTarget() == mark || mark.getUUID().equals(ox.chargeTargetUuid());
				helper.assertTrue(oxMarked, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "ox took the same mark", mark.getUUID(),
						ox.getTarget() == null ? "null" : String.valueOf(ox.getTarget().getUUID())));
			} finally {
				mark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(NEW_BODY_SIC_TICK + 4, () -> helper.succeed());
	}

	/**
	 * C4 — an aim that names nothing is the cancel order (D5/R9): every MANUAL mark on the field is
	 * dropped and the press reports that it did something. A second empty press has nothing left to
	 * cancel, so it answers false like any other stray aim, and the shared slot was never charged
	 * for either of them.
	 */
	@GameTest(maxTicks = 120)
	public void sicIntoThinAirCancelsTheOwnersManualOrders(GameTestHelper helper) {
		String fixture = "sicIntoThinAirCancelsTheOwnersManualOrders";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos markFeet = new BlockPos(2, 1, 5);
		layPad(helper);
		helper.setBlock(markFeet.below(), Blocks.STONE);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, markFeet);
		mark.setPersistenceRequired();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(caster, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), ownerId, "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.TOAD);
			boolean toad = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(toad, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"toad", helper.getTick(), ownerId, "toad tryPrimary result", "true", toad));
		}));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			TodoSwapTestFixtures.aimAt(caster, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(caster, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), ownerId, "trySic result on a named mark", "true", sicced));
			for (MegumiDivineDogEntity dog : MegumiSummonRuntime.livingDogs(level.getServer(), ownerId)) {
				helper.assertTrue(dog.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "dog holds the manual mark", mark.getUUID(),
						dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
			}
		}));

		helper.runAtTickTime(SIC_TICK + 2, () -> {
			try {
				UUID ownerId = caster.getUUID();
				// Straight up: the aim resolves to the structure, never to an entity.
				TodoSwapTestFixtures.aimAt(caster, caster.position().add(0.5, 30.0, 0.5));
				boolean cleared = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(cleared, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cancel", helper.getTick(), ownerId, "trySic result on the cancel aim", "true", cleared));

				for (MegumiDivineDogEntity dog : MegumiSummonRuntime.livingDogs(level.getServer(), ownerId)) {
					helper.assertTrue(dog.getTarget() == null, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"cancel", helper.getTick(), ownerId, "dog mark after the cancel", "null",
							dog.getTarget()));
				}
				for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), ownerId)) {
					helper.assertTrue(body.getTarget() == null, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"cancel", helper.getTick(), ownerId, "shikigami mark after the cancel", "null",
							body.getTarget()));
				}

				// Nothing left to cancel: the press falls back to the empty-aim no-op it always was.
				boolean again = MegumiShikigamiRuntime.trySic(caster, false);
				helper.assertTrue(!again, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cancel", helper.getTick(), ownerId, "second cancel press with no marks", "false", again));
			} finally {
				mark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(SIC_TICK + 6, () -> helper.succeed());
	}

	/**
	 * C5 — R7, the manual half: a hand-placed order outranks the retaliation pass. The dogs are
	 * holding a manual mark when a fresh attacker hits the owner; the pass may never re-mark a body
	 * that already carries an owner's order, so both dogs keep their mark on the far body and never
	 * turn on the attacker.
	 */
	@GameTest(maxTicks = 160)
	public void manualOrderOutlivesTheOwnersFreshAttacker(GameTestHelper helper) {
		String fixture = "manualOrderOutlivesTheOwnersFreshAttacker";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		BlockPos markFeet = new BlockPos(2, 1, 5);
		BlockPos attackerFeet = new BlockPos(5, 1, 5);
		layPad(helper);
		helper.setBlock(markFeet.below(), Blocks.STONE);
		helper.setBlock(attackerFeet.below(), Blocks.STONE);

		ServerPlayer owner = setupDamageableOwner(helper, fixture, casterFeet);
		ServerLevel level = helper.getLevel();
		// The mark must outlive the measurement window, so it is a body the pack cannot delete in
		// twenty ticks: resistance IV cuts the pack's damage to a fifth, and the NoAI spawn keeps the
		// mark standing exactly where the aim left it.
		Zombie mark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, markFeet);
		mark.setPersistenceRequired();
		mark.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 10000, 4, false, false, false));
		Zombie attacker = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, attackerFeet);
		attacker.setPersistenceRequired();
		AtomicBoolean summoned = new AtomicBoolean();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			MegumiShikigamiSelection.set(owner.getUUID(), MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(owner, false);
			summoned.set(dogs);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"dogs", helper.getTick(), owner.getUUID(), "dog tryToggle result", "true", dogs));
		}));

		helper.runAtTickTime(SIC_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, owner, () -> {
			UUID ownerId = owner.getUUID();
			TodoSwapTestFixtures.aimAt(owner, mark.position().add(0.0, mark.getBbHeight() / 2.0, 0.0));
			boolean sicced = MegumiShikigamiRuntime.trySic(owner, false);
			helper.assertTrue(sicced, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"sic", helper.getTick(), ownerId, "trySic result", "true", sicced));
			for (MegumiDivineDogEntity dog : MegumiSummonRuntime.livingDogs(level.getServer(), ownerId)) {
				helper.assertTrue(dog.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"sic", helper.getTick(), ownerId, "dog holds the manual mark", mark.getUUID(),
						dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
			}
		}));

		helper.runAtTickTime(SIC_TICK + 6, () -> {
			try {
				UUID ownerId = owner.getUUID();
				helper.assertTrue(summoned.get(), MegumiShikigamiTestFixtures.diagnostic(fixture,
						"attack", helper.getTick(), ownerId, "dogs out before the attack", "true", summoned.get()));
				owner.hurtServer(level, level.damageSources().mobAttack(attacker), 1.0f);
				helper.assertTrue(owner.getLastHurtByMob() == attacker,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "attack", helper.getTick(), ownerId,
								"the hit is attributed to the attacker", attacker.getUUID(),
								owner.getLastHurtByMob() == null ? "null"
										: owner.getLastHurtByMob().getUUID()));

				// The retaliation pass runs at the end of the tick the hit lands on, so this first read
				// is the pre-pass baseline and the +16 read is the one the pass has had time to fight.
				assertManualOrdersStand(helper, fixture, ownerId, mark, attacker);
			} catch (RuntimeException | AssertionError failure) {
				attacker.discard();
				mark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				CursedSpiritTestFixtures.cleanupVictim(helper, owner);
				throw failure;
			}
		});

		helper.runAtTickTime(SIC_TICK + 16, () -> {
			try {
				assertManualOrdersStand(helper, fixture, owner.getUUID(), mark, attacker);
			} catch (RuntimeException | AssertionError failure) {
				attacker.discard();
				mark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
				CursedSpiritTestFixtures.cleanupVictim(helper, owner);
				throw failure;
			}
			attacker.discard();
			mark.discard();
			MegumiShikigamiTestFixtures.cleanupCaster(helper, owner);
			CursedSpiritTestFixtures.cleanupVictim(helper, owner);
			helper.succeed();
		});
	}

	/** Every living dog still answers the owner's own order, and none of them answers the attacker. */
	private static void assertManualOrdersStand(GameTestHelper helper, String fixture, UUID ownerId,
			Zombie mark, Zombie attacker) {
		ServerLevel level = helper.getLevel();
		List<MegumiDivineDogEntity> dogs = MegumiSummonRuntime.livingDogs(level.getServer(), ownerId);
		helper.assertTrue(dogs.size() == 2, MegumiShikigamiTestFixtures.diagnostic(fixture,
				"retaliate", helper.getTick(), ownerId, "living dogs", "2", dogs.size()));
		for (MegumiDivineDogEntity dog : dogs) {
			helper.assertTrue(dog.getTarget() == mark, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"retaliate", helper.getTick(), ownerId, "dog keeps the manual mark", mark.getUUID(),
					dog.getTarget() == attacker ? "the attacker" : String.valueOf(dog.getTarget())));
		}
	}

	/**
	 * C6 — two owners, two fields. Each caster's key reaches only their own bodies, each keeps their
	 * own packs, and a recall charged to one owner leaves the other's summon rows untouched.
	 */
	@GameTest(maxTicks = 160)
	public void twoOwnersKeepTheirPacksAndOrdersApart(GameTestHelper helper) {
		String fixture = "twoOwnersKeepTheirPacksAndOrdersApart";
		BlockPos firstFeet = new BlockPos(2, 1, 2);
		BlockPos secondFeet = new BlockPos(5, 1, 6);
		BlockPos firstMarkFeet = new BlockPos(2, 1, 5);
		BlockPos secondMarkFeet = new BlockPos(5, 1, 2);
		layPad(helper);

		ServerPlayer first = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, firstFeet, 0.0f, 0.0f);
		ServerPlayer second = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, secondFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();
		Zombie firstMark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, firstMarkFeet);
		firstMark.setPersistenceRequired();
		Zombie secondMark = GameTestFixtures.spawnMob(helper, fixture, EntityType.ZOMBIE, secondMarkFeet);
		secondMark.setPersistenceRequired();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, first, () -> {
			MegumiShikigamiSelection.set(first.getUUID(), MegumiShikigami.DOGS);
			boolean dogs = MegumiSummonRuntime.tryToggle(first, false);
			helper.assertTrue(dogs, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"first", helper.getTick(), first.getUUID(), "first caster dogs", "true", dogs));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, first, () -> {
			MegumiShikigamiSelection.set(first.getUUID(), MegumiShikigami.TOAD);
			boolean toad = MegumiShikigamiRuntime.tryPrimary(first, false);
			helper.assertTrue(toad, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"first", helper.getTick(), first.getUUID(), "first caster toad", "true", toad));
		}));

		helper.runAtTickTime(THIRD_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, second, () -> {
			MegumiShikigamiSelection.set(second.getUUID(), MegumiShikigami.NUE);
			boolean nue = MegumiShikigamiRuntime.tryPrimary(second, false);
			helper.assertTrue(nue, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"second", helper.getTick(), second.getUUID(), "second caster nue", "true", nue));
		}));

		helper.runAtTickTime(SIC_TICK, () -> {
			try {
				UUID firstId = first.getUUID();
				UUID secondId = second.getUUID();
				TodoSwapTestFixtures.aimAt(first,
						firstMark.position().add(0.0, firstMark.getBbHeight() / 2.0, 0.0));
				helper.assertTrue(MegumiShikigamiRuntime.trySic(first, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), firstId,
								"first caster sic", "true", "false"));
				TodoSwapTestFixtures.aimAt(second,
						secondMark.position().add(0.0, secondMark.getBbHeight() / 2.0, 0.0));
				helper.assertTrue(MegumiShikigamiRuntime.trySic(second, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), secondId,
								"second caster sic", "true", "false"));

				for (MegumiDivineDogEntity dog : MegumiSummonRuntime.livingDogs(level.getServer(), firstId)) {
					helper.assertTrue(dog.getTarget() == firstMark, MegumiShikigamiTestFixtures.diagnostic(
							fixture, "sic", helper.getTick(), firstId, "first caster dog mark",
							firstMark.getUUID(), dog.getTarget() == null ? "null" : dog.getTarget().getUUID()));
				}
				for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), firstId)) {
					helper.assertTrue(body.getTarget() == firstMark, MegumiShikigamiTestFixtures.diagnostic(
							fixture, "sic", helper.getTick(), firstId, "first caster shikigami mark",
							firstMark.getUUID(), body.getTarget() == null ? "null" : body.getTarget().getUUID()));
				}
				for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(level.getServer(), secondId)) {
					helper.assertTrue(body.getTarget() == secondMark, MegumiShikigamiTestFixtures.diagnostic(
							fixture, "sic", helper.getTick(), secondId, "second caster nue mark",
							secondMark.getUUID(), body.getTarget() == null ? "null" : body.getTarget().getUUID()));
				}
				List<String> firstTypes = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), firstId);
				helper.assertTrue(firstTypes.equals(List.of(MegumiShikigami.TOAD.id())),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), firstId,
								"first caster packs", List.of("toad"), firstTypes));
				List<String> secondTypes = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), secondId);
				helper.assertTrue(secondTypes.equals(List.of(MegumiShikigami.NUE.id())),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "sic", helper.getTick(), secondId,
								"second caster packs", List.of("nue"), secondTypes));

				// A recall charged to the first caster must not touch the second caster's rows.
				long gameTime = level.getGameTime();
				helper.assertTrue(
						MegumiSummonCooldowns.remainingTicks(secondId, MegumiShikigami.NUE, gameTime) == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(), secondId,
								"second caster nue cooldown before the recall", "0",
								MegumiSummonCooldowns.remainingTicks(secondId, MegumiShikigami.NUE, gameTime)));
				MegumiShikigamiSelection.set(firstId, MegumiShikigami.TOAD);
				helper.assertTrue(MegumiShikigamiRuntime.tryPrimary(first, false),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "cooldown", helper.getTick(), firstId,
								"first caster toad recall", "true", "false"));
				long toadCooldown =
						MegumiSummonCooldowns.remainingTicks(firstId, MegumiShikigami.TOAD, level.getGameTime());
				helper.assertTrue(toadCooldown > 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cooldown", helper.getTick(), firstId, "first caster toad cooldown", "> 0", toadCooldown));
				long secondCooldown = MegumiSummonCooldowns.remainingTicks(
						secondId, MegumiShikigami.NUE, level.getGameTime());
				helper.assertTrue(secondCooldown == 0, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cooldown", helper.getTick(), secondId, "second caster nue cooldown after it", "0",
						secondCooldown));
				boolean secondNueOut = MegumiShikigamiTestFixtures.hasPack(
						level.getServer(), secondId, MegumiShikigami.NUE);
				helper.assertTrue(secondNueOut, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"cooldown", helper.getTick(), secondId, "second caster nue pack", "kept", "gone"));
			} finally {
				firstMark.discard();
				secondMark.discard();
				MegumiShikigamiTestFixtures.cleanupCaster(helper, first);
				MegumiShikigamiTestFixtures.cleanupCaster(helper, second);
			}
		});
		helper.runAtTickTime(SIC_TICK + 4, () -> helper.succeed());
	}

	/** A player who can actually take the scripted hit (the mock caster's invulnerability gates it). */
	private static ServerPlayer setupDamageableOwner(GameTestHelper helper, String fixture, BlockPos feet) {
		ServerPlayer owner = CursedSpiritTestFixtures.setupVictim(helper, fixture, feet);
		CharacterSelectionManager.select(owner, JujutsuCharacter.MEGUMI);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY);
		CharacterAbilityCooldowns.clear(owner, CharacterAbility.PRIMARY_SNEAK);
		MegumiShikigamiSelection.clear(owner.getUUID());
		return owner;
	}

	/**
	 * R8/R9 (integration #115+#117) — a Rabbit anchor kill is type-scoped twice over: the Nue pack
	 * keeps flying with no cooldown armed, and the failure memory dies with the swept rabbit body,
	 * not with the owner's row. Before the type-scoped teardown the anchor loss nuked every pack
	 * and the owner-keyed memory wiped a sibling's history with it.
	 */
	@GameTest(maxTicks = 80)
	public void rabbitAnchorLossLeavesNuePackAndItsMemoryAlone(GameTestHelper helper) {
		String fixture = "rabbitAnchorLossLeavesNuePackAndItsMemoryAlone";
		BlockPos casterFeet = new BlockPos(2, 1, 2);
		layPad(helper);

		ServerPlayer caster = MegumiShikigamiTestFixtures.setupMegumiCaster(helper, fixture, casterFeet, 0.0f, 0.0f);
		ServerLevel level = helper.getLevel();

		helper.runAtTickTime(FIRST_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.NUE);
			boolean nue = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(nue, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"nue", helper.getTick(), ownerId, "nue tryPrimary result", "true", nue));
		}));

		helper.runAtTickTime(SECOND_SUMMON_TICK, () -> MegumiShikigamiTestFixtures.runGuarded(helper, caster, () -> {
			UUID ownerId = caster.getUUID();
			MegumiShikigamiSelection.set(ownerId, MegumiShikigami.RABBITS);
			boolean rabbits = MegumiShikigamiRuntime.tryPrimary(caster, false);
			helper.assertTrue(rabbits, MegumiShikigamiTestFixtures.diagnostic(fixture,
					"rabbits", helper.getTick(), ownerId, "rabbits tryPrimary result", "true", rabbits));
		}));

		helper.runAtTickTime(RECALL_TICK, () -> {
			try {
				UUID ownerId = caster.getUUID();
				long gameTime = level.getGameTime();

				// Seed one remembered failure on a rabbit body and one on the Nue body: after the
				// anchor kill the rabbit's memory must be swept while the Nue's survives (R9).
				MegumiShikigamiRuntime.PackView rabbitsView = MegumiShikigamiRuntime
						.packViews(level.getServer(), ownerId).stream()
						.filter(view -> view.type().equals(MegumiShikigami.RABBITS.id()))
						.findFirst().orElse(null);
				helper.assertTrue(rabbitsView != null, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "rabbits pack present", "present", "absent"));
				UUID anchorId = UUID.fromString(rabbitsView.anchorId());
				List<MegumiNueEntity> nueBodies = MegumiShikigamiTestFixtures.nueOwnedBy(level, ownerId);
				helper.assertTrue(nueBodies.size() == 1, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "nue body present", "1", nueBodies.size()));
				UUID nueBodyId = nueBodies.get(0).getUUID();
				MegumiFailureMemory.recordFailure(anchorId, "grab", gameTime);
				MegumiFailureMemory.recordFailure(nueBodyId, "grab", gameTime);

				if (!(level.getEntity(anchorId) instanceof MegumiRabbitEntity anchor)) {
					helper.assertTrue(false, MegumiShikigamiTestFixtures.diagnostic(fixture,
							"kill", helper.getTick(), ownerId, "anchor body present", "present", "absent"));
					return;
				}
				// The real damage pipeline: AFTER_DEATH reconciles synchronously inside hurtServer.
				boolean damaged = anchor.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
				helper.assertTrue(damaged, MegumiShikigamiTestFixtures.diagnostic(fixture,
						"kill", helper.getTick(), ownerId, "lethal damage applied", "true", damaged));

				// R8: only RABBITS tears down — the Nue pack and its zero cooldown survive.
				List<String> types = MegumiShikigamiTestFixtures.shikigamiPackTypes(level.getServer(), ownerId);
				helper.assertTrue(types.equals(List.of(MegumiShikigami.NUE.id())),
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"packs after the anchor kill", List.of("nue"), types));
				helper.assertTrue(MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.RABBITS, gameTime) > 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"rabbits death cooldown", "> 0", "armed"));
				helper.assertTrue(MegumiSummonCooldowns.remainingTicks(ownerId, MegumiShikigami.NUE, gameTime) == 0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"nue summon cooldown", "0", "armed"));

				// R9: the swept rabbit body's memory is gone; the Nue body's is untouched.
				helper.assertTrue(MegumiFailureMemory.weight(anchorId, "grab", gameTime) == 1.0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"swept rabbit failure memory", "1.0 (cleared)", "retained"));
				helper.assertTrue(MegumiFailureMemory.weight(nueBodyId, "grab", gameTime) < 1.0,
						MegumiShikigamiTestFixtures.diagnostic(fixture, "kill", helper.getTick(), ownerId,
								"nue failure memory survives", "< 1.0", "cleared"));
			} finally {
				MegumiShikigamiTestFixtures.cleanupCaster(helper, caster);
			}
		});
		helper.runAtTickTime(50, () -> helper.succeed());
	}

	private static List<String> shikigamiPackTypes(MegumiShikigami[] types) {
		List<String> ids = new java.util.ArrayList<>();
		for (MegumiShikigami type : types) {
			ids.add(type.id());
		}
		return ids;
	}

	/**
	 * Stone floor for the whole arena. The width is the point: the dog pair lands on the two spots
	 * {@code owner ± right * 1.5} and the Toad takes the first of them, so a floor that covers those
	 * spots (not just the caster's block) makes the summons land on the pad instead of falling back
	 * to whatever the world below the structure happens to offer.
	 */
	private static void layPad(GameTestHelper helper) {
		for (int dx = 1; dx <= 6; dx++) {
			for (int dz = 1; dz <= 6; dz++) {
				helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
			}
		}
	}
}
