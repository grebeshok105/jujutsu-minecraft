package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The two Block 5 tag JSONs as a contract (issue #79). Both files are data no other test reads, so
 * a silently emptied or over-broadened list would only show up when a player watches a toad grab a
 * boss or an elephant chew through a chest — the exact failures R18 and the ungrabbable decision
 * name. The block list is checked from both sides: the litter it must reach, and the property it
 * must never touch.
 */
final class MegumiShikigamiTagContractTest {
	private static final Path TAG_DIR = Path.of("src/main/resources/data/jujutsumod/tags");

	@Test
	void ungrabbableHoldsTheBossGradeBodies() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("entity_type/ungrabbable.json"));
		assertTrue(values.contains("jujutsumod:greater_cursed_spirit"),
				"the greater cursed spirit is the precedent the decision names: " + values);
		assertTrue(values.contains("minecraft:wither"), "the wither is not grabbable: " + values);
		assertTrue(values.contains("minecraft:ender_dragon"), "the dragon is not grabbable: " + values);
		assertTrue(values.contains("minecraft:warden"), "the warden is not grabbable: " + values);
	}

	@Test
	void ungrabbableLeavesOrdinarySpiritsGrabbable() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("entity_type/ungrabbable.json"));
		assertFalse(values.contains("jujutsumod:lesser_cursed_spirit"),
				"an ordinary lesser spirit must stay grabbable: " + values);
		assertFalse(values.contains("jujutsumod:cursed_spirit"),
				"an ordinary spirit must stay grabbable: " + values);
	}

	@Test
	void footprintAllowlistHoldsTheLitter() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("block/destructible_by_shikigami.json"));
		assertTrue(values.contains("minecraft:dirt"), "dirt is the canonical litter: " + values);
		assertTrue(values.contains("minecraft:sand"), "sand is natural terrain: " + values);
		assertTrue(values.contains("#minecraft:leaves"), "leaves are natural terrain: " + values);
	}

	@Test
	void footprintAllowlistSparsBaseBlocks() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("block/destructible_by_shikigami.json"));
		assertFalse(values.contains("#minecraft:planks"), "planks are a base block, never litter: " + values);
		assertFalse(values.contains("#minecraft:fences"), "fences are a base block, never litter: " + values);
		assertFalse(values.contains("minecraft:glass"), "glass is a base block, never litter: " + values);
		assertFalse(values.contains("minecraft:torch"), "a torch is placed by a player, never litter: " + values);
		assertFalse(values.contains("#minecraft:crops"), "crops are planted by a player, never litter: " + values);
	}

	@Test
	void footprintAllowlistSparsProperty() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("block/destructible_by_shikigami.json"));
		assertFalse(values.contains("minecraft:chest"), "a chest is property, never litter: " + values);
		assertFalse(values.contains("minecraft:barrel"), "a barrel is property, never litter: " + values);
		assertFalse(values.contains("minecraft:furnace"), "a furnace is property, never litter: " + values);
	}

	private static Set<String> readValues(Path file) throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		Set<String> values = new HashSet<>();
		root.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
		return values;
	}
}
