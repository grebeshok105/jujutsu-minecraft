package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tag JSON contract (Step 9, tag half of R17): the curse-remnant tag gains exactly the three
 * spirit ids, the Boogie Woogie immune tag holds only the greater spirit.
 */
final class CursedSpiritTagContractTest {
	private static final Path TAG_DIR = Path.of("src/main/resources/data/jujutsumod/tags/entity_type");

	@Test
	void remnantCurseTagContainsAllThreeTiers() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("resonance_remnant_curse.json"));
		assertTrue(values.contains("jujutsumod:lesser_cursed_spirit"), "lesser in curse tag: " + values);
		assertTrue(values.contains("jujutsumod:cursed_spirit"), "common in curse tag: " + values);
		assertTrue(values.contains("jujutsumod:greater_cursed_spirit"), "greater in curse tag: " + values);
	}

	@Test
	void boogieWoogieImmuneTagContainsOnlyGreater() throws Exception {
		Set<String> values = readValues(TAG_DIR.resolve("boogie_woogie_immune.json"));
		assertTrue(values.contains("jujutsumod:greater_cursed_spirit"), "greater immune: " + values);
		assertTrue(!values.contains("jujutsumod:lesser_cursed_spirit"), "lesser swappable: " + values);
		assertTrue(!values.contains("jujutsumod:cursed_spirit"), "common swappable: " + values);
	}

	private static Set<String> readValues(Path file) throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		Set<String> values = new HashSet<>();
		root.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
		return values;
	}
}
