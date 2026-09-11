package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Sound symmetry for the Dire Wolf voice: every dog sound event created in code must be
 * registered, declared in sounds.json with samples that exist on disk and subtitles in
 * both languages, and the data-driven wolf sound variant must route its custom voice to
 * those events while leaving the remaining slots on the vanilla wolf. A forgotten
 * sounds.json entry or a typo in the variant file otherwise fails silently (no crash,
 * just a mute or vanilla-sounding dog).
 */
final class MegumiDireWolfSoundContractTest {
	private static final Path SOUNDS_SOURCE = Path.of("src/main/java/jujutsu/mod/registry/JujutsuSounds.java");
	private static final Path SOUNDS_JSON = Path.of("src/main/resources/assets/jujutsumod/sounds.json");
	private static final Path SOUND_ROOT = Path.of("src/main/resources/assets/jujutsumod/sounds");
	private static final Path EN_US = Path.of("src/main/resources/assets/jujutsumod/lang/en_us.json");
	private static final Path RU_RU = Path.of("src/main/resources/assets/jujutsumod/lang/ru_ru.json");
	private static final Path DIRE_WOLF_VARIANT =
			Path.of("src/main/resources/data/jujutsumod/wolf_sound_variant/dire_wolf.json");

	private static final Set<String> CONTRACT_DOG_EVENTS = Set.of("megumi.dog_ambient", "megumi.dog_growl");

	@Test
	void createdDogEventsAreRegisteredAndShippedWithSamplesAndSubtitles() throws Exception {
		String source = Files.readString(SOUNDS_SOURCE);
		Set<String> created = ids(source, "create\\(\"(megumi\\.dog_[a-z_]+)\"\\)");
		assertEquals(CONTRACT_DOG_EVENTS, created, "JujutsuSounds must create exactly the Dire Wolf voice events");
		assertEquals(created, ids(source, "register\\(\"(megumi\\.dog_[a-z_]+)\""),
				"Every created dog sound event must be registered");

		JsonObject sounds = json(SOUNDS_JSON);
		JsonObject english = json(EN_US);
		JsonObject russian = json(RU_RU);
		for (String id : created) {
			assertTrue(sounds.has(id), "sounds.json has no entry for created event " + id);
			JsonObject entry = sounds.getAsJsonObject(id);
			JsonArray samples = entry.getAsJsonArray("sounds");
			assertTrue(samples != null && !samples.isEmpty(), "sounds.json entry " + id + " lists no samples");
			for (JsonElement sample : samples) {
				Path ogg = samplePath(sample.getAsString());
				assertTrue(Files.isRegularFile(ogg), "Missing shipped ogg sample " + sample.getAsString());
			}
			String subtitle = entry.get("subtitle").getAsString();
			assertTrue(english.has(subtitle), "en_us.json is missing subtitle " + subtitle);
			assertTrue(russian.has(subtitle), "ru_ru.json is missing subtitle " + subtitle);
		}
	}

	@Test
	void direWolfVariantRoutesCustomVoiceOverTheVanillaBody() throws Exception {
		JsonObject variant = json(DIRE_WOLF_VARIANT);
		JsonObject sounds = json(SOUNDS_JSON);
		for (String field : List.of("ambient_sound", "growl_sound")) {
			String namespaced = variant.get(field).getAsString();
			assertTrue(namespaced.startsWith("jujutsumod:"),
					field + " must use the mod voice, not " + namespaced);
			assertTrue(sounds.has(namespaced.substring("jujutsumod:".length())),
					field + " points at " + namespaced + " which has no sounds.json entry");
		}
		for (String field : List.of("hurt_sound", "death_sound", "pant_sound", "whine_sound")) {
			assertTrue(variant.get(field).getAsString().startsWith("minecraft:entity.wolf."),
					"Only ambient and growl are custom; " + field + " must stay vanilla wolf");
		}
	}

	private static Set<String> ids(String source, String regex) {
		return Pattern.compile(regex)
				.matcher(source)
				.results()
				.map(match -> match.group(1))
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private static Path samplePath(String sample) {
		String path = sample.contains(":") ? sample.substring(sample.indexOf(':') + 1) : sample;
		return SOUND_ROOT.resolve(path + ".ogg");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
