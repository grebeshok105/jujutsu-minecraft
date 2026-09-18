package jujutsu.mod.client.character.megumi.selector;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jujutsu.mod.registry.JujutsuSounds;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;

/**
 * Audio contract for the shikigami quick selector (issue #109, spec section "Audio feedback").
 *
 * <p>The spec makes four feedback roles mandatory -- open, hover, confirm, reject -- and requires them
 * to be short and restrained enough not to become annoying under repeated combat use. A forgotten
 * registration, a sounds.json row that names a file nobody shipped, or a role silently wired to
 * another role's sample all fail without a crash, and the resulting selector would simply sound wrong
 * or mute. This test is the tripwire for exactly that, plus the subtitle keys both languages need.
 */
final class MegumiSelectorSoundTest {
	private static final Path SOUNDS_SOURCE = Path.of("src/main/java/jujutsu/mod/registry/JujutsuSounds.java");
	private static final Path SOUNDS_JSON = Path.of("src/main/resources/assets/jujutsumod/sounds.json");
	private static final Path SOUND_ROOT = Path.of("src/main/resources/assets/jujutsumod/sounds");
	private static final Path EN_US = Path.of("src/main/resources/assets/jujutsumod/lang/en_us.json");
	private static final Path RU_RU = Path.of("src/main/resources/assets/jujutsumod/lang/ru_ru.json");
	private static final String ROLE_PREFIX = "megumi.selector_";
	private static final Set<String> ROLE_IDS = Set.of(
			ROLE_PREFIX + "open", ROLE_PREFIX + "hover", ROLE_PREFIX + "select", ROLE_PREFIX + "reject");
	/** Shipped samples are 45-120 ms of 44.1 kHz mono Vorbis; the band rejects both silence and long files. */
	private static final long MIN_SAMPLE_BYTES = 1_500L;
	private static final long MAX_SAMPLE_BYTES = 12_000L;
	private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};

	private static final List<SoundEvent> EVENTS = List.of(
			JujutsuSounds.MEGUMI_SELECTOR_OPEN,
			JujutsuSounds.MEGUMI_SELECTOR_HOVER,
			JujutsuSounds.MEGUMI_SELECTOR_SELECT,
			JujutsuSounds.MEGUMI_SELECTOR_REJECT);

	@Test
	void theFourFeedbackRolesExistAsDistinctSoundEvents() {
		assertEquals(4, EVENTS.size(), "the design contract names exactly four feedback roles");
		for (int i = 0; i < EVENTS.size(); i++) {
			assertNotNull(EVENTS.get(i), "selector role " + i + " has no sound event");
			for (int j = i + 1; j < EVENTS.size(); j++) {
				assertNotSame(EVENTS.get(i), EVENTS.get(j),
						"two selector roles share one sound event, so the player cannot tell them apart");
			}
		}
	}

	@Test
	void createdSelectorEventsAreRegisteredUnderTheirOwnIds() throws Exception {
		String source = Files.readString(SOUNDS_SOURCE);
		Set<String> created = ids(source, "create\\(\"(" + ROLE_PREFIX + "[a-z]+)\"\\)");
		assertEquals(ROLE_IDS, created, "JujutsuSounds must create exactly the four selector roles");
		assertEquals(created, ids(source, "register\\(\"(" + ROLE_PREFIX + "[a-z]+)\""),
				"A created selector sound event is never registered, so the game has no audio for it");
	}

	@Test
	void eachRoleShipsItsOwnShortSampleWithSubtitlesInBothLanguages() throws Exception {
		JsonObject sounds = json(SOUNDS_JSON);
		JsonObject english = json(EN_US);
		JsonObject russian = json(RU_RU);
		for (String id : ROLE_IDS) {
			JsonObject entry = sounds.getAsJsonObject(id);
			assertNotNull(entry, "sounds.json has no entry for the selector role " + id);
			JsonArray samples = entry.getAsJsonArray("sounds");
			assertNotNull(samples, "sounds.json entry " + id + " lists no samples");
			String expected = "jujutsumod:megumi/selector_" + id.substring(ROLE_PREFIX.length());
			assertEquals(List.of(expected), names(samples),
					id + " must ship its own sample -- cross-wiring two roles would sound like a mistake");
			for (JsonElement sample : samples) {
				Path ogg = SOUND_ROOT.resolve(sample.getAsString().substring("jujutsumod:".length()) + ".ogg");
				assertTrue(Files.isRegularFile(ogg), "Missing shipped selector sample " + ogg);
				long bytes = Files.size(ogg);
				assertTrue(bytes >= MIN_SAMPLE_BYTES && bytes <= MAX_SAMPLE_BYTES,
						id + " sample is " + bytes + " bytes, outside the short one-shot band ("
								+ MIN_SAMPLE_BYTES + "-" + MAX_SAMPLE_BYTES + ")");
				assertArrayEquals(OGG_MAGIC, magic(ogg),
						id + " must ship a real Ogg Vorbis sample, not a renamed placeholder");
			}
			String subtitle = entry.get("subtitle").getAsString();
			assertTrue(english.has(subtitle), "en_us.json is missing selector subtitle " + subtitle);
			assertTrue(russian.has(subtitle), "ru_ru.json is missing selector subtitle " + subtitle);
			assertTrue(subtitle.startsWith("subtitles.jujutsumod.megumi.selector_"),
					"selector subtitles live under subtitles.jujutsumod.megumi.selector_*, got " + subtitle);
		}
	}

	private static List<String> names(JsonArray samples) {
		List<String> names = new ArrayList<>();
		for (JsonElement sample : samples) {
			names.add(sample.getAsString());
		}
		return names;
	}

	/** Ogg page header: every shipped sample must really be an Ogg container, not a renamed stub. */
	private static byte[] magic(Path ogg) throws Exception {
		try (InputStream in = Files.newInputStream(ogg)) {
			return in.readNBytes(OGG_MAGIC.length);
		}
	}

	private static Set<String> ids(String source, String regex) {
		return Pattern.compile(regex)
				.matcher(source)
				.results()
				.map(match -> match.group(1))
				.collect(Collectors.toCollection(TreeSet::new));
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
