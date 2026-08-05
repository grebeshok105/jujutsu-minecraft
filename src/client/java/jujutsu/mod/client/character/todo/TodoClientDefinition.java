package jujutsu.mod.client.character.todo;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.CharacterRosterEntry;
import jujutsu.mod.client.character.JujutsuCharacterIcons;
import jujutsu.mod.client.render.CharacterSkinAnimation;
import jujutsu.mod.client.render.todo.TodoPlayerGeoAnimatable;
import jujutsu.mod.client.render.todo.TodoSkinAnimationAdapter;
import jujutsu.mod.client.render.todo.TodoStoneRenderer;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.registry.JujutsuEntities;

/** Todo on the client: violet shell, his own player model, the flying stone, and his HUD chips. */
public final class TodoClientDefinition implements CharacterClientDefinition {
	private static final ResourceLocation SKIN = JujutsuMod.id("textures/entity/character/todo.png");
	private static final int ACCENT = 0xFFA56CFF;

	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.TODO;
	}

	/**
	 * His six casts, in input order. The strip used to list only the swap, which is how a player
	 * learned the feint and the pair swap existed by reading a changelog rather than the menu.
	 */
	@Override
	public CharacterRosterEntry rosterEntry() {
		return new CharacterRosterEntry(
				"screen.jujutsumod.character_select.todo",
				"screen.jujutsumod.character_select.todo.role",
				"screen.jujutsumod.character_select.todo.technique",
				SKIN, true,
				List.of(
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.FIST,
								"screen.jujutsumod.character_select.ability.boogie_woogie", "R"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BOLT,
								"screen.jujutsumod.character_select.ability.fake_clap", "⇧R"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.LINK,
								"screen.jujutsumod.character_select.ability.pair_swap", "B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.LINK,
								"screen.jujutsumod.character_select.ability.triple_swap", "⇧B"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.BOLT,
								"screen.jujutsumod.character_select.ability.stone_throw", "V"),
						new CharacterRosterEntry.Ability(JujutsuCharacterIcons.LINK,
								"screen.jujutsumod.character_select.ability.stone_swap", "⇧V")));
	}

	private static final CharacterSkinAnimation SKIN_ANIMATION =
			new TodoSkinAnimationAdapter();

	@Override
	public CharacterSkinAnimation skinAnimation() {
		return SKIN_ANIMATION;
	}

	@Override
	public ResourceLocation playerSkin() {
		return SKIN;
	}

	@Override
	public int rosterOrder() {
		return 1;
	}

	@Override
	public int accent() {
		return ACCENT;
	}

	@Override
	public float warmth() {
		return 0.45f;
	}

	@Override
	public void registerClientHooks() {
		// The stone is a real entity, so it renders through its own code-geometry renderer.
		EntityRendererRegistry.register(JujutsuEntities.TODO_STONE, TodoStoneRenderer::new);
		TodoVfxRecipes.register();
		VfxDirector.registerHudContribution(JujutsuMod.id("todo_stone_status"), TodoStatusHud::renderStone);
		VfxDirector.registerHudContribution(JujutsuMod.id("todo_pair_status"), TodoStatusHud::renderPair);
	}

	@Override
	public String moduleName() {
		return "Todo";
	}

	@Override
	public String moduleDescription() {
		return "Boogie Woogie — Heavy Melee vessel";
	}
}
