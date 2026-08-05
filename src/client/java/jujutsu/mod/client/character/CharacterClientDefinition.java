package jujutsu.mod.client.character;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.render.CharacterSkinAnimation;

/**
 * Everything the client needs to know about one vessel: how it looks, what colour it paints the menu,
 * what its card says, and which client-only listeners it installs.
 *
 * <p>Deliberately a separate interface from the server-side {@code CharacterDefinition} rather than an
 * extension of it. A dedicated server loads that one and every implementation of it, so a renderer or a
 * VFX recipe reachable from there would drag client classes onto a machine that has none. The two halves
 * are joined only by the {@link JujutsuCharacter} constant they both answer for.
 *
 * <p>Only {@link #id()} and {@link #rosterEntry()} are required — every vessel has a name and a card.
	 * Everything else has a safe default: no skin animation means the vanilla player pose, and no hooks
	 * means nothing to install.
 */
public interface CharacterClientDefinition {
	/** The enum constant this definition speaks for. The registry test checks that they agree. */
	JujutsuCharacter id();

	/** What the selection menu shows for this vessel. */
	CharacterRosterEntry rosterEntry();

	/** GeckoLib pose adapter applied to the vanilla player model, or {@code null} to keep vanilla pose. */
	default CharacterSkinAnimation skinAnimation() {
		return null;
	}

	/**
	 * The texture that replaces the vanilla player skin, or {@code null} to keep the player's own.
	 *
	 * <p>This drives first-person hands and every vanilla skin path; the third-person GeckoLib model uses
	 * its own geo texture. It lives here so the path is written once — it used to be spelled out in both
	 * the skin mixin and the roster.
	 */
	default ResourceLocation playerSkin() {
		return null;
	}

	/** Physical scale applied to the selected player's third-person body. */
	default float bodyScale() {
		return id().bodyScale();
	}

	/** Where this vessel sits in the menu, low first. Only relative order matters. */
	default int rosterOrder() {
		return 0;
	}

	/** Accent colour the ClickGui shell eases toward while this vessel is previewed or selected. */
	int accent();

	/** How warm the shell's surface treatment goes, 0 for neutral. */
	default float warmth() {
		return 0.0f;
	}

	/** Registers this vessel's client-only listeners — entity renderers, VFX recipes — once at client init. */
	default void registerClientHooks() {}

	/** Label and blurb for this vessel's row in the Characters tab. */
	String moduleName();

	/** One line under the module name. */
	String moduleDescription();

	/** Whether this vessel's module row starts switched on. Exactly one vessel should say yes. */
	default boolean moduleStartsEnabled() {
		return false;
	}
}
