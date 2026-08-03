package jujutsu.mod.client.character.nobara;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.CharacterRosterEntry;
import jujutsu.mod.client.character.JujutsuCharacterIcons;
import jujutsu.mod.client.render.ProjectJjkNailRenderer;
import jujutsu.mod.client.render.CharacterSkinAnimation;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;
import jujutsu.mod.client.render.nobara.doll.ProjectJjkStrawDollRenderer;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoAnimatable;
import jujutsu.mod.client.render.nobara.NobaraSkinAnimationModel;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.registry.JujutsuEntities;

/** Nobara on the client: orange shell, her own player model, nails and a straw doll to draw. */
public final class NobaraClientDefinition implements CharacterClientDefinition {
	private static final ResourceLocation SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
	private static final int ACCENT = 0xFFE48A36;

	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.NOBARA;
	}

	/**
	 * All five input slots, in input order.
	 *
	 * <p>The strip used to list four and describe them wrongly: it labelled left click "Boom" and never
	 * mentioned the nail trap at all, while showing a nail launch that is not on a slot. What is listed
	 * here is what her router actually answers.
	 */
	@Override
	public CharacterRosterEntry rosterEntry() {
		return new CharacterRosterEntry(
				"screen.jujutsumod.character_select.nobara.full",
				"screen.jujutsumod.character_select.nobara.role",
				"screen.jujutsumod.character_select.nobara.grade",
				SKIN, true,
				List.of(
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.PIN,
								"screen.jujutsumod.character_select.ability.hairpin_enlarge", "R"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.LINK,
								"screen.jujutsumod.character_select.ability.resonance", "⇧R"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BOOM,
								"screen.jujutsumod.character_select.ability.hairpin_explosion", "B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BOLT,
								"screen.jujutsumod.character_select.ability.nail_trap", "⇧B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.FIST,
								"screen.jujutsumod.character_select.ability.hammer", "LMB")));
	}

	private static final CharacterSkinAnimation SKIN_ANIMATION =
			new CharacterSkinAnimationAdapter<>(NobaraPlayerGeoAnimatable.INSTANCE, new NobaraSkinAnimationModel());

	@Override
	public CharacterSkinAnimation skinAnimation() {
		return SKIN_ANIMATION;
	}

	@Override
	public ResourceLocation playerSkin() {
		return SKIN;
	}

	@Override
	public int accent() {
		return ACCENT;
	}

	@Override
	public float warmth() {
		return 1.0f;
	}

	@Override
	public void registerClientHooks() {
		ProjectJjkStrawDollItem.setRendererFactory(ProjectJjkStrawDollRenderer::provider);
		EntityRendererRegistry.register(JujutsuEntities.PROJECTJJK_NAIL, ProjectJjkNailRenderer::new);
		NobaraVfxRecipes.register();
	}

	@Override
	public String moduleName() {
		return "Nobara";
	}

	@Override
	public String moduleDescription() {
		return "Straw Doll Technique — Grade 3 vessel";
	}
}
