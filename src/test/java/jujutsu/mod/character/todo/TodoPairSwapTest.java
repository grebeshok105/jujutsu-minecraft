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
		assertDistanceIsMeasuredFromTodoOnly();
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
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert swap.contains("strictness == Strictness.SOFT && isInWorldDestination")
				: "The fallback must stay gated on SOFT so STRICT genuinely cancels";
		assert pair.contains("TodoSwapPlan.preflight")
				: "The pair swap must use the same atomic two-destination rule as the self swap";
		assert pair.contains("rollback incomplete")
				: "A failed pair placement must log the incomplete restore, as the self swap does";
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

	private static void assertSelectionLifecycleIsFullyUnwired() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		for (String hook : new String[] {"END_WORLD_TICK", "DISCONNECT", "AFTER_RESPAWN", "AFTER_PLAYER_CHANGE_WORLD", "SERVER_STOPPING"}) {
			assert pair.contains(hook) : "A pending selection must be dropped on " + hook;
		}
		// The cleanup moved out of the shared selection manager and into Todo's own definition, where it
		// belongs. What matters is that leaving him still runs it, and that the manager still calls the
		// hook for the vessel being left rather than for the one arriving.
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		assert definition.contains("public void onDeselected(ServerPlayer player)")
				&& definition.contains("TodoPairSwapRuntime.forget(player.getUUID())")
				: "Leaving the vessel must drop a half-finished pair selection";
		String selection = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterSelectionManager.java"));
		assert selection.contains("JujutsuCharacters.definition(previous).onDeselected(player)")
				: "The departing vessel's hook must run, not the arriving one's";
		int deselect = selection.indexOf("onDeselected(player)");
		int store = selection.indexOf("setAttached(JujutsuAttachments.CHARACTER_STATE");
		assert deselect >= 0 && store > deselect
				: "The departing vessel must pack up while it is still the selected one";
		String init = Files.readString(Path.of("src/main/java/jujutsu/mod/JujutsuMod.java"));
		assert init.contains("TodoPairSwapRuntime.register()")
				: "The pair swap lifecycle must be registered from mod init";
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
