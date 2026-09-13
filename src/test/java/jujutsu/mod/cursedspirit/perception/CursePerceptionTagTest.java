package jujutsu.mod.cursedspirit.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Step 2, tag half: {@code curse_perception_subject} holds exactly the three spirit ids and the
 * acid projectile Block 3 adds (a projectile that should not be seen must be a member, or the
 * client renders it for everyone). {@link CursePerception#isSubject} reads the live tag in-game;
 * this pins the file it reads.
 *
 * <p>Red-proof: drop one id from the JSON and the exact-match test goes red.
 */
final class CursePerceptionTagTest {
	private static final Path TAG =
			Path.of("src/main/resources/data/jujutsumod/tags/entity_type/curse_perception_subject.json");

	@Test
	void tagHoldsTheThreeSpiritIdsAndTheAcidProjectile() throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(TAG)).getAsJsonObject();
		assertFalse(root.get("replace").getAsBoolean(), "tag must not replace: " + root);
		Set<String> values = new HashSet<>();
		root.getAsJsonArray("values").forEach(element -> values.add(element.getAsString()));
		assertEquals(Set.of("jujutsumod:lesser_cursed_spirit", "jujutsumod:cursed_spirit",
				"jujutsumod:greater_cursed_spirit", "jujutsumod:cursed_acid_spit"), values);
	}
}
