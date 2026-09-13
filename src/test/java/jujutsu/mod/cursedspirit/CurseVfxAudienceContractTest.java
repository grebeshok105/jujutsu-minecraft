package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Issue #80 wiring contract: every curse VFX cue goes through the 5-arg broadcast with
 * {@code CursePerception::perceives} as its audience. A reverted call site (4-arg shape)
 * turns this red — the isolated helper test cannot see the wiring.
 */
final class CurseVfxAudienceContractTest {
	private static final Path CURSE_TREE = Path.of("src/main/java/jujutsu/mod/cursedspirit");
	private static final String CALL = "broadcastVfxCue(";
	private static final String AUDIENCE = "CursePerception::perceives";
	/** The twelve wired call sites; a thirteenth filtered site stays green, a deletion goes red. */
	private static final int WIRED_SITES = 12;

	@Test
	void everyCurseVfxCueIsPerceiversOnly() throws Exception {
		List<String> offenders = new ArrayList<>();
		int found = 0;
		List<Path> files;
		try (var paths = Files.walk(CURSE_TREE)) {
			files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
		}
		for (Path file : files) {
			String src = Files.readString(file);
			int from = 0;
			int open;
			while ((open = src.indexOf(CALL, from)) >= 0) {
				int end = closingParen(src, open + CALL.length() - 1);
				found++;
				if (!src.substring(open, end).contains(AUDIENCE)) {
					offenders.add(file.getFileName() + "@line"
							+ src.substring(0, open).chars().filter(ch -> ch == '\n').count());
				}
				from = end;
			}
		}
		assertTrue(found >= WIRED_SITES, "expected the wired curse cues, saw " + found);
		assertTrue(offenders.isEmpty(), "4-arg curse cues without an audience: " + offenders);
	}

	/** Index just past the paren balancing the one at {@code open}. */
	private static int closingParen(String src, int open) {
		int depth = 0;
		for (int i = open; i < src.length(); i++) {
			if (src.charAt(i) == '(') {
				depth++;
			} else if (src.charAt(i) == ')') {
				depth--;
				if (depth == 0) {
					return i + 1;
				}
			}
		}
		throw new IllegalStateException("unbalanced broadcastVfxCue call at index " + open);
	}
}
