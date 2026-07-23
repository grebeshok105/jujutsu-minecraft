package jujutsu.mod.registry;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.CharacterPlayerState;

/** Persistent Fabric attachments owned by jujutsumod. */
public final class JujutsuAttachments {
	public static final AttachmentType<CharacterPlayerState> CHARACTER_STATE = AttachmentRegistry.create(
			JujutsuMod.id("character_state"),
			builder -> builder
					.persistent(CharacterPlayerState.CODEC)
					.copyOnDeath()
	);

	private JujutsuAttachments() {}

	public static void register() {
		JujutsuMod.LOGGER.info("Registered jujutsumod attachments");
	}
}
