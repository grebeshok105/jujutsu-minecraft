package jujutsu.mod.character.nobara;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
		assertAStaleVesselCannotCast();
		System.out.println("NobaraAbilitySlotsTest passed");
	}

	/**
	 * The character menu applies a switch locally and closes before the server confirms it. Because a slot
	 * is an input position rather than an ability, a press inside that window used to be executed by the
	 * vessel the player had just left — casting Todo's teleport where a hairpin was asked for, and taking
	 * his cooldown for it. The request now names the vessel the client believed in, and the server checks.
	 */
	private static void assertAStaleVesselCannotCast() throws Exception {
		String payload = Files.readString(MAIN.resolve("jujutsu/mod/network/CharacterAbilityPayload.java"));
		assert payload.contains("record CharacterAbilityPayload(int abilityId, String characterId)")
				: "An ability request must name the vessel the client believed it was casting as";
		String networking = Files.readString(MAIN.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert networking.contains("JujutsuCharacter.byId(payload.characterId()) != CharacterSelectionManager.selected(player)")
				: "The server must refuse a request whose named vessel is not the one it has selected";
		int guard = networking.indexOf("byId(payload.characterId())");
		int cast = networking.indexOf("CharacterAbilityExecutor.tryCast(player, ability, true)");
		assert guard >= 0 && cast > guard
				: "The stale-vessel check must run before dispatch, not after something has already fired";
		String keybinds = Files.readString(Path.of("src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("new CharacterAbilityPayload(ability.networkId(), character.id())")
				: "The client must stamp the vessel it is showing the player, not a constant";
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
		// The shape alone proves nothing — a constant vessel would keep the shape and lose the property.
		// What matters is that the value comes from the live selection.
		assert server.contains("new Key(player.getUUID(), CharacterSelectionManager.selected(player), ability)")
				: "The key's vessel must be read from the player's selection, not supplied by the caller";
		String client = Files.readString(Path.of(
				"src/client/java/jujutsu/mod/client/character/ClientAbilityCooldowns.java"));
		assert client.contains("record Key(JujutsuCharacter character, CharacterAbility ability)")
				: "The client mirror keys on the vessel too; the two must agree or prediction desyncs";
		// The mirror is filled from the payload, so the payload's vessel has to come from the same read.
		// Letting a caller name it is how the two sides came to disagree in the first place.
		String networking = Files.readString(MAIN.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert networking.contains("sendAbilityCooldown(ServerPlayer player, CharacterAbility ability, int remainingTicks)")
				: "The cooldown mirror must not take a vessel argument";
		assert networking.contains("JujutsuCharacter character = CharacterSelectionManager.selected(player)")
				: "The mirrored vessel must be resolved the same way the server key resolves it";
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
		//
		// This used to also require that JujutsuNetworking calls SelfResonanceRuntime.select, which
		// pinned *where* the receiver is installed as well as *that* the packet exists. The receiver
		// belongs in Nobara's registerServerHooks, and keeping that half would have made the fix fail a
		// test. VesselBoundaryTest#theOneKnownNetworkLeakDoesNotGrow now tracks the real placement
		// structurally, and fails when it is corrected so the exception is removed with it.
		assert networking.contains("SelectCurseLinkPayload.TYPE")
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
		// She used to fill every slot; USE_CONTEXT is the first one she refuses. So the claim is no longer
		// "the map covers everything" but the stronger "every slot is accounted for" — each constant either
		// reaches a runtime below or is refused in its own arm. A new slot still lands here on purpose.
		Set<CharacterAbility> mapped = new HashSet<>();
		for (Slot entry : map) {
			mapped.add(entry.slot());
		}
		for (CharacterAbility slot : CharacterAbility.values()) {
			if (mapped.contains(slot)) {
				continue;
			}
			assert armOf(router, slot).contains("false")
					: slot + " is neither routed to a runtime nor explicitly refused; decide which it is";
		}
		// Each call is looked for INSIDE its own arm. Searching the whole file instead would pass with two
		// arms transposed — every string would still be present, just bound to the wrong input.
		for (Slot entry : map) {
			String arm = armOf(router, entry.slot());
			assert arm.contains(entry.call())
					: entry.slot() + " must reach " + entry.call() + ", its arm reads: " + arm.strip();
			for (Slot other : map) {
				assert other == entry || !arm.contains(other.call())
						: entry.slot() + "'s arm must not also reach " + other.slot() + "'s runtime";
			}
		}
		assert !Pattern.compile("default\\s*->").matcher(router).find()
				: "The slot switch must stay exhaustive so a new slot cannot fall into an existing ability";
		// Both Hairpin slots share one precondition, and it has to be in both arms rather than twice in one.
		String precondition = "ProjectJjkNobaraRuntime.canCastMarkedHairpin(nobara)";
		for (CharacterAbility hairpin : new CharacterAbility[] {CharacterAbility.PRIMARY, CharacterAbility.SECONDARY}) {
			assert countOf(armOf(router, hairpin), precondition) == 1
					: hairpin + " must check the marked-target precondition exactly once";
		}
	}

	/**
	 * The text of one switch arm, from its {@code ->} to the next {@code case} or the closing brace.
	 *
	 * <p>Label matching requires the arrow immediately after the name, so {@code PRIMARY} cannot match
	 * {@code PRIMARY_SNEAK}. Anything that binds a slot to a call has to be measured within one arm; a
	 * whole-file substring search proves only that the call exists somewhere.
	 */
	private static String armOf(String source, CharacterAbility slot) {
		Matcher label = Pattern.compile("case\\s+" + slot.name() + "\\s*->").matcher(source);
		assert label.find() : "Nobara's router must answer " + slot + " in an arm of its own";
		int from = label.end();
		Matcher end = Pattern.compile("\\n\\s*(case\\s|\\};)").matcher(source);
		return source.substring(from, end.find(from) ? end.start() : source.length());
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

	/**
	 * Scoped to the router on purpose. It cannot claim the player sees exactly one line, because some of
	 * her runtimes speak for themselves before returning false and this fallback then overwrites them —
	 * see E10 in KNOWN_ISSUES. What it does pin is that the router adds no second line of its own.
	 */
	private static void assertExactlyOneFallbackMessage() throws Exception {
		String router = Files.readString(ROUTER);
		assert countOf(router, "displayClientMessage") == 1
				: "The router must add at most one line, not one per rejection reason";
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
		assert executor.contains("JujutsuCharacters.definition(character)")
				&& executor.contains("definition.tryCast(player, slot, notify)")
				: "The shared gate must ask the vessel's definition rather than name any vessel";
		assert !executor.contains("JujutsuCharacter.NOBARA") && !executor.contains("JujutsuCharacter.TODO")
				: "The shared gate must not single out a vessel";
		String definition = Files.readString(MAIN.resolve("jujutsu/mod/character/nobara/NobaraDefinition.java"));
		assert definition.contains("NobaraAbilityRouter.tryCast(player, slot, notify)")
				: "Nobara's definition must send casts to her slot router";
		String registry = Files.readString(MAIN.resolve("jujutsu/mod/character/JujutsuCharacters.java"));
		assert registry.contains("case NOBARA -> NOBARA")
				: "The registry must bind her constant to her definition";
		assert !Pattern.compile("default\\s*->").matcher(registry).find()
				: "A new vessel must fail compilation in the registry rather than inheriting a catch-all";
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
