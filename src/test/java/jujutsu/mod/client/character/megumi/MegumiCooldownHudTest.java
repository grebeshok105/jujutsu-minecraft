package jujutsu.mod.client.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MegumiCooldownHudTest {
	private static final Path ROOT = Path.of("");

	@Test
	void visibilityRequiresLocalMegumiAndPositivePrimaryCooldown() {
		assertFalse(MegumiCooldownHud.visible(false, true, 240));
		assertFalse(MegumiCooldownHud.visible(true, false, 240));
		assertFalse(MegumiCooldownHud.visible(true, true, 0));
		assertTrue(MegumiCooldownHud.visible(true, true, 240));
		assertTrue(MegumiCooldownHud.visible(true, true, 600));
	}

	@Test
	void megumiRegistersIntoTheSingleDirectorOwnedHudAndLangKeysExistInBothLocales() throws Exception {
		String definition = Files.readString(ROOT.resolve(
				"src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java"));
		String director = Files.readString(ROOT.resolve("src/client/java/jujutsu/mod/client/vfx/VfxDirector.java"));
		String hud = Files.readString(ROOT.resolve("src/client/java/jujutsu/mod/client/vfx/VfxHudChannel.java"));
		assertTrue(definition.contains("VfxDirector.registerHudContribution"));
		assertTrue(director.contains("HudElementRegistry.attachElementAfter"));
		assertFalse(hud.contains("JujutsuCharacter.MEGUMI"));

		String key = "hud.jujutsumod.megumi.divine_dogs";
		assertTrue(Files.readString(ROOT.resolve("src/main/resources/assets/jujutsumod/lang/en_us.json")).contains(key));
		assertTrue(Files.readString(ROOT.resolve("src/main/resources/assets/jujutsumod/lang/ru_ru.json")).contains(key));
	}
}
