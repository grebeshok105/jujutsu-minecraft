package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import jujutsu.mod.registry.JujutsuSounds;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;

/**
 * Two-way contract between the {@link CursedSpiritVariant} sound wiring, the {@link JujutsuSounds}
 * cursed rows, {@code sounds.json}, the shipped {@code .ogg} files and both lang files.
 *
 * <p>The resource side alone is covered by {@code CursedSpiritResourceContractTest}; this test owns the
 * Java side: every frozen variant/channel pair resolves to a sound event whose id maps back to the
 * shipped file, the cursed rows contain nothing extra, and no variant aliases another's channel.
 * Registry presence (that {@code JujutsuSounds.register()} actually wires every row) is asserted
 * on a live server in {@code CursedSpiritIntegrationGameTests} — not here, because the unit-test
 * registry is frozen before any registration could happen.
 */
class CursedSpiritSoundSymmetryTest {
	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/jujutsumod");
	private static final Set<String> CHANNELS = Set.of("ambient", "hurt", "death", "scream");
	private static final Set<CursedSpiritVariant> SCREAMLESS = Set.of(CursedSpiritVariant.GUZZLER, CursedSpiritVariant.GULBER);

	@Test
	void everyFrozenChannelMapsBackToItsShippedFileAndSubtitles() throws IOException {
		JsonObject soundsJson = readJson(ASSETS.resolve("sounds.json"));
		JsonObject enUs = readJson(ASSETS.resolve("lang/en_us.json"));
		JsonObject ruRu = readJson(ASSETS.resolve("lang/ru_ru.json"));

		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			for (String channel : CHANNELS) {
				SoundEvent event = channelSound(variant, channel);
				if (channel.equals("scream") && SCREAMLESS.contains(variant)) {
					assertNull(event, variant.id() + " must not wire a " + channel + " channel");
					continue;
				}
				assertNotNull(event, variant.id() + " is missing its " + channel + " sound");

				String key = "cursed." + variant.id() + "_" + channel;
				assertEquals("jujutsumod:" + key, event.location().toString());

				assertTrue(soundsJson.has(key), "sounds.json is missing " + key);
				JsonArray entries = soundsJson.getAsJsonObject(key).getAsJsonArray("sounds");
				String expectedPath = "cursed/cursed_" + variant.id() + "_" + channel;
				assertEquals("jujutsumod:" + expectedPath, entries.get(0).getAsString());
				assertTrue(Files.exists(ASSETS.resolve("sounds/" + expectedPath + ".ogg")),
					"missing shipped audio " + expectedPath + ".ogg");

				String subtitle = "subtitles.jujutsumod." + key;
				assertTrue(enUs.has(subtitle), "en_us.json is missing " + subtitle);
				assertTrue(ruRu.has(subtitle), "ru_ru.json is missing " + subtitle);
			}
		}
	}

	@Test
	void cursedRowsContainExactlyTheFrozenChannelsAndNothingElse() throws IllegalAccessException {
		Set<String> expected = new TreeSet<>();
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			for (String channel : CHANNELS) {
				if (!(channel.equals("scream") && SCREAMLESS.contains(variant))) {
					expected.add("CURSED_" + variant.id().toUpperCase(Locale.ROOT) + "_" + channel.toUpperCase(Locale.ROOT));
				}
			}
		}

		Set<String> actual = new TreeSet<>();
		for (Field field : JujutsuSounds.class.getDeclaredFields()) {
			if (field.getType() == SoundEvent.class && Modifier.isStatic(field.getModifiers())
				&& field.getName().startsWith("CURSED_")) {
				actual.add(field.getName());
			}
		}

		assertEquals(expected, actual);
	}

	@Test
	void noVariantAliasesAnotherVariantsChannel() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Set<String> seen = new LinkedHashSet<>();
			for (String channel : CHANNELS) {
				SoundEvent event = channelSound(variant, channel);
				if (event == null) {
					continue;
				}
				String id = event.location().toString();
				assertEquals("jujutsumod:cursed." + variant.id() + "_" + channel, id);
				assertTrue(seen.add(id), "duplicate sound id " + id + " inside " + variant.id());
			}
			assertEquals(SCREAMLESS.contains(variant) ? 3 : 4, seen.size(),
				variant.id() + " must wire exactly " + (SCREAMLESS.contains(variant) ? 3 : 4) + " channels");
		}
	}

	private static SoundEvent channelSound(CursedSpiritVariant variant, String channel) {
		return switch (channel) {
			case "ambient" -> variant.ambientSound();
			case "hurt" -> variant.hurtSound();
			case "death" -> variant.deathSound();
			case "scream" -> variant.screamSound();
			default -> throw new IllegalArgumentException(channel);
		};
	}

	private static JsonObject readJson(Path path) throws IOException {
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(path));
			return parsed.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new AssertionError("failed to parse " + path, e);
		}
	}
}
