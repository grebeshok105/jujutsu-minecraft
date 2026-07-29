package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jujutsu.mod.character.megumi.MegumiProfile;
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;
import jujutsu.mod.character.todo.TodoProfile;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class VfxRadiusContractTest {
	@Test
	void everyLiveIdHasOneDeliveryAndPresentationOwner() {
		List<RadiusContract> contracts = List.of(
				radius("Nobara hammer", Set.of(NobaraVfxIds.HAMMER), NobaraHammerCombatRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				radius("Nobara hammer actions", Set.of(NobaraVfxIds.HAMMER_HORIZONTAL, NobaraVfxIds.HAMMER_OVERHEAD,
						NobaraVfxIds.HAMMER_NAIL_LAUNCH, NobaraVfxIds.SELF_RESONANCE), NobaraHammerCombatRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.HAMMER_ACTION_PRESENTATION_RADIUS),
				radius("Nobara impact", Set.of(NobaraVfxIds.IMPACT), ProjectJjkNobaraRuntime.IMPULSE_BROADCAST_RADIUS, NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				direct("Nobara direct", Set.of(NobaraVfxIds.IMPACT_SOUND, NobaraVfxIds.FIRST_PERSON_SNAP), 0.0),
				direct("Nobara detonate", Set.of(NobaraVfxIds.DETONATE), NobaraVfxRecipes.HAMMER_ACTION_PRESENTATION_RADIUS),
				radius("Nobara wide", Set.of(NobaraVfxIds.ENLARGE, NobaraVfxIds.EXPLOSION, NobaraVfxIds.REMNANT_DROP),
						ProjectJjkRitualRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara ritual", Set.of(NobaraVfxIds.RITUAL_BIND), ProjectJjkRitualRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				radius("Nobara doll strike", Set.of(NobaraVfxIds.DOLL_STRIKE), ProjectJjkStrawDollRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				radius("Nobara resonance", Set.of(NobaraVfxIds.RESONANCE_RELEASE), SelfResonanceRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara deepen", Set.of(NobaraVfxIds.NAIL_DEEPEN), NailTrapRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.HAMMER_PRESENTATION_RADIUS),
				direct("Nobara trap placement", Set.of(NobaraVfxIds.NAIL_TRAP_PLACED), 0.0),
				radius("Nobara trap warning", Set.of(NobaraVfxIds.NAIL_TRAP_ARMED, NobaraVfxIds.NAIL_TRAP_COLLAPSE),
						NailTrapRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.IMPACT_PRESENTATION_RADIUS),
				radius("Nobara trap impact", Set.of(NobaraVfxIds.NAIL_TRAP_IMPACT), NailTrapRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Nobara black flash", Set.of(NobaraVfxIds.BLACK_FLASH), NobaraHammerCombatRuntime.VFX_DELIVERY_RADIUS, NobaraVfxRecipes.WIDE_PRESENTATION_RADIUS),
				radius("Todo clap", Set.of(TodoVfxIds.BOOGIE_WOOGIE), TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS, TodoVfxRecipes.BOOGIE_WOOGIE_PRESENTATION_RADIUS),
				radius("Todo mark", Set.of(TodoVfxIds.PAIR_MARK), TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS, 0.0),
				direct("Todo feint", Set.of(TodoVfxIds.FEINT_TELL), 0.0),
				radius("Todo swap geometry", Set.of(TodoVfxIds.SWAP_ENDPOINT, TodoVfxIds.SWAP_AFTERIMAGE, TodoVfxIds.SWAP_ARRIVAL),
						TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS, 0.0),
				radius("Todo momentum", Set.of(TodoVfxIds.MOMENTUM_STRIKE), TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS, 0.0),
				radius("Megumi dogs", MegumiVfxIds.LIVE, MegumiProfile.VFX_CUE_RADIUS, 0.0));

		Set<ResourceLocation> covered = new HashSet<>();
		for (RadiusContract contract : contracts) {
			for (ResourceLocation id : contract.ids()) {
				assertTrue(covered.add(id), "duplicate radius owner: " + id);
			}
			if (contract.deliveryKind() == DeliveryKind.RADIUS) {
				assertTrue(contract.presentationRadius() <= contract.deliveryRadius(), contract.name());
			}
		}
		Set<ResourceLocation> live = new HashSet<>();
		live.addAll(NobaraVfxIds.LIVE);
		live.addAll(TodoVfxIds.LIVE);
		live.addAll(MegumiVfxIds.LIVE);
		assertEquals(live, covered);
		assertTrue(TodoProfile.BOOGIE_WOOGIE_CUE_RADIUS > TodoVfxRecipes.BOOGIE_WOOGIE_PRESENTATION_RADIUS);
		assertTrue(MegumiProfile.VFX_CUE_RADIUS > 0.0);
	}

	private static RadiusContract radius(String name, Set<ResourceLocation> ids, double delivery, double presentation) {
		return new RadiusContract(name, ids, DeliveryKind.RADIUS, delivery, presentation);
	}

	private static RadiusContract direct(String name, Set<ResourceLocation> ids, double presentation) {
		return new RadiusContract(name, ids, DeliveryKind.DIRECT, Double.POSITIVE_INFINITY, presentation);
	}

	private enum DeliveryKind { RADIUS, DIRECT }

	private record RadiusContract(String name, Set<ResourceLocation> ids, DeliveryKind deliveryKind,
			double deliveryRadius, double presentationRadius) {}
}
