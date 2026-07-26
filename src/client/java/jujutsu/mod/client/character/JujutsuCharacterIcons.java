package jujutsu.mod.client.character;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

/** The small glyphs the roster's input strips draw. Shared so two vessels cannot spell one path twice. */
public final class JujutsuCharacterIcons {
	public static final ResourceLocation BUST = JujutsuMod.id("textures/gui/dashboard/emoji_bust.png");
	public static final ResourceLocation FIST = JujutsuMod.id("textures/gui/dashboard/emoji_fist.png");
	public static final ResourceLocation PIN = JujutsuMod.id("textures/gui/dashboard/emoji_pin.png");
	public static final ResourceLocation BOOM = JujutsuMod.id("textures/gui/dashboard/emoji_boom.png");
	public static final ResourceLocation LINK = JujutsuMod.id("textures/gui/dashboard/emoji_link.png");
	public static final ResourceLocation BOLT = JujutsuMod.id("textures/gui/dashboard/emoji_bolt.png");

	private JujutsuCharacterIcons() {}
}
