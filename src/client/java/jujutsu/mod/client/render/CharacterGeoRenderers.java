package jujutsu.mod.client.render;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoRenderer;
import jujutsu.mod.client.render.todo.TodoPlayerGeoRenderer;

/** Single place that decides which vessel gets a GeckoLib replaced-player renderer. */
public final class CharacterGeoRenderers {
	private CharacterGeoRenderers() {}

	/**
	 * Builds one renderer instance per vessel that replaces the vanilla player model.
	 * Vessels absent from the returned map keep the vanilla renderer.
	 */
	public static Map<JujutsuCharacter, CharacterGeoRenderer> create(EntityRendererProvider.Context context) {
		EnumMap<JujutsuCharacter, CharacterGeoRenderer> renderers = new EnumMap<>(JujutsuCharacter.class);
		for (JujutsuCharacter character : JujutsuCharacter.values()) {
			// Exhaustive switch with no default on purpose: a new JujutsuCharacter constant must fail
			// compilation here until it either declares a renderer or explicitly opts into vanilla.
			CharacterGeoRenderer renderer = switch (character) {
				case NOBARA -> new NobaraPlayerGeoRenderer<>(context);
				case TODO -> new TodoPlayerGeoRenderer<>(context);
				case NONE -> null;
			};
			if (renderer != null) {
				renderers.put(character, renderer);
			}
		}
		return renderers;
	}
}
