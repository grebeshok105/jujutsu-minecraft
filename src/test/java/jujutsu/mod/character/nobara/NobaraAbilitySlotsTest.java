package jujutsu.mod.character.nobara;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jujutsu.mod.character.CharacterAbility;

/**
 * Nobara's half of the input-slot contract: one gate, one packet, one message per failed cast.
 *
 * <p>She used to reach her kit through a private int-keyed gate and a private packet, which is how her
 * behaviour and Todo's drifted apart. These assertions exist to keep the second path from coming back.
 */
public final class NobaraAbilitySlotsTest {
	private static final Path MAIN = Path.of("src/main/java");
	private static final Path ROUTER = MAIN.resolve("jujutsu/mod/character/nobara/NobaraAbilityRouter.java");

	private NobaraAbilitySlotsTest() {}

	public static void main(String[] args) throws Exception {
		assertTheBespokePathIsGone();
		assertEverySlotReachesExactlyOneRuntime();
		assertStaggerGuardsTheWholeSwitch();
		assertExactlyOneFallbackMessage();
		assertTheSharedGateOwnsSelection();
		assertCooldownsCannotCrossVessels();
		System.out.println("NobaraAbilitySlotsTest passed");
	}

	/**
	 * A slot is an input position, so the same slot is a different ability for every vessel. Sharing a
	 * cooldown across them let Todo's swap refuse Nobara's hairpin for five seconds after a switch, and
	 * the client mirror disagreed with the server about it because only the mirror knew the vessel.
	 */
	private static void assertCooldownsCannotCrossVessels() throws Exception {
		String server = Files.readString(MAIN.resolve("jujutsu/mod/character/CharacterAbilityCooldowns.java"));
		assert server.contains("record Key(UUID playerId, JujutsuCharacter character, CharacterAbility ability)")
				: "The server cooldown key must include the vessel, not just the player and the slot";
		String client = Files.readString(Path.of(
				"src/client/java/jujutsu/mod/client/character/ClientAbilityCooldowns.java"));
		assert client.contains("record Key(JujutsuCharacter character, CharacterAbility ability)")
				: "The client mirror keys on the vessel too; the two must agree or prediction desyncs";
	}

	private static void assertTheBespokePathIsGone() throws Exception {
		for (String removed : new String[] {
				"jujutsu/mod/network/NobaraActionPayload.java",
				"jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"}) {
			assert !Files.exists(MAIN.resolve(removed)) : "The bespoke Nobara ability path must stay deleted: " + removed;
		}
		String networking = Files.readString(MAIN.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert !networking.contains("NobaraAction") && !networking.contains("nobara_action")
				: "No second client-to-server ability channel may exist beside CharacterAbilityPayload";
		// The curse-link picker is a different conversation and must survive: Self Resonance opens it,
		// the player answers it, and only the next cast reads the answer.
		assert networking.contains("SelectCurseLinkPayload.TYPE") && networking.contains("SelfResonanceRuntime.select")
				: "The curse-link selection packet is not part of the ability path and must stay registered";
	}

	private static void assertEverySlotReachesExactlyOneRuntime() throws Exception {
		String router = Files.readString(ROUTER);
		record Slot(CharacterAbility slot, String call) {}
		Slot[] map = {
				new Slot(CharacterAbility.PRIMARY, "ProjectJjkRitualRuntime.startDirectedHairpin(nobara)"),
				new Slot(CharacterAbility.PRIMARY_SNEAK, "SelfResonanceRuntime.tryCast(nobara)"),
				new Slot(CharacterAbility.SECONDARY, "ProjectJjkRitualRuntime.startMassHairpin(nobara)"),
				new Slot(CharacterAbility.SECONDARY_SNEAK, "NailTrapRuntime.tryPlace(nobara)"),
				new Slot(CharacterAbility.ATTACK_CONTEXT, "NobaraHammerCombatRuntime.handleInput(nobara)"),
		};
		assert map.length == CharacterAbility.values().length
				: "Nobara fills every input slot, so a new slot must be given a meaning here on purpose";
		for (Slot entry : map) {
			assert countOf(router, "case " + entry.slot().name() + " ->") == 1
					: "Exactly one arm may answer " + entry.slot() + ", found " + countOf(router, "case " + entry.slot().name() + " ->");
			assert router.contains(entry.call())
					: entry.slot() + " must reach " + entry.call();
		}
		assert !router.contains("default ->")
				: "The slot switch must stay exhaustive so a new slot cannot fall into an existing ability";
		// Both Hairpin slots share one precondition. If only one of them checks it they diverge silently.
		assert countOf(router, "ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)") == 2
				: "Both Hairpin slots must keep the marked-target precondition";
	}

	private static void assertStaggerGuardsTheWholeSwitch() throws Exception {
		String router = Files.readString(ROUTER);
		int stagger = router.indexOf("CombatStagger.GLOBAL.isStaggered");
		int firstArm = router.indexOf("case PRIMARY ->");
		assert stagger >= 0 : "Nobara keeps her own stagger gate; the shared executor has none";
		assert stagger < firstArm : "The stagger check must run before any arm, as it did in the gate this replaced";
		String executor = Files.readString(MAIN.resolve("jujutsu/mod/character/CharacterAbilityExecutor.java"));
		assert !executor.contains("CombatStagger")
				: "Stagger is Nobara's rule, not every vessel's; do not promote it into the shared gate";
	}

	private static void assertExactlyOneFallbackMessage() throws Exception {
		String router = Files.readString(ROUTER);
		assert countOf(router, "displayClientMessage") == 1
				: "A failed cast must produce exactly one line, not one per rejection reason";
		assert router.contains("message.jujutsumod.nobara.action.no_target")
				: "The single fallback line must stay the one players already know";
		assert router.contains("if (!cast && notify)")
				: "The fallback line must be suppressible, so command and packet callers can differ";
		String lang = Files.readString(Path.of("src/main/resources/assets/jujutsumod/lang/en_us.json"));
		assert lang.contains("message.jujutsumod.nobara.action.no_target")
				: "The fallback line needs a translation";
		assert !lang.contains("message.jujutsumod.nobara.action.not_selected")
				: "The bespoke gate's selection line is orphaned; the shared gate has its own";
	}

	private static void assertTheSharedGateOwnsSelection() throws Exception {
		String router = Files.readString(ROUTER);
		assert !router.contains("CharacterSelectionManager")
				: "Selection is checked once, in the shared gate, before any router is reached";
		String executor = Files.readString(MAIN.resolve("jujutsu/mod/character/CharacterAbilityExecutor.java"));
		assert executor.contains("case NOBARA -> NobaraAbilityRouter.tryCast(player, ability, notify)")
				: "Nobara must be dispatched from the shared gate like every other vessel";
		assert !executor.contains("default ->")
				: "A new vessel must fail compilation here rather than silently casting nothing";
		String commands = Files.readString(MAIN.resolve("jujutsu/mod/command/JujutsuCommands.java"));
		assert commands.contains("CharacterAbilityExecutor.tryCast(player, slot, true)")
				: "The debug commands must share the gate so they cannot bypass its checks";
	}

	private static int countOf(String haystack, String needle) {
		Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
		int count = 0;
		while (matcher.find()) {
			count++;
		}
		return count;
	}
}
