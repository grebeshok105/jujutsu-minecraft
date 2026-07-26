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

	private TodoSwapMarkerTest() {}

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
		System.out.println("TodoSwapMarkerTest passed");
	}

	private static void assertPositionMarkIsFixedAndOwnsItsProjectile() {
		Vec3 rest = new Vec3(12.0, 65.0, -4.0);
		TodoSwapMark mark = TodoSwapMark.atPosition(OVERWORLD, rest, 77, 400L);
		assert mark.form() == TodoSwapMark.Form.POSITION : "A block hit must produce a position mark";
		assert mark.entityId() == 77 : "A position mark must remember the resting projectile it has to discard";
		assert mark.entityUuid() == null && !mark.glowApplied() : "A position mark marks no body, so it glows nothing";
		// A landed mark does not drift, even if something else is passed in.
		assert mark.destination(new Vec3(999.0, 999.0, 999.0)).equals(rest) : "A position mark must stay where it landed";
		assert mark.destination(null).equals(rest) : "A position mark needs no live position";
	}

	private static void assertEntityMarkFollowsItsBody() {
		UUID body = UUID.nameUUIDFromBytes(new byte[] {9});
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
		TodoSwapMark mark = TodoSwapMark.atPosition(OVERWORLD, Vec3.ZERO, 1, 400L);
		assert !mark.isExpired(399L) : "A mark must survive until its expiry tick";
		assert mark.isExpired(400L) : "A mark must expire on its expiry tick";
		assert mark.isIn(OVERWORLD) && !mark.isIn(NETHER) : "A mark must not be usable from another dimension";
		assert TodoProfile.MARKER_MARK_TTL_TICKS > TodoProfile.MARKER_FLIGHT_TICKS
				: "A mark must outlive the throw that placed it";
		assert TodoProfile.MARKER_SWAP_RANGE > TodoProfile.BOOGIE_WOOGIE_RANGE
				: "A thrown mark is paid for with an item and a telegraph, so it must reach further";
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
	}

	private static void assertMarkSwapIsStrictAndCostsThePrimaryCooldown() throws Exception {
		String swap = Files.readString(TODO.resolve("TodoMarkerSwapRuntime.java"));
		assert swap.contains("Strictness.STRICT") && !swap.contains("Strictness.SOFT")
				: "A planned, telegraphed swap has no need of the last-resort fallback";
		assert swap.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS)")
				: "A mark swap is the primary swap and must cost the primary cooldown";
		assert swap.contains("TodoSwapMarks.clear(level.getServer(), todo.getUUID())")
				: "A mark is consumed by the swap it enables, not reusable";
		assert swap.contains("TodoSwapPlan.preflight")
				: "Moving a marked body must use the same atomic two-destination rule";
		assert swap.contains("rollback incomplete") : "A failed mark swap must log the incomplete restore";
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
		String entity = Files.readString(TODO.resolve("TodoSwapMarkerEntity.java"));
		int release = entity.indexOf("TodoSwapMarks.clear(level.getServer(), owner.getUUID())");
		int readGlow = entity.indexOf("boolean glowApplied = !struck.hasGlowingTag()");
		assert release > 0 && readGlow > 0 && release < readGlow
				: "The previous mark must be released before the glow is read, or re-marking the same body "
						+ "reads its own glow as foreign and then switches it off";
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
