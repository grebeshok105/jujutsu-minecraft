package jujutsu.mod.client.character.megumi;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.CharacterRosterEntry;
import jujutsu.mod.client.character.JujutsuCharacterIcons;
import jujutsu.mod.client.character.megumi.vfx.MegumiVfxRecipes;
import jujutsu.mod.client.render.CharacterGeoRenderer;
import jujutsu.mod.client.render.megumi.MegumiDivineDogRenderer;
import jujutsu.mod.client.render.megumi.MegumiPlayerGeoRenderer;
import jujutsu.mod.registry.JujutsuEntities;

/** Megumi's client presentation: his player body, Divine Dog renderer, and VFX recipe pack. */
public final class MegumiClientDefinition implements CharacterClientDefinition {
	private static final ResourceLocation SKIN = JujutsuMod.id("textures/entity/character/megumi.png");
	private static final int ACCENT = 0xFF2F8F83;

	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.MEGUMI;
	}

	@Override
	public CharacterRosterEntry rosterEntry() {
		return new CharacterRosterEntry(
				"screen.jujutsumod.character_select.megumi",
				"screen.jujutsumod.character_select.megumi.role",
				"screen.jujutsumod.character_select.megumi.technique",
				SKIN, true,
				List.of(
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BUST,
								"screen.jujutsumod.character_select.ability.divine_dogs", "R"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.PIN,
								"screen.jujutsumod.character_select.ability.sic", "⇧R")));
	}

	@Override
	public CharacterGeoRenderer createRenderer(EntityRendererProvider.Context context) {
		return new MegumiPlayerGeoRenderer<>(context);
	}

	@Override
	public ResourceLocation playerSkin() {
		return SKIN;
	}

	@Override
	public int rosterOrder() {
		return 2;
	}

	@Override
	public int accent() {
		return ACCENT;
	}

	@Override
	public float warmth() {
		return 0.15f;
	}

	@Override
	public void registerClientHooks() {
		EntityRendererRegistry.register(JujutsuEntities.MEGUMI_DIVINE_DOG, MegumiDivineDogRenderer::new);
		MegumiVfxRecipes.register();
	}

	@Override
	public String moduleName() {
		return "Megumi";
	}

	@Override
	public String moduleDescription() {
		return "Ten Shadows — Shikigami Summoner";
	}
}
