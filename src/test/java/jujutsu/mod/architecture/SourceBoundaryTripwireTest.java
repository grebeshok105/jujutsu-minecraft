package jujutsu.mod.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jujutsu.mod.character.JujutsuCharacter;
import org.junit.jupiter.api.Test;

/**
 * Source-text tripwires. <b>This is a grep, and it is named like one on purpose.</b>
 *
 * <p>{@code VesselBoundaryTest} reads bytecode and is the better tool wherever bytecode carries the
 * evidence. Two kinds of seam breach never reach bytecode at all, and an adversarial review confirmed
 * both against a green build:
 *
 * <ul>
 *   <li><b>Compile-time constants.</b> {@code static final double X = TodoProfile.BOOGIE_WOOGIE_RANGE}
 *       is folded into the caller by javac. Verified with {@code javap}: the reading class contains no
 *       mention of {@code TodoProfile}. {@code TodoProfile} is nothing <em>but</em> constants, which
 *       makes it the most leakable class in the mod and invisible to every structural rule.</li>
 *   <li><b>Dynamic loading.</b> {@code Class.forName("jujutsu.mod.character." + id + ".…")} leaves no
 *       edge either.</li>
 * </ul>
 *
 * <p>The known weaknesses of a grep are stated rather than hidden. Comments and literals are stripped
 * before matching, so a comment saying "do not use TodoProfile" does not fail the build. A name
 * assembled from fragments at runtime still passes, and so does a constant copied by hand with no
 * reference to its origin. Those are recorded as limits of the gate in docs/KNOWN_ISSUES.md, not
 * papered over here.
 */
class SourceBoundaryTripwireTest {
	private static final List<String> VESSEL_IDS = Arrays.stream(JujutsuCharacter.values())
			.map(JujutsuCharacter::id)
			.filter(id -> !id.equals(JujutsuCharacter.NONE.id()))
			.toList();

	private static final List<Path> PRODUCTION_ROOTS = List.of(
			Path.of("src/main/java"), Path.of("src/client/java"));

	private static final List<String> VESSEL_PACKAGE_PARENTS = List.of(
			"jujutsu/mod/character/",
			"jujutsu/mod/client/character/",
			"jujutsu/mod/client/render/",
			"jujutsu/mod/client/vfx/");

	/** Runtime class resolution, which erases the evidence a structural rule needs. */
	private static final Pattern DYNAMIC_LOADING = Pattern.compile(
			"\\b(Class\\s*\\.\\s*forName|ServiceLoader\\s*\\.\\s*load|MethodHandles\\s*\\.|\\.\\s*loadClass\\s*\\()");

	/**
	 * Shared files allowed to name a vessel type, by role. Registering a vessel's item, entity, effect or
	 * component is content registration, and adding one more is ordinary work — so these are not pinned
	 * to an exact set, only to the role.
	 */
	private static final Set<String> REGISTRIES_MAY_NAME_VESSEL_CONTENT = Set.of(
			"src/main/java/jujutsu/mod/character/JujutsuCharacters.java",
			"src/main/java/jujutsu/mod/registry/JujutsuItems.java",
			"src/main/java/jujutsu/mod/registry/JujutsuEntities.java",
			"src/main/java/jujutsu/mod/registry/JujutsuEffects.java",
			"src/main/java/jujutsu/mod/registry/JujutsuDataComponents.java",
			"src/client/java/jujutsu/mod/client/character/JujutsuCharacterClients.java");

	/**
	 * The shared files that name a vessel for reasons that are debt rather than design. Pinned to the exact
	 * type names, so none can quietly acquire another reference, and so fixing one fails this test and
	 * forces its entry to be deleted with it.
	 *
	 * <p>{@code JujutsuNetworking} was the second entry and is gone: E13 is closed, and the grep half of
	 * that proof is this map shrinking. It named {@code SelfResonanceRuntime} through an inline fully
	 * qualified name, which is precisely the shape a source-text tripwire catches and an import-based one
	 * does not.
	 */
	private static final Map<String, Set<String>> TRACKED_DEBT = Map.of(
			"src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java",
			Set.of("ProjectJjkNailEmbedding", "ProjectJjkNailEntity", "<package nobara>"));

