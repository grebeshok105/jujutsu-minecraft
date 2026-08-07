package jujutsu.mod.client.character.todo;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.CharacterRosterEntry;
import jujutsu.mod.client.character.HudSlot;
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

	/**
	 * The six HUD cells, one per technique slot.
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
				hudSlot(strip, 4, CharacterAbility.TERTIARY),
				hudSlot(strip, 5, CharacterAbility.TERTIARY_SNEAK));
	}

	/**
	 * The longest cooldown each technique slot can carry, the denominator of the HUD overlay fraction.
	 *
	 * <p>A slot's casts can share a cooldown key at different prices — throwing the stone is cheaper
	 * than swapping through it — so the denominator is the largest price any cast on the slot can ask.
	 */
	@Override
	public int maxCooldownTicks(CharacterAbility ability) {
		return switch (ability) {
			case PRIMARY -> TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS;
			case PRIMARY_SNEAK -> TodoProfile.FAKE_CLAP_COOLDOWN_TICKS;
			case SECONDARY -> TodoProfile.PAIR_SWAP_COOLDOWN_TICKS;
			case SECONDARY_SNEAK -> TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS;
			case TERTIARY -> Math.max(TodoProfile.STONE_THROW_COOLDOWN_TICKS, TodoProfile.STONE_SELF_SWAP_COOLDOWN_TICKS);
			case TERTIARY_SNEAK -> TodoProfile.STONE_TARGET_SWAP_COOLDOWN_TICKS;
			default -> 0;
		};
	}

	/** One HUD cell: the roster card's ability at {@code index}, bound to its technique slot. */
	private static HudSlot hudSlot(List<CharacterRosterEntry.Ability> strip, int index, CharacterAbility ability) {
		CharacterRosterEntry.Ability card = strip.get(index);
		return new HudSlot(card.icon(), card.nameKey(), ability, card.inputLabel());
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
