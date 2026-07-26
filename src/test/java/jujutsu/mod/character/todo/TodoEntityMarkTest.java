package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import jujutsu.mod.character.CharacterAbility;

/**
 * Marking a body by hand. The point of these assertions is that this is not a second mark system: it must
 * produce the same mark, through the same call, under the same gate as the thrown marker.
 */
public final class TodoEntityMarkTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");

	private TodoEntityMarkTest() {}

	public static void main(String[] args) throws Exception {
		assertTheSlotIsAppendedNeverRenumbered();
		assertOneGlowOrderForBothWaysOfMarking();
		assertItReusesTheSwapsOwnTargeting();
		assertTheClapGateStillDecides();
		assertMarkingIsCheapButNotFree();
		System.out.println("TodoEntityMarkTest passed");
	}

	private static void assertTheSlotIsAppendedNeverRenumbered() {
		// The ids travel in CharacterAbilityPayload. Renumbering an existing slot would make an in-flight
		// packet mean a different ability on the other side, which is why new slots only ever append.
		assert CharacterAbility.USE_CONTEXT.networkId() == 5
				: "USE_CONTEXT must take the next free id, never one an older slot used";
		assert CharacterAbility.byNetworkId(5) == CharacterAbility.USE_CONTEXT
				: "the id must resolve back to the slot it names";
		assert CharacterAbility.PRIMARY.networkId() == 0 && CharacterAbility.ATTACK_CONTEXT.networkId() == 4
				: "adding a slot must not disturb the ids already on the wire";
	}

	private static void assertOneGlowOrderForBothWaysOfMarking() throws Exception {
		// The order is load-bearing: release the old mark BEFORE reading the glow, or re-marking the same
		// body reads its own glow as foreign and the old mark's release then switches it off. Two copies of
		// that order is how this feature would rot, so both paths must call one method.
		String marks = Files.readString(TODO.resolve("TodoSwapMarks.java"));
		int release = marks.indexOf("clear(level.getServer(), owner);", marks.indexOf("static void markBody("));
		int readGlow = marks.indexOf("boolean glowApplied = !struck.hasGlowingTag()");
		assert release > 0 && readGlow > release
				: "markBody must release the previous mark before it reads the glow";

		String entity = Files.readString(TODO.resolve("TodoSwapMarkerEntity.java"));
		assert entity.contains("TodoSwapMarks.markBody(")
				: "the thrown marker must go through the shared body-marking path";
		assert !entity.contains("setGlowingTag(true)")
				: "the thrown marker must not keep its own copy of the glow sequence";

		String ability = Files.readString(TODO.resolve("TodoEntityMarkRuntime.java"));
		assert ability.contains("TodoSwapMarks.markBody(")
				: "the ability must produce the same mark as the throw, not a second kind";
		assert !ability.contains("setGlowingTag(true)") && !ability.contains("TodoSwapMark.onEntity(")
				: "the ability must not build a mark by hand; that is what markBody is for";
	}

	private static void assertItReusesTheSwapsOwnTargeting() throws Exception {
		// A body you can mark must be exactly a body you could have swapped with. A separate predicate here
		// would let the mark land on something the swap then refuses to move -- a dead end the player has
		// no way to see coming.
		String ability = Files.readString(TODO.resolve("TodoEntityMarkRuntime.java"));
		assert ability.contains("TodoBoogieWoogieRuntime.isEligibleTarget(todo, candidate)")
				: "marking must use the swap's own eligibility rule";
		assert ability.contains("TodoProfile.BOOGIE_WOOGIE_RANGE")
				: "marking must reach exactly as far as the aimed swap";
		assert ability.contains("hasLineOfSight")
				: "this cast reaches out and touches someone now, so it must require sight of them";
	}

	private static void assertTheClapGateStillDecides() throws Exception {
		// If marking were allowed under conditions a clap is not, an observer could learn Todo's state from
		// which of his casts got refused -- the same leak the feint's shared gate exists to close.
		String ability = Files.readString(TODO.resolve("TodoEntityMarkRuntime.java"));
		assert ability.contains("TodoSwapGates.evaluate(todo)")
				: "marking must read the same truth table as the claps";
		int gate = ability.indexOf("TodoSwapGates.evaluate(todo)");
		int mark = ability.indexOf("TodoSwapMarks.markBody(");
		assert gate > 0 && mark > gate
				: "the gate must run before anything is marked, not after";
	}

	private static void assertMarkingIsCheapButNotFree() throws Exception {
		assert TodoProfile.ENTITY_MARK_COOLDOWN_TICKS > 0
				: "without a cooldown a held right click would repaint the mark every tick";
		assert TodoProfile.ENTITY_MARK_COOLDOWN_TICKS < TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS
				: "setting a mark moves nobody and costs no item, so it must not price like the swap itself";

		// One mark per Todo is the existing contract, and marking a body therefore replaces a landed anchor.
		// That is a real cost rather than an oversight, and it holds because markBody funnels into mark().
		String marks = Files.readString(TODO.resolve("TodoSwapMarks.java"));
		assert marks.contains("MARKS.put(owner, mark)") && marks.split("MARKS\\.put\\(", -1).length == 2
				: "there must be exactly one way a mark is stored, or the one-mark rule is not a rule";
	}
}
