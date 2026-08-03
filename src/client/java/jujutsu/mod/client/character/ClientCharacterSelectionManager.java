package jujutsu.mod.client.character;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.network.CharacterSelectionSyncPayload;

public final class ClientCharacterSelectionManager {
	private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, UUID> ENTITY_IDS = new ConcurrentHashMap<>();
	private static final Map<Integer, RenderContext> RENDER_CONTEXTS = new ConcurrentHashMap<>();

	private ClientCharacterSelectionManager() {}

	public static void apply(CharacterSelectionSyncPayload payload) {
		JujutsuCharacter character = JujutsuCharacter.byId(payload.characterId());
		// Always remember the selection, including NONE — UI defaults must match the server.
		Selection previous = SELECTIONS.put(payload.playerId(), new Selection(character, model(payload.modelId())));
		cancelFirstPersonOnVesselChange(payload.playerId(), previous, character);
		refreshDimensions(payload.playerId());
	}

	/** Optimistic local update after Confirm (before server echo). */
	public static void applyLocal(UUID playerId, JujutsuCharacter character, PlayerSkin.Model model) {
		Selection previous = SELECTIONS.put(playerId, new Selection(character, model));
		cancelFirstPersonOnVesselChange(playerId, previous, character);
		refreshDimensions(playerId);
	}

	/**
	 * A vessel switch mid-animation would otherwise leave the first-person channel cancelling the
	 * vanilla hand path for the rest of its run, hiding held items.
	 */
	private static void cancelFirstPersonOnVesselChange(UUID playerId, Selection previous, JujutsuCharacter character) {
		if (previous != null && previous.character() == character) {
			return;
		}
		LocalPlayer local = Minecraft.getInstance().player;
		if (local != null && local.getUUID().equals(playerId)) {
			VfxDirector.cancelFirstPerson();
		}
	}

	private static void refreshDimensions(UUID playerId) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.getUUID().equals(playerId)) {
			client.player.refreshDimensions();
		}
		if (client.level == null) {
			return;
		}
		for (Player player : client.level.players()) {
			if (player.getUUID().equals(playerId) && player != client.player) {
				player.refreshDimensions();
				return;
			}
		}
	}

	public static Selection selection(UUID playerId) {
		return SELECTIONS.get(playerId);
	}

	/**
	 * Character currently known for this player. Missing entry means {@link JujutsuCharacter#NONE}
	 * (matches server {@code CharacterSelectionManager.selected} default).
	 */
	public static JujutsuCharacter characterOrNone(UUID playerId) {
		Selection selection = selection(playerId);
		return selection == null ? JujutsuCharacter.NONE : selection.character();
	}

	public static void rememberEntity(AbstractClientPlayer player, float partialTick) {
		ENTITY_IDS.put(player.getId(), player.getUUID());
		RENDER_CONTEXTS.put(player.getId(), new RenderContext(new WeakReference<>(player), partialTick));
	}

	public static Selection selectionByEntityId(int entityId) {
		UUID playerId = ENTITY_IDS.get(entityId);
		return playerId == null ? null : selection(playerId);
	}

	public static RenderContext renderContextByEntityId(int entityId) {
		RenderContext context = RENDER_CONTEXTS.get(entityId);
		if (context == null || context.player() != null) {
			return context;
		}
		RENDER_CONTEXTS.remove(entityId);
		return null;
	}

	public static void clear() {
		SELECTIONS.clear();
		ENTITY_IDS.clear();
		RENDER_CONTEXTS.clear();
	}

	private static PlayerSkin.Model model(String modelId) {
		return "slim".equalsIgnoreCase(modelId) ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
	}

	public record Selection(JujutsuCharacter character, PlayerSkin.Model model) {}

	public record RenderContext(WeakReference<AbstractClientPlayer> playerReference, float partialTick) {
		public AbstractClientPlayer player() {
			return playerReference.get();
		}
	}
}
