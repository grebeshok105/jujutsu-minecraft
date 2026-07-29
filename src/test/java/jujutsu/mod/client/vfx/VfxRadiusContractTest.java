package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.TodoVfxIds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VfxRadiusContractTest {
	@Test
	void everyLiveIdHasOnePresentationOwnerAndFactualDeliveryOwners() {
		List<PresentationOwner> presentations = List.of(
				radius("Nobara hammer", Set.of(NobaraVfxIds.HAMMER), NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				radius("Nobara hammer actions", Set.of(NobaraVfxIds.HAMMER_HORIZONTAL, NobaraVfxIds.HAMMER_OVERHEAD,
						NobaraVfxIds.HAMMER_NAIL_LAUNCH, NobaraVfxIds.SELF_RESONANCE), NobaraVfxRecipes.HAMMER_ACTION_PRESENTATION_RADIUS),
				radius("Nobara impact", Set.of(NobaraVfxIds.IMPACT), NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				direct("Nobara direct", Set.of(NobaraVfxIds.IMPACT_SOUND, NobaraVfxIds.FIRST_PERSON_SNAP)),
				// DETONATE is intentionally caster-only: launchHairpin uses sendVfxCue, not broadcastVfxCue.
				directRadius("Nobara detonate", NobaraVfxIds.DETONATE, NobaraVfxRecipes.HAMMER_ACTION_PRESENTATION_RADIUS),
				radius("Nobara wide", Set.of(NobaraVfxIds.ENLARGE, NobaraVfxIds.EXPLOSION, NobaraVfxIds.REMNANT_DROP),
						NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara ritual", Set.of(NobaraVfxIds.RITUAL_BIND), NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				radius("Nobara doll strike", Set.of(NobaraVfxIds.DOLL_STRIKE), NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				radius("Nobara resonance", Set.of(NobaraVfxIds.RESONANCE_RELEASE), NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara deepen", Set.of(NobaraVfxIds.NAIL_DEEPEN), NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				finiteNone("Nobara trap placement", Set.of(NobaraVfxIds.NAIL_TRAP_PLACED)),
				radius("Nobara trap warning", Set.of(NobaraVfxIds.NAIL_TRAP_ARMED, NobaraVfxIds.NAIL_TRAP_COLLAPSE),
						NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				radius("Nobara trap impact", Set.of(NobaraVfxIds.NAIL_TRAP_IMPACT), NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara black flash", Set.of(NobaraVfxIds.BLACK_FLASH), NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Todo clap", Set.of(TodoVfxIds.BOOGIE_WOOGIE), TodoVfxRecipes.BOOGIE_WOOGIE_PRESENTATION_RADIUS),
				finiteNone("Todo mark", Set.of(TodoVfxIds.PAIR_MARK)),
				direct("Todo feint", Set.of(TodoVfxIds.FEINT_TELL)),
				finiteNone("Todo swap geometry", Set.of(TodoVfxIds.SWAP_ENDPOINT, TodoVfxIds.SWAP_AFTERIMAGE, TodoVfxIds.SWAP_ARRIVAL)),
				finiteNone("Todo momentum", Set.of(TodoVfxIds.MOMENTUM_STRIKE)),
				finiteNone("Megumi dogs", MegumiVfxIds.LIVE));

		Map<ResourceLocation, Set<Double>> deliveries = VfxCompletenessTest.productionDeliveryRadii();
		Set<ResourceLocation> covered = new HashSet<>();
		for (PresentationOwner presentation : presentations) {
			for (ResourceLocation id : presentation.ids()) {
				assertTrue(covered.add(id), "duplicate presentation owner: " + id);
				assertTrue(deliveries.containsKey(id), "missing production delivery owner: " + id);
				Set<Double> deliveryRadii = deliveries.get(id);
				if (presentation.deliveryKind() == DeliveryKind.RADIUS) {
					assertFalse(deliveryRadii.isEmpty(), presentation.name() + " must have a finite delivery owner");
				} else {
					assertTrue(deliveryRadii.isEmpty(), presentation.name() + " is a direct send and must not gain a finite delivery owner");
				}
				if (presentation.presentationKind() == PresentationKind.RADIUS) {
					assertNotNull(presentation.radius());
				}
				if (presentation.presentationKind() == PresentationKind.RADIUS
						&& presentation.deliveryKind() == DeliveryKind.RADIUS) {
					assertFalse(deliveryRadii.isEmpty(), presentation.name() + " must have a finite delivery owner");
					assertTrue(presentation.radius() <= deliveryRadii.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),
							presentation.name() + " presentation exceeds a real delivery owner: " + deliveryRadii);
				}
			}
		}
		Set<ResourceLocation> live = new HashSet<>();
		live.addAll(NobaraVfxIds.LIVE);
		live.addAll(TodoVfxIds.LIVE);
		live.addAll(MegumiVfxIds.LIVE);
		assertEquals(live, covered);
		assertEquals(Set.of(MegumiProfile.VFX_CUE_RADIUS), deliveries.get(MegumiVfxIds.DOGS_SUMMON));
	}

	private static PresentationOwner radius(String name, Set<ResourceLocation> ids, double radius) {
		return new PresentationOwner(name, ids, DeliveryKind.RADIUS, PresentationKind.RADIUS, radius);
	}

	private static PresentationOwner directRadius(String name, ResourceLocation id, double radius) {
		return new PresentationOwner(name, Set.of(id), DeliveryKind.DIRECT, PresentationKind.RADIUS, radius);
	}

	private static PresentationOwner finiteNone(String name, Set<ResourceLocation> ids) {
		return new PresentationOwner(name, ids, DeliveryKind.RADIUS, PresentationKind.NONE, null);
	}

	private static PresentationOwner direct(String name, Set<ResourceLocation> ids) {
		return new PresentationOwner(name, ids, DeliveryKind.DIRECT, PresentationKind.NONE, null);
	}

	private enum DeliveryKind { RADIUS, DIRECT }

	private enum PresentationKind { NONE, RADIUS }

	private record PresentationOwner(String name, Set<ResourceLocation> ids, DeliveryKind deliveryKind,
			PresentationKind presentationKind, Double radius) {}
}
