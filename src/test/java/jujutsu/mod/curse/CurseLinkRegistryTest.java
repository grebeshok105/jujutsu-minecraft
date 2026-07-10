package jujutsu.mod.curse;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class CurseLinkRegistryTest {
	private CurseLinkRegistryTest() {}

	public static void main(String[] args) {
		CurseLinkRegistry registry = new CurseLinkRegistry();
		UUID source = UUID.randomUUID();
		UUID nobara = UUID.randomUUID();
		UUID enemy = UUID.randomUUID();
		CurseLink first = registry.createLink(source, ResourceLocation.parse("jujutsumod:decay"), Set.of(nobara, enemy), 20L);
		assert registry.linksForParticipant(nobara).equals(List.of(first));
		assert CurseLinkSelection.resolve(registry.linksForParticipant(nobara), null).status() == CurseLinkSelection.Status.READY;
		assert CurseLinkSelection.resolve(registry.linksForParticipant(nobara), null).link().equals(first);

		CurseLink second = registry.createLink(UUID.randomUUID(), ResourceLocation.parse("jujutsumod:test_link"), Set.of(nobara), 30L);
		assert CurseLinkSelection.resolve(registry.linksForParticipant(nobara), null).status() == CurseLinkSelection.Status.NEEDS_SELECTION;
		assert CurseLinkSelection.resolve(registry.linksForParticipant(nobara), second.id()).link().equals(second);
		registry.removeLink(second.id());
		assert CurseLinkSelection.resolve(registry.linksForParticipant(nobara), second.id()).status() == CurseLinkSelection.Status.READY : "single remaining link auto-selects instead of accepting stale selection";
		registry.removeLinksOwnedBy(source);
		assert registry.linksForParticipant(nobara).isEmpty();
		System.out.println("CurseLinkRegistryTest passed");
	}
}
