package jujutsu.mod.client.character;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.network.CharacterSelectionSyncPayload;

public final class ClientCharacterSelectionManager {
	private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, UUID> ENTITY_IDS = new ConcurrentHashMap<>();
	private static final Map<Integer, RenderContext> RENDER_CONTEXTS = new ConcurrentHashMap<>();

	private ClientCharacterSelectionManager() {}

	public static void apply(CharacterSelectionSyncPayload payload) {
		JujutsuCharacter character = JujutsuCharacter.byId(payload.characterId());
		if (character == JujutsuCharacter.NONE) {
			SELECTIONS.remove(payload.playerId());
			return;
		}
		SELECTIONS.put(payload.playerId(), new Selection(character, model(payload.modelId())));
	}

	public static Selection selection(UUID playerId) {
		return SELECTIONS.get(playerId);
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
