package jujutsu.mod.client.character.nobara;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.CharacterRosterEntry;
import jujutsu.mod.client.character.HudSlot;
import jujutsu.mod.client.character.JujutsuCharacterIcons;
import jujutsu.mod.client.render.ProjectJjkNailRenderer;
import jujutsu.mod.client.render.CharacterSkinAnimation;
import jujutsu.mod.client.render.nobara.doll.ProjectJjkStrawDollRenderer;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoAnimatable;
import jujutsu.mod.client.render.nobara.NobaraSkinAnimationAdapter;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.character.nobara.NobaraEspState;
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
								"screen.jujutsumod.character_select.ability.mega_nail", "B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BOLT,
								"screen.jujutsumod.character_select.ability.nail_trap", "⇧B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.FIST,
								"screen.jujutsumod.character_select.ability.hammer", "LMB")));
	}

	/**
	 * The five HUD cells, one per technique slot.
	 *
	 * <p>The roster card and the HUD show the same abilities in the same order, so the HUD list is
	 * built from the card's strip rather than written out a second time.
	 */
	@Override
	public List<HudSlot> hudSlots() {
		List<CharacterRosterEntry.Ability> strip = rosterEntry().abilities();
		return List.of(
				hudSlot(strip, 0, CharacterAbility.PRIMARY),
				hudSlot(strip, 1, CharacterAbility.PRIMARY_SNEAK),
				hudSlot(strip, 2, CharacterAbility.SECONDARY),
				hudSlot(strip, 3, CharacterAbility.SECONDARY_SNEAK),
				hudSlot(strip, 4, CharacterAbility.ATTACK_CONTEXT));
	}

	/** One HUD cell: the roster card's ability at {@code index}, bound to its technique slot. */
	private static HudSlot hudSlot(List<CharacterRosterEntry.Ability> strip, int index, CharacterAbility ability) {
		CharacterRosterEntry.Ability card = strip.get(index);
		return new HudSlot(card.icon(), card.nameKey(), ability, card.inputLabel());
	}

	private static final CharacterSkinAnimation SKIN_ANIMATION =
			new NobaraSkinAnimationAdapter();

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
		NobaraEspState.register();
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
