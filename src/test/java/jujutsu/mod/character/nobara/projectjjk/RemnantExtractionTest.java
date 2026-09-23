package jujutsu.mod.character.nobara.projectjjk;

import java.nio.file.Files;
import java.nio.file.Path;

/** Contract checks for the Deeply Anchored + overhead extraction boundary. */
public final class RemnantExtractionTest {
	private RemnantExtractionTest() {}

	public static void main(String[] args) {
		String runtime = read("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java");
		String hammer = read("src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java");

		int deepGate = runtime.indexOf("NailAnchorRegistry.isDeeplyAnchored");
		int mintPath = runtime.indexOf("mintRemnant(");
		assert deepGate >= 0 : "extraction must use the derived Deeply Anchored registry predicate";
		assert mintPath > deepGate : "remnant minting must follow the Deeply Anchored gate";
		assert runtime.contains("hasLiveBoundRemnant") : "duplicate extraction must be a no-op";
		assert runtime.contains("NobaraVfxIds.REMNANT_EXTRACT") : "extraction must emit its world cue";
		assert runtime.contains("NobaraVfxIds.CASTER_REMNANT_EXTRACT") : "extraction must emit caster action 6";
		assert runtime.contains("remnant.extracted") : "extraction must explain the new setup to the caster";
		assert !runtime.contains("onOrdinaryNailHit") : "ordinary nail hits must never mint remnants";
		assert hammer.contains("pending.kind() == AttackKind.OVERHEAD")
				&& hammer.contains("ProjectJjkStrawDollRuntime.tryExtractRemnant(player, target)")
				: "only the overhead hammer role may request extraction";
		String extraction = runtime.substring(runtime.indexOf("tryExtractRemnant"),
				runtime.indexOf("tryStartResonance"));
		assert !extraction.contains("shrink(") && !extraction.contains("consumeResources")
				: "extraction setup must not consume nails or remnants";
		System.out.println("RemnantExtractionTest passed");
	}

	private static String read(String relativePath) {
		try {
			return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
		} catch (Exception exception) {
			throw new AssertionError(relativePath, exception);
		}
	}
}