	@Test
	void vesselPackageClassifierAcceptsOnlyRegisteredVesselRoots() {
		assertTrue(isInsideVesselPackage(Path.of("src/main/java/jujutsu/mod/character/megumi/MegumiProfile.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src\\main\\java\\jujutsu\\mod\\character\\megumi\\vfx\\MegumiVfxIds.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src\\client\\java\\jujutsu\\mod\\client\\character\\megumi\\vfx\\MegumiVfxRecipes.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/character/megumi/particle/MegumiShadowMoteParticle.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src\\client\\java\\jujutsu\\mod\\client\\render\\megumi\\MegumiDivineDogRenderer.java"), "megumi"));
		assertTrue(isInsideVesselPackage(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java"), "nobara"));
		assertTrue(isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/character/todo/TodoClientDefinition.java"), "todo"));

		assertTrue(!isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/rich/megumi/Anything.java"), "megumi"));
		assertTrue(!isInsideVesselPackage(Path.of("src/main/java/jujutsu/mod/vfx/TodoVfxIds.java"), "todo"));
		assertTrue(!isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/fx/NobaraHudState.java"), "nobara"));
		assertTrue(!isInsideVesselPackage(Path.of("src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java"), "nobara"));
	}

	@Test
	void sharedProductionCodeResolvesNoClassAtRuntime() {
		Map<String, List<String>> offenders = new TreeMap<>();
		for (Path file : sharedProductionFiles()) {
			String body = withoutCommentsAndLiterals(read(file));
			List<String> hits = DYNAMIC_LOADING.matcher(body).results()
					.map(result -> result.group(1).replaceAll("\\s+", ""))
					.distinct()
					.toList();
			if (!hits.isEmpty()) {
				offenders.put(file.toString().replace('\\', '/'), hits);
			}
		}
		assertTrue(offenders.isEmpty(),
				() -> "shared production code must not resolve classes at runtime — a name built at runtime "
						+ "reaches a vessel with no dependency for any structural rule to see: " + offenders);
	}

	@Test
	void sharedProductionCodeNamesNoVesselType() {
		Map<String, Set<String>> vesselTypes = vesselOwnedTypeNames();
		assertTrue(vesselTypes.size() >= 2, () -> "expected a type inventory per vessel, got " + vesselTypes.keySet());

		Map<String, Set<String>> unexpected = new TreeMap<>();
		Map<String, Set<String>> debtFound = new TreeMap<>();

		for (Path file : sharedProductionFiles()) {
			String key = file.toString().replace('\\', '/');
			if (REGISTRIES_MAY_NAME_VESSEL_CONTENT.contains(key)) {
				continue;
			}
			String body = withoutCommentsAndLiterals(read(file));
			String declaredHere = fileName(file);

			Set<String> named = new TreeSet<>();
			for (Set<String> perVessel : vesselTypes.values()) {
				for (String type : perVessel) {
					if (type.equals(declaredHere)) {
						continue;
					}
					if (Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(body).find()) {
						named.add(type);
					}
				}
			}
			for (String id : VESSEL_IDS) {
				if (Pattern.compile("\\bjujutsu\\.mod\\.character\\." + id + "\\b").matcher(body).find()) {
					named.add("<package " + id + ">");
				}
			}
			if (named.isEmpty()) {
				continue;
			}
			if (TRACKED_DEBT.containsKey(key)) {
				debtFound.put(key, named);
			} else {
				unexpected.put(key, named);
			}
		}

		assertTrue(unexpected.isEmpty(),
				() -> "shared production code names a vessel's own type. A compile-time constant read this "
						+ "way is folded by javac and leaves no dependency, so no structural rule can see it: "
						+ unexpected);
		assertEquals(new TreeMap<>(TRACKED_DEBT), debtFound,
				"the tracked shared-code references to vessel types changed. Growing one means a new leak; "
						+ "shrinking one means a leak was fixed and its entry here should go with it (E14).");
	}

	@Test
	void noVesselNamesAnotherVesselsType() {
		// The bytecode rule vesselsDoNotKnowEachOther has one demonstrated bypass: a compile-time constant.
		// `static final int X = TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS` inside Nobara's package is folded
		// by javac and leaves no edge, so the structural rule passes. Confirmed green before this test
		// existed. Vessel source is scanned here for exactly one thing — another vessel's type names.
		Map<String, Set<String>> vesselTypes = vesselOwnedTypeNames();
		Map<String, Set<String>> offenders = new TreeMap<>();

		for (String owner : VESSEL_IDS) {
			for (Path root : PRODUCTION_ROOTS) {
				for (Path file : javaFilesUnder(root)) {
					if (!isInsideVesselPackage(file, owner)) {
						continue;
					}
					String body = withoutCommentsAndLiterals(read(file));
					Set<String> foreign = new TreeSet<>();
					for (String other : VESSEL_IDS) {
						if (other.equals(owner)) {
							continue;
						}
						for (String type : vesselTypes.get(other)) {
							if (Pattern.compile("\\b" + Pattern.quote(type) + "\\b").matcher(body).find()) {
								foreign.add(type);
							}
						}
						if (Pattern.compile("\\bjujutsu\\.mod\\.character\\." + other + "\\b").matcher(body).find()) {
							foreign.add("<package " + other + ">");
						}
					}
					if (!foreign.isEmpty()) {
						offenders.put(file.toString().replace('\\', '/'), foreign);
					}
				}
			}
		}
		assertTrue(offenders.isEmpty(),
				() -> "a vessel names another vessel's type. Read as a compile-time constant this leaves no "
						+ "bytecode dependency at all, so vesselsDoNotKnowEachOther cannot see it: " + offenders);
	}

	private static Map<String, Set<String>> vesselOwnedTypeNames() {
		Map<String, Set<String>> byVessel = new LinkedHashMap<>();
		for (String id : VESSEL_IDS) {
			Set<String> names = new TreeSet<>();
			for (Path root : PRODUCTION_ROOTS) {
				for (Path file : javaFilesUnder(root)) {
					if (isInsideVesselPackage(file, id)) {
						names.add(fileName(file));
					}
				}
			}
			assertTrue(!names.isEmpty(), () -> "no types found for vessel '" + id + "'; the scan is looking in the wrong place");
			byVessel.put(id, names);
		}
		return byVessel;
	}

	private static List<Path> sharedProductionFiles() {
		List<Path> shared = new ArrayList<>();
		for (Path root : PRODUCTION_ROOTS) {
			for (Path file : javaFilesUnder(root)) {
				if (VESSEL_IDS.stream().noneMatch(id -> isInsideVesselPackage(file, id))) {
					shared.add(file);
				}
			}
		}
		assertTrue(shared.size() > 100, () -> "only " + shared.size() + " shared files scanned; this tripwire is not seeing the tree");
		return shared;
	}

	private static List<Path> javaFilesUnder(Path root) {
		assertTrue(Files.isDirectory(root), () -> "missing source root: " + root.toAbsolutePath());
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isInsideVesselPackage(Path file, String vesselId) {
		String path = file.toString().replace('\\', '/');
		for (Path root : PRODUCTION_ROOTS) {
			String rootPrefix = root.toString().replace('\\', '/') + "/";
			if (!path.startsWith(rootPrefix)) {
				continue;
			}
			String packagePath = path.substring(rootPrefix.length());
			return VESSEL_PACKAGE_PARENTS.stream()
					.anyMatch(parent -> packagePath.startsWith(parent + vesselId + "/"));
		}
		return false;
	}

	private static String fileName(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - ".java".length());
	}

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Blanks out comments, string literals, text blocks and char literals. Without this the tripwire would
	 * fail on a comment that merely mentions a vessel type, which would be a comedy rather than a check.
	 */
	static String withoutCommentsAndLiterals(String source) {
		StringBuilder out = new StringBuilder(source.length());
		int i = 0;
		int n = source.length();
		while (i < n) {
			char c = source.charAt(i);
			if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
				while (i < n && source.charAt(i) != '\n') {
					i++;
				}
			} else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
				i += 2;
				while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
					i++;
				}
				i = Math.min(n, i + 2);
			} else if (c == '"' && source.startsWith("\"\"\"", i)) {
				i += 3;
				while (i + 2 < n && !source.startsWith("\"\"\"", i)) {
					i++;
				}
				i = Math.min(n, i + 3);
				out.append("\"\"");
			} else if (c == '"') {
				i++;
				while (i < n && source.charAt(i) != '"') {
					i += source.charAt(i) == '\\' ? 2 : 1;
				}
				i++;
				out.append("\"\"");
			} else if (c == '\'') {
				i++;
				while (i < n && source.charAt(i) != '\'') {
					i += source.charAt(i) == '\\' ? 2 : 1;
				}
				i++;
				out.append("''");
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}
}
