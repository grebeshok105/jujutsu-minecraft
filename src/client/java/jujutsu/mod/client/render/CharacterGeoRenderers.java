package jujutsu.mod.client.render;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.JujutsuCharacterClients;

/** Single place that decides which vessel gets a GeckoLib replaced-player renderer. */
public final class CharacterGeoRenderers {
	private CharacterGeoRenderers() {}

	/**
	 * Builds one renderer instance per vessel that replaces the vanilla player model.
	 * Vessels absent from the returned map keep the vanilla renderer.
	 *
	 * <p>This file used to hold the switch itself. It now asks each definition, because a vessel deciding
	 * how it is drawn belongs beside the vessel deciding what colour it paints the menu — and the
	 * exhaustiveness that made this switch worth having lives in the client registry now.
	 */
	public static Map<JujutsuCharacter, CharacterGeoRenderer> create(EntityRendererProvider.Context context) {
		EnumMap<JujutsuCharacter, CharacterGeoRenderer> renderers = new EnumMap<>(JujutsuCharacter.class);
		for (CharacterClientDefinition definition : JujutsuCharacterClients.all()) {
			CharacterGeoRenderer renderer = definition.createRenderer(context);
			if (renderer != null) {
				renderers.put(definition.id(), renderer);
			}
		}
		return renderers;
	}
}
