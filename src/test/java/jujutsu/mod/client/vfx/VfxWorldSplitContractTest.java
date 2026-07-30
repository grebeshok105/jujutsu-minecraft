package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import jujutsu.mod.vfx.VfxCue;

class VfxWorldSplitContractTest {
	private static final Path CLIENT_JAVA = Path.of("src/client/java");
	private static final Path WORLD_CHANNEL = CLIENT_JAVA.resolve(
			"jujutsu/mod/client/vfx/VfxWorldChannel.java");
	private static final List<String> FAMILY_FILES = List.of(
			"jujutsu/mod/client/vfx/world/HairpinWorldEffects.java",
			"jujutsu/mod/client/vfx/world/BlackFlashWorldEffects.java",
			"jujutsu/mod/client/vfx/world/SwapWorldEffects.java",
			"jujutsu/mod/client/vfx/world/ShadowWorldEffects.java",
			"jujutsu/mod/client/vfx/world/VfxWorldGeometry.java");

	@Test
	void channelRetainsLifecycleAndBoundedEviction() {
		VfxWorldChannel channel = new VfxWorldChannel();
		assertEquals(0, channel.activeEffectCount());
		VfxCue cue = cue(0L);

		channel.triggerImpact(cue, VfxWorldChannel.ImpactStyle.HAMMER_SEND, 10);
		assertEquals(1, channel.activeEffectCount());
		for (int index = 1; index < 48; index++) {
			channel.triggerImpact(cue(index), VfxWorldChannel.ImpactStyle.HAMMER_SEND, 10);
		}
		assertEquals(48, channel.activeEffectCount());
		channel.triggerImpact(cue(48L), VfxWorldChannel.ImpactStyle.HAMMER_SEND, 10);
		assertEquals(48, channel.activeEffectCount());
		channel.clear();
		assertEquals(0, channel.activeEffectCount());
	}

	@Test
	void channelOwnsCapListRecordAnchorResolutionBuffersAndExhaustiveDispatch() throws IOException {
		String channel = stripped(Files.readString(WORLD_CHANNEL));
		assertTrue(channel.contains("private static final int MAX_IMPACT_FLASHES = 48;"));
		assertTrue(channel.contains("private final List<ImpactFlash> impactFlashes"));
		assertTrue(channel.contains("private record ImpactFlash"));
		assertTrue(channel.contains("VfxAnchorResolver.resolve(flash.cue()"));
		assertTrue(channel.contains("getBuffer(RenderType.lightning())"));
		assertTrue(channel.contains("getBuffer(RenderType.debugQuads())"));
		assertTrue(channel.contains("private void renderImpactFlashes"));
		assertTrue(channel.contains("public enum ImpactStyle"));
		assertFalse(channel.contains("default ->"), "ImpactStyle dispatch must stay exhaustive");

		String[] dispatches = {
				"case HAMMER_SEND -> HairpinWorldEffects.renderHammerSend",
				"case ENLARGE -> HairpinWorldEffects.renderEnlargeImpact",
				"case EXPLOSION -> HairpinWorldEffects.renderExplosionImpact",
				"case RITUAL_BIND -> HairpinWorldEffects.renderRitualBind",
				"case DOLL_STRIKE -> HairpinWorldEffects.renderDollStrike",
				"case RESONANCE_RELEASE -> HairpinWorldEffects.renderResonanceRelease",
				"case BLACK_FLASH -> BlackFlashWorldEffects.renderBlackFlash",
				"case BOOGIE_WOOGIE -> SwapWorldEffects.renderBoogieWoogie",
				"case SWAP_AFTERIMAGE -> SwapWorldEffects.renderSwapAfterimage",
				"case SWAP_ARRIVAL -> SwapWorldEffects.renderSwapArrival",
				"case MEGUMI_SHADOW_OPEN -> ShadowWorldEffects.renderMegumiShadowPool",
				"case MEGUMI_SHADOW_CLOSE -> ShadowWorldEffects.renderMegumiShadowPool"
		};
		for (String dispatch : dispatches) {
			assertEquals(1, occurrences(channel, dispatch), "Each style must delegate exactly once: " + dispatch);
		}
	}

	@Test
	void worldFixedFlagsRemainTheExistingContract() {
		assertFalse(VfxWorldChannel.ImpactStyle.HAMMER_SEND.isWorldFixed());
		assertFalse(VfxWorldChannel.ImpactStyle.ENLARGE.isWorldFixed());
		assertFalse(VfxWorldChannel.ImpactStyle.EXPLOSION.isWorldFixed());
		assertFalse(VfxWorldChannel.ImpactStyle.RITUAL_BIND.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.DOLL_STRIKE.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.RESONANCE_RELEASE.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.BLACK_FLASH.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.BOOGIE_WOOGIE.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.SWAP_AFTERIMAGE.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.SWAP_ARRIVAL.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN.isWorldFixed());
		assertTrue(VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE.isWorldFixed());
	}

	@Test
	void extractedFamiliesDoNotOwnLifecycleCallbacksOrTransport() throws IOException {
		for (String file : FAMILY_FILES) {
			String source = stripped(Files.readString(CLIENT_JAVA.resolve(file)));
			assertFalse(source.contains("List<"), file);
			assertFalse(source.contains("ArrayList"), file);
			assertFalse(source.contains("WorldRenderEvents"), file);
			assertFalse(source.contains("HudElementRegistry"), file);
			assertFalse(source.contains("ClientPlayConnectionEvents"), file);
			assertFalse(source.contains("ServerPlayNetworking"), file);
			assertFalse(source.contains("ClientPlayNetworking"), file);
			assertFalse(source.contains("Networking"), file);
		}
	}

	@Test
	void sourceContractProvesOldestEvictionWithoutExposingTheRetainedList() throws IOException {
		String channel = stripped(Files.readString(WORLD_CHANNEL));
		assertTrue(channel.contains("if (impactFlashes.size() > MAX_IMPACT_FLASHES)"));
		assertTrue(channel.contains("impactFlashes.remove(0)"), "eviction must remove the oldest retained effect");
	}

	private static VfxCue cue(long startGameTime) {
		return new VfxCue(ResourceLocation.parse("jujutsumod:test/world-split"), Vec3.ZERO,
				VfxCue.NO_ANCHOR, Vec3.ZERO, 1, startGameTime, startGameTime, Vec3.ZERO);
	}

	private static int occurrences(String source, String needle) {
		return source.split(Pattern.quote(needle), -1).length - 1;
	}

	private static String stripped(String source) {
		return source.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("(?m)//.*$", "")
				.replaceAll("\\\"(?:\\\\.|[^\\\"\\\\])*\\\"", "\\\"\\\"");
	}
}
