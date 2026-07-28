package jujutsu.mod.client.character.megumi;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientAbilityCooldowns;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

/** Megumi-owned contribution to the director's single combat HUD callback. */
public final class MegumiCooldownHud {
	private static final int SHADOW_TEAL = 0xFF2F8F83;

	private MegumiCooldownHud() {}

	static boolean visible(boolean localPlayerExists, boolean megumiSelected, int remainingTicks) {
		return localPlayerExists && megumiSelected && remainingTicks > 0;
	}

	public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		boolean megumiSelected = ClientCharacterSelectionManager.characterOrNone(client.player.getUUID())
				== JujutsuCharacter.MEGUMI;
		int remaining = ClientAbilityCooldowns.remainingTicks(JujutsuCharacter.MEGUMI, CharacterAbility.PRIMARY);
		if (!visible(true, megumiSelected, remaining)) {
			return;
		}

		Font font = client.font;
		Component label = Component.translatable("hud.jujutsumod.megumi.divine_dogs");
		String seconds = ((remaining + 19) / 20) + "s";
		int width = Math.max(96, font.width(label) + font.width(seconds) + 24);
		int x = 12;
		int y = Math.max(12, graphics.guiHeight() / 2 - 13);
		graphics.fill(x, y, x + width, y + 24, 0xB8121818);
		graphics.fill(x, y, x + 3, y + 24, SHADOW_TEAL);
		graphics.drawString(font, label, x + 8, y + 5, 0xFFE5F1EF, false);
		graphics.drawString(font, seconds, x + width - font.width(seconds) - 7, y + 5, 0xFF65D7C8, false);
	}
}
