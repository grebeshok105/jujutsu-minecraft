package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import jujutsu.mod.character.CharacterAbility;

/** Pair-swap selection lifecycle, and the safety rules that only apply when bystanders are moved. */
public final class TodoPairSwapTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

	private TodoPairSwapTest() {}

	public static void main(String[] args) throws Exception {
		assertSelectionExpiresOnItsOwnClock();
		assertSelectionIsBoundToDimensionAndIdentity();
		assertBystandersGetStrictPlacement();
		assertOnlyTodoHimselfMayReachTheFallback();
		assertDistanceIsMeasuredFromTodoOnly();
		assertTheSneakVariantFoldsOntoTheSameSlot();
		assertSelectionLifecycleIsFullyUnwired();
		assertMarkingIsFreeAndOnlyTheSwapCosts();
		System.out.println("TodoPairSwapTest passed");
	}

	private static void assertSelectionExpiresOnItsOwnClock() {
		UUID target = UUID.nameUUIDFromBytes(new byte[] {1});
		long expiry = 500L + TodoProfile.PAIR_SELECTION_TTL_TICKS;
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, target, 42, expiry);
		assert !selection.isExpired(expiry - 1) : "A selection must survive until its expiry tick";
		assert selection.isExpired(expiry) : "A selection must expire on its expiry tick, not after it";
		assert selection.isExpired(expiry + 1000) : "An expired selection must stay expired";
	}

	private static void assertSelectionIsBoundToDimensionAndIdentity() {
		UUID target = UUID.nameUUIDFromBytes(new byte[] {2});
		UUID other = UUID.nameUUIDFromBytes(new byte[] {3});
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, target, 42, 100L);
		assert selection.isIn(OVERWORLD) : "A selection must recognize its own dimension";
		assert !selection.isIn(NETHER) : "A selection must not survive into another dimension";
		assert selection.identifies(target) : "A selection must recognize its marked entity";
		// The network id can be recycled onto a different entity; the UUID is what makes that detectable.
		assert !selection.identifies(other) : "A recycled entity id must not pass as the marked entity";
	}

	private static void assertBystandersGetStrictPlacement() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("Strictness.STRICT")
				: "Moving bystanders must not use the last-resort fallback that exists for Todo's own feel";
		assert !pair.contains("Strictness.SOFT")
				: "The pair swap must never fall back to the exact requested point";
		// The scan lives in shared SafeBodyPlacement since the Megumi shadow kit; the property is the
		// same, split across the seam: only the SOFT policy carries the exact-point fallback, and the
		// shared gate hands the requested point back only when the policy opted in.
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert swap.contains("strictness == Strictness.SOFT ? SOFT_PLACEMENT : STRICT_PLACEMENT")
				: "Only SOFT may select the policy that keeps the exact-point fallback";
		String placement = Files.readString(
				Path.of("src/main/java/jujutsu/mod/combat/SafeBodyPlacement.java"));
		assert placement.contains("policy.exactRequestedFallback() && isInWorld")
				: "The fallback must stay gated on the policy flag so a strict scan genuinely cancels";
		assert pair.contains("TodoSwapPlan.preflight")
				: "The pair swap must use the same atomic two-destination rule as the self swap";
		assert pair.contains("TodoBoogieWoogieRuntime.rollback(\"pair swap\"")
				: "A failed pair placement must report the incomplete restore through the shared helper";
	}

	/**
	 * No body but Todo's own may reach the unchecked fallback.
	 *
	 * <p>The pair swap and both mark swaps always passed {@code STRICT} explicitly. The aimed swap did not
	 * pass anything: a defaulting overload supplied {@code SOFT} for <em>both</em> destinations, so the
	 * target — the one participant who did not ask to be moved — could be placed at the exact requested
	 * point with {@code noBlockCollision} skipped. The overload is deleted rather than merely bypassed, so
	 * the unsafe choice cannot be made by omission again.
	 *
	 * <p>The properties this protects — a wall refuses the placement, a large body is judged by its own
	 * bounding box, the world border is enforced, and open air is still a legal destination — live inside
	 * {@code isPlaceableDestination} and need a real {@code ServerLevel} to exercise. Nothing here can do
	 * that; they are in-game checks. What is checkable is that a third party reaches that predicate at all,
	 * which is exactly what the defect removed.
	 */
	private static void assertOnlyTodoHimselfMayReachTheFallback() throws Exception {
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert !swap.contains("findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested) {")
				: "the defaulting overload must stay deleted; it is how SOFT was applied without anyone choosing it";
		assert swap.contains("findSafeDestination(level, todo, targetSnapshot.position(), Strictness.SOFT)")
				: "Todo's own arrival keeps the fallback: the risk is his and it is what makes a mid-air swap feel right";
		assert swap.contains("findSafeDestination(level, target, todoSnapshot.position(), Strictness.STRICT)")
				: "the aimed target must be placed only where noBlockCollision passed, or the cast must cancel";
		// One SOFT site in the whole package, and it is the line above.
		int soft = swap.split(java.util.regex.Pattern.quote("Strictness.SOFT"), -1).length - 1;
		assert soft == 2
				: "SOFT must appear exactly twice in this file: the one call site, and the gate inside the search";
		for (String path : new String[] {"TodoPairSwapRuntime.java", "TodoMarkerSwapRuntime.java"}) {
			assert !Files.readString(TODO.resolve(path)).contains("Strictness.SOFT")
					: path + " moves bodies that are not Todo and must never reach the fallback";
		}
	}

	private static void assertDistanceIsMeasuredFromTodoOnly() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("todo.distanceToSqr(participant)")
				: "Reach must be measured from Todo to each participant";
		// The pair may legitimately be 40 blocks apart; that spread is the whole value of the technique.
		assert !pair.contains("first.distanceToSqr(aimed)") && !pair.contains("aimed.distanceToSqr(first)")
				: "The distance between the two participants must never be limited";
		assert pair.contains("TodoProfile.BOOGIE_WOOGIE_RANGE")
				: "Reach must reuse the swap range rather than introduce a second number";
	}

	/**
	 * Shift+B must reach the pair swap. It is two presses on one key and cares about neither stance nor
	 * hands, so crouching to line up the second participant used to lose the press silently while the
	 * mark ticked on toward an expiry nobody explained.
	 */
	private static void assertTheSneakVariantFoldsOntoTheSameSlot() throws Exception {
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		assert definition.contains("slot == CharacterAbility.SECONDARY_SNEAK ? CharacterAbility.SECONDARY : slot")
				: "Shift+B must fold onto B for Todo";
		String executor = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterAbilityExecutor.java"));
		int fold = executor.indexOf("definition.canonicalSlot(ability)");
		int cooldown = executor.indexOf("CharacterAbilityCooldowns.isReady");
		assert fold >= 0 && cooldown > fold
				: "Folding must precede the cooldown check, or the sneak variant would bypass the real cooldown";
		assert executor.contains("CharacterAbilityCooldowns.isReady(player, slot)")
				&& executor.contains("definition.tryCast(player, slot, notify)")
				: "Both the cooldown check and the cast must use the folded slot, not the raw input";
	}

	private static void assertSelectionLifecycleIsFullyUnwired() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		for (String hook : new String[] {"END_WORLD_TICK", "DISCONNECT", "AFTER_RESPAWN", "AFTER_PLAYER_CHANGE_WORLD", "SERVER_STOPPING"}) {
			assert pair.contains(hook) : "A pending selection must be dropped on " + hook;
		}
		// The cleanup moved out of the shared selection manager and into Todo's own definition, where it
		// belongs. What matters is that leaving him still runs it, and that the manager still calls the
		// hook for the vessel being left rather than for the one arriving.
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		// The three calls that used to be listed here moved into TodoStateLifecycle, because death needs
		// exactly the same teardown and two copies of it are two chances to forget one line. So the check
		// follows the property through the delegation rather than pinning the old spelling.
		assert definition.contains("public void onDeselected(ServerPlayer player)")
				&& definition.contains("TodoStateLifecycle.dropEverything(player)")
				: "Leaving the vessel must run the shared teardown";
		String lifecycle = Files.readString(TODO.resolve("TodoStateLifecycle.java"));
		assert lifecycle.contains("TodoPairSwapRuntime.forget(player.getUUID())")
				: "and that teardown must drop a half-finished pair selection";
		// Death, not respawn. Every teardown Todo had was keyed on AFTER_RESPAWN, so between the killing
		// blow and clicking the button — a stretch the player controls and can hold open indefinitely — a
		// live mark, a glowing body and a resting projectile all survived. Nobara's package has had a death
		// listener since the nail work; Todo's simply had none.
		assert lifecycle.contains("ServerLivingEntityEvents.AFTER_DEATH.register")
				: "Todo state must end at death, not at whatever moment the player chooses to respawn";
		assert lifecycle.contains("TodoSwapMarks.clear(player.getServer(), player.getUUID())")
				&& lifecycle.contains("discardMarkersInFlight(player.getServer(), player.getUUID())")
				&& lifecycle.contains("removeEffect(JujutsuEffects.TODO_SWAP_MOMENTUM)")
				: "the teardown must cover the mark, the projectiles still in the air, and the momentum window";
		String selection = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterSelectionManager.java"));
		assert selection.contains("JujutsuCharacters.definition(previous).onDeselected(player)")
				: "The departing vessel's hook must run, not the arriving one's";
		int deselect = selection.indexOf("onDeselected(player)");
		int store = selection.indexOf("setAttached(JujutsuAttachments.CHARACTER_STATE");
		assert deselect >= 0 && store > deselect
				: "The departing vessel must pack up while it is still the selected one";
		// Registration moved out of mod init and into the vessel that owns it, which is the point of the
		// definition seam: mod init no longer names a single vessel's runtime.
		assert definition.contains("TodoPairSwapRuntime.register()")
				: "The pair swap lifecycle must be registered by Todo's definition";
		String init = Files.readString(Path.of("src/main/java/jujutsu/mod/JujutsuMod.java"));
		assert init.contains("definition.registerServerHooks()")
				: "Mod init must register every vessel's hooks through the registry";
		assert !init.contains("TodoPairSwapRuntime") && !init.contains("NailTrapRuntime")
				: "Mod init must not hand-list any vessel's runtimes";
	}

	private static void assertMarkingIsFreeAndOnlyTheSwapCosts() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS)")
				: "A committed pair swap must take its own cooldown slot";
		// Exactly one cooldown call: marking, cancelling and every rejection must be free.
		assert pair.split("CharacterAbilityCooldowns\\.start", -1).length == 2
				: "Only the committed swap may start a cooldown";
		assert CharacterAbility.SECONDARY.networkId() == 2 : "The pair swap must sit on the second technique key";
		assert CharacterAbility.byNetworkId(2) == CharacterAbility.SECONDARY : "Network id 2 must resolve to SECONDARY";
		assert TodoProfile.PAIR_SWAP_COOLDOWN_TICKS > TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS
				: "Swapping bystanders carries no personal risk, so it must cost more than Todo's own swap";
		String router = Files.readString(TODO.resolve("TodoAbilityRouter.java"));
		assert router.contains("case SECONDARY ->") : "The pair swap must be routed from Todo's slot map";
	}
}
