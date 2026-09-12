package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * File-level contract for the Block 1 cursed-spirit asset import (Sons of Sins pack).
 * The frozen per-variant tables are pasted here as literals: every texture must exist,
 * decode as PNG and match its frozen sheet size; every sound must exist, carry the
 * {@code OggS} magic and exceed 1 KB; every {@code cursed.*} sounds.json key must name
 * a subtitle plus one shipped file; both lang files must carry the three entity keys
 * and all 34 subtitle keys; the nine textures must be pairwise non-identical.
 */
final class CursedSpiritResourceContractTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/jujutsumod");
	private static final Path TEXTURE_DIR = ASSETS.resolve("textures/entity/cursed");
	private static final Path SOUND_DIR = ASSETS.resolve("sounds/cursed");
	private static final Path SOUNDS_JSON = ASSETS.resolve("sounds.json");
	private static final Path EN_LANG = ASSETS.resolve("lang/en_us.json");
	private static final Path RU_LANG = ASSETS.resolve("lang/ru_ru.json");

	/** Frozen variant roster: variant id (lowercase) to expected PNG pixel dimensions. */
	private static final Map<String, int[]> TEXTURE_SIZES = Map.of(
			"prowler", new int[]{64, 64},
			"floating_curse", new int[]{64, 64},
			"gulber", new int[]{64, 64},
			"kelvin", new int[]{64, 64},
			"butcher", new int[]{64, 64},
			"blud", new int[]{64, 64},
			"guzzler", new int[]{64, 64},
			"walking_bed", new int[]{128, 128},
			"wistiver", new int[]{64, 64});

	/** Frozen sound channels: 34 entries of {@code <variantLower>_<channel>}. */
	private static final List<String> SOUND_KEYS = List.of(
			"prowler_ambient", "prowler_hurt", "prowler_death", "prowler_scream",
			"floating_curse_ambient", "floating_curse_hurt", "floating_curse_death", "floating_curse_scream",
			"gulber_ambient", "gulber_hurt", "gulber_death",
			"kelvin_ambient", "kelvin_hurt", "kelvin_death", "kelvin_scream",
			"butcher_ambient", "butcher_hurt", "butcher_death", "butcher_scream",
			"guzzler_ambient", "guzzler_hurt", "guzzler_death",
			"blud_ambient", "blud_hurt", "blud_death", "blud_scream",
			"walking_bed_ambient", "walking_bed_hurt", "walking_bed_death", "walking_bed_scream",
			"wistiver_ambient", "wistiver_hurt", "wistiver_death", "wistiver_scream");

	private static final List<String> ENTITY_KEYS = List.of(
			"entity.jujutsumod.lesser_cursed_spirit",
			"entity.jujutsumod.cursed_spirit",
			"entity.jujutsumod.greater_cursed_spirit");

	@Test
	void texturesExistDecodeAsPngAndMatchFrozenDimensions() throws Exception {
		assertEquals(9, TEXTURE_SIZES.size(), "frozen roster must pin nine variants");
		for (Map.Entry<String, int[]> entry : TEXTURE_SIZES.entrySet()) {
			Path texture = TEXTURE_DIR.resolve("cursed_" + entry.getKey() + ".png");
			assertTrue(Files.isRegularFile(texture), "missing texture: " + texture);
			var image = ImageIO.read(texture.toFile());
			assertNotNull(image, "texture does not decode as PNG: " + texture);
			assertEquals(entry.getValue()[0], image.getWidth(), "width mismatch: " + texture);
			assertEquals(entry.getValue()[1], image.getHeight(), "height mismatch: " + texture);
		}
	}

	@Test
	void soundsExistCarryOggMagicAndExceedOneKilobyte() throws Exception {
		assertEquals(34, SOUND_KEYS.size(), "frozen table must pin 34 sound channels");
		for (String key : SOUND_KEYS) {
			Path sound = SOUND_DIR.resolve("cursed_" + key + ".ogg");
			assertTrue(Files.isRegularFile(sound), "missing sound: " + sound);
			byte[] bytes = Files.readAllBytes(sound);
			assertTrue(bytes.length > 1024, "sound under 1 KB: " + sound);
			assertEquals('O', bytes[0] & 0xFF, "missing OggS magic: " + sound);
			assertEquals('g', bytes[1] & 0xFF, "missing OggS magic: " + sound);
			assertEquals('g', bytes[2] & 0xFF, "missing OggS magic: " + sound);
			assertEquals('S', bytes[3] & 0xFF, "missing OggS magic: " + sound);
		}
	}

	@Test
	void soundsJsonKeysCarrySubtitlesAndResolveToShippedFiles() throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(SOUNDS_JSON)).getAsJsonObject();
		for (String key : SOUND_KEYS) {
			String event = "cursed." + key;
			assertTrue(root.has(event), "sounds.json is missing key: " + event);
			JsonObject entry = root.getAsJsonObject(event);
			assertTrue(entry.has("subtitle"), "sounds.json entry has no subtitle: " + event);
			assertEquals("subtitles.jujutsumod.cursed." + key,
					entry.get("subtitle").getAsString(), "subtitle mismatch: " + event);
			assertTrue(entry.has("sounds") && entry.getAsJsonArray("sounds").size() > 0,
					"sounds.json entry has no sound files: " + event);
			String location = entry.getAsJsonArray("sounds").get(0).getAsString();
			assertEquals("jujutsumod:cursed/cursed_" + key, location, "sound path mismatch: " + event);
			Path file = SOUND_DIR.resolve("cursed_" + key + ".ogg");
			assertTrue(Files.isRegularFile(file), "sounds.json points at a missing file: " + event);
		}
	}

	@Test
	void bothLangFilesContainEntityAndSubtitleKeys() throws Exception {
		for (Path lang : List.of(EN_LANG, RU_LANG)) {
			JsonObject root = JsonParser.parseString(Files.readString(lang)).getAsJsonObject();
			for (String key : ENTITY_KEYS) {
				assertTrue(root.has(key), lang + " is missing entity key: " + key);
				assertTrue(!root.get(key).getAsString().isBlank(), lang + " has a blank entity name: " + key);
			}
			for (String key : SOUND_KEYS) {
				String subtitle = "subtitles.jujutsumod.cursed." + key;
				assertTrue(root.has(subtitle), lang + " is missing subtitle key: " + subtitle);
				assertTrue(!root.get(subtitle).getAsString().isBlank(),
						lang + " has a blank subtitle: " + subtitle);
			}
		}
	}

	@Test
	void textureSheetsArePairwiseNonIdentical() throws Exception {
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		Map<String, String> digests = new LinkedHashMap<>();
		for (String variant : TEXTURE_SIZES.keySet()) {
			byte[] bytes = Files.readAllBytes(TEXTURE_DIR.resolve("cursed_" + variant + ".png"));
			StringBuilder hex = new StringBuilder();
			for (byte b : md5.digest(bytes)) {
				hex.append(String.format("%02x", b));
			}
			digests.put(variant, hex.toString());
		}
		assertEquals(new TreeSet<>(digests.values()).size(), digests.size(),
				"duplicate texture sheets among: " + digests);
	}
}
