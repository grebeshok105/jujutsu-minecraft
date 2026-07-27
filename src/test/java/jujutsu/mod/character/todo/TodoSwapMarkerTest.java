package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The thrown mark: two forms, one cleanup path, and an empty-hands gate that stays absolute. */
public final class TodoSwapMarkerTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));
	private static final UUID BODY = UUID.nameUUIDFromBytes(new byte[] {9});

	private TodoSwapMarkerTest() {}

	/**
	 * A best-effort rollback is an accepted design; a silent one is not.
	 *
	 * <p>Three of the four commit paths hand-copied the same restore-and-log block and the fourth — the
	 * single-body marker swap, the only one with no second participant — copied the restore alone and threw
	 * the result away. A failed restore there left a body somewhere neither the plan nor the snapshot
	 * describes, with nothing anywhere to say so. All four now go through one helper, and this asserts they
	 * do rather than asserting each one logs, because the point is that there is one implementation.
	 */
	private static void assertEveryCommitPathReportsAFailedRollback() throws Exception {
		String shared = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert shared.contains("static void rollback(") && shared.contains("rollback incomplete")
				: "the rollback report must be one helper, not a block each path remembers to copy";

		// Exactly one implementation, counted across the package: a second one is a copy that will drift,
		// and drift is how the silent path came about in the first place.
		assert occurrences(shared, "rollback incomplete") == 1
				: "the shared helper must own the only rollback report";
		assert occurrences(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")), "rollback incomplete") == 0
				&& occurrences(Files.readString(TODO.resolve("TodoMarkerSwapRuntime.java")), "rollback incomplete") == 0
				: "no commit path may keep its own copy of the report";

		// Both marker forms, including the single-body one whose failed restore used to be discarded.
		assert occurrences(Files.readString(TODO.resolve("TodoMarkerSwapRuntime.java")), "TodoBoogieWoogieRuntime.rollback(") == 2
				: "both marker swap forms must report, including the single-body one that used to be silent";
		assert occurrences(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")), "TodoBoogieWoogieRuntime.rollback(") == 1
				: "the pair swap must report through the same helper";
	}

	/**
	 * The throw is gated on the vessel; the landing was not.
	 *
	 * <p>Switching vessel inside the 60-tick flight let the projectile land and create a mark after the
	 * leaving-the-vessel teardown had already run — a mark in the world owned by a player who is not Todo,
	 * which is the shape E12 was closed to prevent. The selection is re-read at landing so the gate covers
	 * the whole flight rather than only its first tick.
	 */
	private static void assertALandingCannotOutliveTheVessel() throws Exception {
		String entity = Files.readString(TODO.resolve("TodoSwapMarkerEntity.java"));
		assert entity.contains("CharacterSelectionManager.selected(owner) == JujutsuCharacter.TODO")
				: "the landing must re-read the vessel rather than trust the throw that started the flight";
		assert occurrences(entity, "todoOwner()") >= 3
				: "both onHitBlock and onHitEntity must go through the gated owner, not getOwner() directly";
		assert !entity.contains("getOwner() instanceof LivingEntity owner")
				: "an ungated owner at landing is exactly the hole this closes";
	}

	private static int occurrences(String source, String needle) {
		return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
	}

	public static void main(String[] args) throws Exception {
		assertPositionMarkIsFixedAndOwnsItsProjectile();
		assertEntityMarkFollowsItsBody();
		assertMarkExpiryAndDimensionRules();
		assertEmptyHandsGateIsNotWeakened();
		assertBothFormsShareOneCleanupPath();
		assertMarkSwapIsStrictAndCostsThePrimaryCooldown();
		assertMarkerFallbackNeverOutranksTheCrosshair();
		assertMarkedBodyIsStillSafeToMoveAtSwapTime();
		assertRemarkingTheSameBodyKeepsItsGlow();
		assertTheMarkerIsObtainable();
		assertEveryCommitPathReportsAFailedRollback();
		assertALandingCannotOutliveTheVessel();
		System.out.println("TodoSwapMarkerTest passed");
	}

	private static void assertPositionMarkIsFixedAndOwnsItsProjectile() {
		Vec3 rest = new Vec3(12.0, 65.0, -4.0);
		TodoSwapMark mark = TodoSwapMark.atPosition(OVERWORLD, rest, 77);
		assert mark.form() == TodoSwapMark.Form.POSITION : "A block hit must produce a position mark";
		assert mark.entityId() == 77 : "A position mark must remember the resting projectile it has to discard";
		assert mark.entityUuid() == null && !mark.glowApplied() : "A position mark marks no body, so it glows nothing";
		// A landed mark does not drift, even if something else is passed in.
		assert mark.destination(new Vec3(999.0, 999.0, 999.0)).equals(rest) : "A position mark must stay where it landed";
		assert mark.destination(null).equals(rest) : "A position mark needs no live position";
	}

	private static void assertEntityMarkFollowsItsBody() {
		UUID body = BODY;
		Vec3 struckAt = new Vec3(1.0, 64.0, 1.0);
		Vec3 nowAt = new Vec3(20.0, 70.0, -6.0);
		TodoSwapMark mark = TodoSwapMark.onEntity(OVERWORLD, struckAt, 51, body, true, 400L);
		assert mark.form() == TodoSwapMark.Form.ENTITY : "An entity hit must produce an entity mark";
		assert mark.destination(nowAt).equals(nowAt) : "An entity mark must follow the body, not the impact point";
		assert mark.glowApplied() : "A mark that switched the glow on must remember that it did";
		// Marking a body that was already glowing must not claim the glow, or ending the mark would
		// extinguish whatever else lit it.
		TodoSwapMark onGlowing = TodoSwapMark.onEntity(OVERWORLD, struckAt, 51, body, false, 400L);
		assert !onGlowing.glowApplied() : "A pre-existing glow must not be claimed by the mark";
	}

	private static void assertMarkExpiryAndDimensionRules() {
		// A landed mark is an anchor, not a countdown. Permanent in time -- but see the record's javadoc:
		// that is permanence until it is cleared or its projectile is lost, never persistence across
		// sessions, and never permission to reach it from another dimension.
		TodoSwapMark landed = TodoSwapMark.atPosition(OVERWORLD, Vec3.ZERO, 1);
		assert landed.expiresAtGameTime() == TodoSwapMark.NEVER : "A landed mark must carry the absence of a clock";
		assert !landed.isExpired(0L) && !landed.isExpired(Long.MAX_VALUE - 1L)
				: "A landed mark must never expire on its own";
		assert landed.isIn(OVERWORLD) && !landed.isIn(NETHER) : "A mark must not be usable from another dimension";

		TodoSwapMark body = TodoSwapMark.onEntity(OVERWORLD, Vec3.ZERO, 51, BODY, true, 400L);
		assert !body.isExpired(399L) : "A body mark must survive until its expiry tick";
		assert body.isExpired(400L) : "A body mark must expire on its expiry tick";

		// The lifetimes are not interchangeable, and the record refuses to hold the wrong one rather than
		// trusting every future call site to remember which form gets a clock.
		assertRejected(() -> new TodoSwapMark(TodoSwapMark.Form.POSITION, OVERWORLD, Vec3.ZERO, 1, null, false, 400L),
				"a landed mark with a clock");
		assertRejected(() -> new TodoSwapMark(TodoSwapMark.Form.ENTITY, OVERWORLD, Vec3.ZERO, 1, BODY, true, TodoSwapMark.NEVER),
				"a body mark without one");

		assert TodoProfile.MARKER_BODY_MARK_TTL_TICKS > TodoProfile.MARKER_FLIGHT_TICKS
				: "A body mark must outlive the throw that placed it";
		assert TodoProfile.MARKER_SWAP_RANGE > TodoProfile.BOOGIE_WOOGIE_RANGE
				: "A thrown mark is paid for with an item and a telegraph, so it must reach further";
	}

	private static void assertRejected(Runnable construction, String what) {
		try {
			construction.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		assert false : "The record must refuse " + what;
	}

	private static void assertEmptyHandsGateIsNotWeakened() throws Exception {
		// The whole reason the marker is a real consumed item: the gate is read at swap time, and by then
		// the throwing hand is empty. Whitelisting the marker would turn an absolute rule into a list --
		// and would hand observers a tell, which the feint exists to deny them.
		String gates = Files.readString(TODO.resolve("TodoSwapGates.java"));
		assert !gates.contains("TODO_SWAP_MARKER") && !gates.contains("SwapMarker")
				: "The empty-hands gate must not whitelist the marker";
		assert gates.contains("isEmptyHand(todo.getMainHandItem()) && isEmptyHand(todo.getOffhandItem())")
				: "The gate must still require both hands empty";
		String items = Files.readString(Path.of("src/main/java/jujutsu/mod/registry/JujutsuItems.java"));
		assert items.contains("createTodoSwapMarker(\"todo_swap_marker\", new Item.Properties().stacksTo(1))")
				: "The marker must be single-stack, or a remainder would stay in hand and block the swap";
		String item = Files.readString(TODO.resolve("TodoSwapMarkerItem.java"));
		assert item.contains("stack.consume(1, player)") : "Throwing the marker must consume it";
	}

	private static void assertBothFormsShareOneCleanupPath() throws Exception {
		String marks = Files.readString(TODO.resolve("TodoSwapMarks.java"));
		assert marks.contains("case POSITION -> entity.discard()")
				: "Ending a position mark must discard the resting projectile";
		assert marks.contains("mark.glowApplied()") && marks.contains("setGlowingTag(false)")
				: "Ending an entity mark must clear only a glow the mark applied";
		for (String hook : new String[] {"END_WORLD_TICK", "DISCONNECT", "AFTER_RESPAWN", "AFTER_PLAYER_CHANGE_WORLD", "SERVER_STOPPING"}) {
			assert marks.contains(hook) : "A mark must be released on " + hook;
		}
		// Every exit funnels through release(), so no path can handle one form and forget the other.
		assert marks.split("private static void release\\(", -1).length == 2
				: "There must be exactly one release path for both mark forms";
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		assert definition.contains("TodoSwapMarks.register()")
				: "The mark lifecycle must be registered by the vessel that owns it";
		// Only he can throw it. Anyone else leaving a mark in the world would leave one his cleanup hook
		// never sees, because that hook fires for the vessel being left, not for whoever threw the item.
		String item = Files.readString(TODO.resolve("TodoSwapMarkerItem.java"));
		assert item.contains("CharacterSelectionView.of(player) != JujutsuCharacter.TODO")
				: "The marker must refuse to throw for anyone who is not Todo";
		int gate = item.indexOf("CharacterSelectionView.of(player)");
		int effect = item.indexOf("level.playSound(");
		assert gate >= 0 && effect > gate
				: "The vessel check must run before the sound and the item consumption, not after";
		String view = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterSelectionView.java"));
		assert view.contains("player instanceof ServerPlayer server")
				: "The shared view must read the server's own selection when it has one";
		String clientInit = Files.readString(Path.of("src/client/java/jujutsu/mod/client/JujutsuModClient.java"));
		assert clientInit.contains("CharacterSelectionView.setClientLookup(")
				: "The client must hand its mirror in, or an item check would pass server-side and mispredict";
	}

	private static void assertMarkSwapIsStrictAndCostsThePrimaryCooldown() throws Exception {
		String swap = Files.readString(TODO.resolve("TodoMarkerSwapRuntime.java"));
		assert swap.contains("Strictness.STRICT") && !swap.contains("Strictness.SOFT")
				: "A planned, telegraphed swap has no need of the last-resort fallback";
		assert swap.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.MARKER_SWAP_COOLDOWN_TICKS)")
				: "A mark swap is the primary swap and must cost the primary slot's cooldown";
		// The two forms are priced differently now, and that decision lives in one place rather than being
		// spelled out at the call site -- so a charge limit later lands there and not in this runtime.
		assert swap.contains("TodoSwapMarks.onUsed(level.getServer(), todo.getUUID(), mark)")
				: "What a swap costs its mark must be asked of TodoSwapMarks, not decided here";
		// Scoped to finish(), because clear() is still right where a marked body has genuinely gone. It is
		// wrong only on the success path, where it would tear down a landed anchor that was never spent.
		String finish = swap.substring(swap.indexOf("private static void finish("));
		assert !finish.contains("TodoSwapMarks.clear(")
				: "clear() is the unconditional teardown; on the success path it would spend a landed anchor";
		assert swap.contains("TodoSwapPlan.preflight")
				: "Moving a marked body must use the same atomic two-destination rule";
		// The report itself now lives in the shared helper; both forms reaching it is asserted by
		// assertEveryCommitPathReportsAFailedRollback, which counts the call sites rather than the message.
		assert swap.contains("TodoBoogieWoogieRuntime.rollback(\"marker swap\"")
				: "A failed mark swap must report the incomplete restore through the shared helper";
	}

	private static void assertMarkedBodyIsStillSafeToMoveAtSwapTime() throws Exception {
		// A mark lasts ten seconds. In that window the marked body can be mounted, boarded or leashed, and
		// teleporting it then is exactly what TodoTargetSafety exists to prevent. Liveness alone is not
		// enough -- the other two swap paths re-check eligibility, and so must this one.
		String swap = Files.readString(TODO.resolve("TodoMarkerSwapRuntime.java"));
		assert swap.contains("TodoBoogieWoogieRuntime.isEligibleTarget(todo, marked)")
				: "The marked body must be re-checked for transport safety before it is teleported";
		int resolve = swap.indexOf("private static LivingEntity resolveMarked(");
		assert resolve > 0 && swap.indexOf("isEligibleTarget", resolve) > resolve
				: "The eligibility re-check belongs in the marked-body resolution, not after the teleport";
		// Line of sight is deliberately absent here: a thrown mark may sit somewhere the caster can no
		// longer see. If that ever changes it must be a decision, not a drive-by consistency edit.
		assert !swap.contains("hasLineOfSight")
				: "A thrown mark must not require line of sight; that is the point of throwing it";
	}

	private static void assertRemarkingTheSameBodyKeepsItsGlow() throws Exception {
		// The release-before-read-glow order moved into TodoSwapMarks.markBody when an ability gained the
		// ability to mark a body without a throw. TodoEntityMarkTest owns that ordering assertion now; what
		// this file still has to prove is that the throw goes through it instead of keeping a second copy.
		String entity = Files.readString(TODO.resolve("TodoSwapMarkerEntity.java"));
		assert entity.contains("TodoSwapMarks.markBody(level, owner.getUUID(), struck)")
				: "A body hit must mark through the one shared path";
		assert !entity.contains("hasGlowingTag") && !entity.contains("setGlowingTag")
				: "A second copy of the glow sequence here is how the two ways of marking would drift apart";
	}

	private static void assertTheMarkerIsObtainable() throws Exception {
		// Todo ships without a starter loadout by decision, so without this the whole mechanic would be
		// reachable only through /give -- shipped, tested, and invisible to players.
		String items = Files.readString(Path.of("src/main/java/jujutsu/mod/registry/JujutsuItems.java"));
		assert items.contains("ItemGroupEvents.modifyEntriesEvent") && items.contains("TODO_SWAP_MARKER))")
				: "The marker must be reachable in game, not only through a command";
	}

	private static void assertMarkerFallbackNeverOutranksTheCrosshair() throws Exception {
		String runtime = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		int aimCheck = runtime.indexOf("aimed.mode() != TargetResolver.Mode.ENTITY");
		int fallback = runtime.indexOf("TodoMarkerSwapRuntime.hasMark(todo, level)");
		assert aimCheck > 0 && fallback > aimCheck
				: "The mark may only be consulted after the crosshair has failed to find a target";
		assert runtime.indexOf("TodoSwapGates.evaluate(todo)") < aimCheck
				: "The shared clap gate must still run before any targeting, marked or aimed";
	}
}
