package jujutsu.mod.client.character.nobara;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.HairpinNetwork;
import jujutsu.mod.character.nobara.projectjjk.HairpinSeedResolver;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraProfile;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkResonanceRemnant;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;

/**
 * Client-local Hairpin chain preview (design spec §11, §26).
 *
 * <p>Every {@value #REFRESH_INTERVAL_TICKS} ticks the preview re-runs the SAME pure
 * {@link HairpinSeedResolver} + {@link HairpinNetwork} rules the server applies on cast,
 * over the nails the client can see and that belong to the local player. The result is
 * advisory only — the server re-resolves seed and network on cast; this preview never
 * authorizes anything and sends no packets.
 *
 * <p>Rendering: a small pulse on each chained anchor plus a faint dust link seed→next,
 * capped at {@value #MAX_PREVIEW_NODES} nodes so the world stays readable. Also renders
 * the R57 pre-action cues: a subtle pulse on an aimed entity that an inventory remnant is
 * bound to (strong connection readable before committing the ritual).
 */
public final class NobaraHairpinPreview {
	private static final int REFRESH_INTERVAL_TICKS = 5;
	private static final int MAX_PREVIEW_NODES = 12;
	private static final DustParticleOptions LINK_DUST = new DustParticleOptions(0x2CE8F5, 0.45f);
	private static final DustParticleOptions CONNECTION_DUST = new DustParticleOptions(0xE48A36, 0.6f);

	private static List<HairpinNetwork.Node> chain = List.of();
	private static int tickCounter;

	private NobaraHairpinPreview() {}

	/** Registers the client tick + disconnect cleanup. Call once from {@code registerClientHooks()}. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NobaraHairpinPreview::onClientTick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			chain = List.of();
			tickCounter = 0;
		});
	}

	private static void onClientTick(Minecraft client) {
		if (client.player == null || client.level == null
				|| ClientCharacterSelectionManager.characterOrNone(client.player.getUUID()) != JujutsuCharacter.NOBARA
				|| !holdsNailTool(client)) {
			chain = List.of();
			return;
		}
		if (++tickCounter % REFRESH_INTERVAL_TICKS == 0) {
			recompute(client);
		}
		render(client);
	}

	private static void recompute(Minecraft client) {
		List<HairpinNetwork.Node> candidates = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ProjectJjkNailEntity nail)) {
				continue;
			}
			if (!nail.clientOwnerUuid().map(client.player.getUUID()::equals).orElse(false)) {
				continue;
			}
			// Server candidates come from NailAnchorRegistry, which only tracks embedded
			// nails; prepared/flying/mega nails must not appear as preview seeds.
			if (!nail.isEmbedded()) {
				continue;
			}
			UUID targetId = null;
			if (nail.embeddedTargetEntityId() >= 0) {
				Entity target = client.level.getEntity(nail.embeddedTargetEntityId());
				if (target != null) {
					targetId = target.getUUID();
				}
			}
			candidates.add(new HairpinNetwork.Node(
					nail.getUUID(), nail.position(), targetId, nail.embedDepthLevel(), nail.origin()));
		}
		if (candidates.isEmpty()) {
			chain = List.of();
			return;
		}
		Entity aimed = client.hitResult instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
		BlockHitResult blockHit = client.hitResult instanceof BlockHitResult block
				&& client.hitResult.getType() == HitResult.Type.BLOCK ? block : null;
		UUID seedId = HairpinSeedResolver.resolveSeed(
				client.level, client.player.getEyePosition(), client.player.getLookAngle(), aimed, blockHit, candidates);
		if (seedId == null) {
			chain = List.of();
			return;
		}
		HairpinNetwork.Node seed = null;
		for (HairpinNetwork.Node node : candidates) {
			if (node.nailId().equals(seedId)) {
				seed = node;
				break;
			}
		}
		if (seed == null) {
			chain = List.of();
			return;
		}
		List<HairpinNetwork.Node> built = HairpinNetwork.build(candidates, seed, ProjectJjkNobaraProfile.HAIRPIN_CHAIN_RADIUS);
		chain = built.size() > MAX_PREVIEW_NODES ? List.copyOf(built.subList(0, MAX_PREVIEW_NODES)) : built;
	}

	private static void render(Minecraft client) {
		if (!chain.isEmpty()) {
			for (int i = 0; i < chain.size(); i++) {
				Vec3 at = chain.get(i).position();
				client.level.addParticle(JujutsuParticles.HAIRPIN_WARN_EDGE, at.x, at.y + 0.15, at.z, 0.0, 0.01, 0.0);
				if (i + 1 < chain.size()) {
					Vec3 next = chain.get(i + 1).position();
					int steps = (int) Math.min(8, Math.max(2, at.distanceTo(next) * 2.0));
					for (int step = 1; step < steps; step++) {
						Vec3 link = at.lerp(next, step / (double) steps);
						client.level.addParticle(LINK_DUST, link.x, link.y + 0.1, link.z, 0.0, 0.005, 0.0);
					}
				}
			}
		}
		renderConnectionCue(client);
	}

	/**
	 * R57 world cue: when the aimed entity is bound by a remnant in the local inventory,
	 * pulse it subtly so the strong connection reads before the ritual is committed.
	 * Reads the network-synchronized RESONANCE_TARGET component — zero packets.
	 */
	private static void renderConnectionCue(Minecraft client) {
		if (!(client.hitResult instanceof EntityHitResult entityHit)) {
			return;
		}
		UUID aimedId = entityHit.getEntity().getUUID();
		var inventory = client.player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(JujutsuItems.RESONANCE_REMNANT)) {
				continue;
			}
			ProjectJjkResonanceRemnant remnant = stack.get(JujutsuDataComponents.RESONANCE_TARGET);
			if (remnant == null || !aimedId.equals(remnant.targetId())) {
				continue;
			}
			Entity target = entityHit.getEntity();
			Vec3 at = target.getBoundingBox().getCenter();
			client.level.addParticle(CONNECTION_DUST, at.x, at.y + target.getBbHeight() * 0.35, at.z, 0.0, 0.02, 0.0);
			return;
		}
	}

	/** Preview only while the player holds the nail or the hammer — the tools that detonate.
	 *  Mirrors the server gate: both hands, both legacy and projectjjk aliases. */
	private static boolean holdsNailTool(Minecraft client) {
		return isNailTool(client.player.getMainHandItem()) || isNailTool(client.player.getOffhandItem());
	}

	private static boolean isNailTool(ItemStack stack) {
		return stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL)
				|| stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}
}
