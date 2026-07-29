package jujutsu.mod.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jujutsu.mod.character.JujutsuCharacter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The vessel seam, checked against compiled bytecode.
 *
 * <p>An earlier version of this file hardcoded the two vessel names. An adversarial review broke it
 * in five ways through that one weakness alone — a package spelled {@code nobaranet}, a third vessel
 * called {@code yuji}, a class moved sideways out of its package — so the identities now come from
 * {@link JujutsuCharacter} and the packages under {@code character} are checked fail-closed.
 *
 * <p><b>What bytecode rules cannot see, and this file does not pretend to cover:</b> a class name
 * assembled at runtime, and a compile-time constant, which javac folds into the caller so that no
 * reference survives. {@code SourceBoundaryTripwireTest} covers both, honestly, by reading source
 * text — it is a grep and is named like one.
 *
 * <p><b>The two outputs are imported separately and never merged.</b> Loom splits the source sets, so
 * a single classpath import would fuse them and the side-separation rule would have nothing to find.
 * Each import asserts a floor on its class count, because a rule running over an empty set reports
 * success just as loudly as one running over the real tree.
 */
class VesselBoundaryTest {
	private static final int MIN_MAIN_CLASSES = 194;
	private static final int MIN_CLIENT_CLASSES = 212;

	/** Vessel identities, derived rather than spelled, so a third vessel is covered the day it exists. */
	private static final List<String> VESSEL_IDS = Arrays.stream(JujutsuCharacter.values())
			.map(JujutsuCharacter::id)
			.filter(id -> !id.equals(JujutsuCharacter.NONE.id()))
			.toList();

	private static final String[] VESSEL_PACKAGES = VESSEL_IDS.stream()
			.map(id -> ".." + id + "..")
			.toArray(String[]::new);

	/**
	 * Class-name prefixes follow the registered vessels. ProjectJjk is Nobara's documented legacy
	 * prefix from the pre-vessel-seam package and remains an explicit exception until that debt is
	 * retired.
	 */
	private static final List<String> VESSEL_CLASS_NAME_PREFIXES = Stream.concat(
				VESSEL_IDS.stream().map(VesselBoundaryTest::capitalized),
				Stream.of("ProjectJjk"))
			.toList();

	/** Package roots whose next segment must name a registered vessel. Fail-closed, so a new one fails. */
	private static final List<String> VESSEL_PARENTS = List.of(
			"jujutsu.mod.character.",
			"jujutsu.mod.client.character.",
			"jujutsu.mod.client.render.",
			"jujutsu.mod.client.vfx.");

	/**
	 * Content registries. Registering a vessel's item, entity, effect or component is not dispatch, so
	 * they may name vessel types — but only to build them. {@link #registriesBuildVesselContentAndDoNotCallIntoIt()}
	 * enforces that distinction, which the earlier per-class exemption could not express and which the
	 * review exploited by hiding per-vessel dispatch inside one of these classes.
	 */
	private static final List<String> CONTENT_REGISTRIES = List.of(
			"jujutsu.mod.registry.JujutsuItems",
			"jujutsu.mod.registry.JujutsuEntities",
			"jujutsu.mod.registry.JujutsuEffects",
			"jujutsu.mod.registry.JujutsuDataComponents");

	private static final String SERVER_VESSEL_REGISTRY = "jujutsu.mod.character.JujutsuCharacters";
	private static final String CLIENT_VESSEL_REGISTRY = "jujutsu.mod.client.character.JujutsuCharacterClients";

	/** Pinned by {@link #theNetworkLayerTouchesNoVesselCode()}; held at zero since E13 was closed. */
	private static final String NETWORK_LAYER = "jujutsu.mod.network.JujutsuNetworking";

	/** See E14. Renders a Nobara entity from the shared render package; belongs under render.nobara. */
	private static final String MISPLACED_NAIL_RENDERER = "jujutsu.mod.client.render.ProjectJjkNailRenderer";

	/**
	 * Every packet that exists. Pinned as a whole set, because "a vessel must not own an ability packet"
	 * is a claim about the packet inventory, not about where a class happens to sit: the review added a
	 * private input path by declaring the payload in the shared network package, where every
	 * location-based rule waved it through.
	 */
	private static final Set<String> KNOWN_PAYLOADS = Set.of(
			"jujutsu.mod.network.SelectCharacterPayload",
			"jujutsu.mod.network.CharacterAbilityPayload",
			"jujutsu.mod.network.SelectCurseLinkPayload",
			"jujutsu.mod.network.CharacterSelectionSyncPayload",
			"jujutsu.mod.network.AbilityCooldownPayload",
			"jujutsu.mod.network.CurseLinkOptionsPayload",
			"jujutsu.mod.network.VfxCuePayload",
			"jujutsu.mod.network.BlackFlashFocusPayload");

	/** Vessel-named classes that sit outside their vessel's packages today. Two are documented, two are debt. */
	private static final Set<String> VESSEL_NAMED_CLASSES_OUTSIDE_VESSEL_PACKAGES = Set.of(
			"jujutsu.mod.vfx.NobaraVfxIds",
			"jujutsu.mod.vfx.TodoVfxIds",
			"jujutsu.mod.client.fx.NobaraHudState",
			MISPLACED_NAIL_RENDERER);

	private static JavaClasses mainClasses;
	private static JavaClasses clientClasses;

	@BeforeAll
	static void importEachOutputOnItsOwn() {
		mainClasses = importOnly(Path.of("build/classes/java/main"), MIN_MAIN_CLASSES);
		clientClasses = importOnly(Path.of("build/classes/java/client"), MIN_CLIENT_CLASSES);
	}

	@Test
	void scanCompletenessFloorsRejectOneLessThanTheMeasuredTree() {
		Path mainOutput = Path.of("build/classes/java/main");
		assertThrows(AssertionError.class,
				() -> assertImportedClassFloor(mainOutput, MIN_MAIN_CLASSES, ignored -> MIN_MAIN_CLASSES - 1));
		assertDoesNotThrow(() -> assertImportedClassFloor(mainOutput, MIN_MAIN_CLASSES, ignored -> MIN_MAIN_CLASSES));
		Path clientOutput = Path.of("build/classes/java/client");
		assertThrows(AssertionError.class,
				() -> assertImportedClassFloor(clientOutput, MIN_CLIENT_CLASSES, ignored -> MIN_CLIENT_CLASSES - 1));
		assertDoesNotThrow(() -> assertImportedClassFloor(clientOutput, MIN_CLIENT_CLASSES, ignored -> MIN_CLIENT_CLASSES));
	}

	@Test
	void noClientTypeLivesInTheServerOutput() {
		// Measured: `src/main` cannot *depend* on net.minecraft.client or jujutsu.mod.client at all,
		// because splitEnvironmentSourceSets keeps both off its compile classpath — an attempt is
		// "package does not exist". What the compiler does not catch is a file under src/main/java that
		// declares a client package: javac accepts the mismatch and the class lands in the server output,
		// which a dedicated server then loads. That is the provable case and the one the proof rests on.
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.should().resideInAnyPackage("jujutsu.mod.client..", "net.minecraft.client..")
				.because("a dedicated server loads this whole output; a client-named type here ships to it")
				.check(mainClasses);

		// Tripwire for the day the source sets stop being split. It cannot fail while they are.
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "jujutsu.mod.client..")
				.because("src/main is loaded by dedicated servers, which have no client classes")
				.check(mainClasses);
	}

	@Test
	void vesselsDoNotKnowEachOther() {
		// Every ordered pair, derived from the enum, in both outputs. A third vessel joins automatically.
		for (String from : VESSEL_IDS) {
			for (String to : VESSEL_IDS) {
				if (from.equals(to)) {
					continue;
				}
				for (JavaClasses output : List.of(mainClasses, clientClasses)) {
					noClasses().that().resideInAPackage(".." + from + "..")
							.should().dependOnClassesThat().resideInAPackage(".." + to + "..")
							.because("a vessel must stay removable without touching another vessel")
							.check(output);
				}
			}
		}
	}

	@Test
	void everyPackageUnderAVesselParentNamesARegisteredVessel() {
		// Fail-closed. The review walked past every rule by inventing `character.nobaranet` and
		// `character.yuji`: both are vessel code by position, and neither matched a hardcoded pattern.
		for (JavaClasses output : List.of(mainClasses, clientClasses)) {
			for (JavaClass type : output) {
				String pkg = type.getPackageName();
				for (String parent : VESSEL_PARENTS) {
					if (!pkg.startsWith(parent)) {
						continue;
					}
					String segment = pkg.substring(parent.length()).split("\\.")[0];
					assertTrue(VESSEL_IDS.contains(segment),
							() -> "package '" + pkg + "' sits where vessel code lives but '" + segment
									+ "' is not a registered vessel id " + VESSEL_IDS
									+ "; either register the vessel or move the package");
				}
			}
		}
	}

	@Test
	void vesselNamedClassesStayInTheirVesselPackage() {
		// Names are evidence, not proof, so this pins the exact set rather than banning a prefix. The
		// review moved TodoProfile wholesale into jujutsu.mod.combat and nothing objected; that move now
		// fails here. A class legitimately leaving the list fails too, which is the point.
		Set<String> found = new TreeSet<>();
		for (JavaClasses output : List.of(mainClasses, clientClasses)) {
			for (JavaClass type : output) {
				if (type.getSimpleName().isEmpty() || type.getName().contains("$")) {
					continue;
				}
				if (VESSEL_CLASS_NAME_PREFIXES.stream().noneMatch(type.getSimpleName()::startsWith)) {
					continue;
				}
				if (resideInAnyPackage(VESSEL_PACKAGES).test(type)) {
					continue;
				}
				found.add(type.getName());
			}
		}
		assertEquals(new TreeSet<>(VESSEL_NAMED_CLASSES_OUTSIDE_VESSEL_PACKAGES), found,
				"a vessel-named class moved into or out of shared packages. Two entries are deliberate "
						+ "(NobaraVfxIds and TodoVfxIds are the per-vessel cue ids AGENTS.md prescribes); two are "
						+ "debt tracked as E14. Anything new here is a vessel escaping its package.");
	}

	@Test
	void sharedServerCodeAsksTheVesselRatherThanNamingOne() {
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.and().resideOutsideOfPackages(VESSEL_PACKAGES)
				.and().doNotHaveFullyQualifiedName(SERVER_VESSEL_REGISTRY)
				.and().doNotHaveFullyQualifiedName(CONTENT_REGISTRIES.get(0))
				.and().doNotHaveFullyQualifiedName(CONTENT_REGISTRIES.get(1))
				.and().doNotHaveFullyQualifiedName(CONTENT_REGISTRIES.get(2))
				.and().doNotHaveFullyQualifiedName(CONTENT_REGISTRIES.get(3))
				.and().doNotHaveFullyQualifiedName(NETWORK_LAYER)
				.should().dependOnClassesThat().resideInAnyPackage(VESSEL_PACKAGES)
				.because("shared code asks the vessel's definition instead of naming a vessel")
				.check(mainClasses);
	}

	@Test
	void sharedClientCodeAsksTheVesselRatherThanNamingOne() {
		noClasses().that().resideInAPackage("jujutsu.mod.client..")
				.and().resideOutsideOfPackages(VESSEL_PACKAGES)
				.and().doNotHaveFullyQualifiedName(CLIENT_VESSEL_REGISTRY)
				.and().doNotHaveFullyQualifiedName(MISPLACED_NAIL_RENDERER)
				.should().dependOnClassesThat().resideInAnyPackage(VESSEL_PACKAGES)
				.because("shared client code reads the vessel's client definition instead of naming a vessel")
				.check(clientClasses);
	}

	@Test
	void registriesBuildVesselContentAndDoNotCallIntoIt() {
		// The exemption above is for *registration*, and this is what makes that word mean something.
		// Constructing a vessel's item and reading its constants is registration; calling a method on a
		// vessel class is the shared side running vessel logic, which is what the review smuggled in.
		for (String registry : CONTENT_REGISTRIES) {
			noClasses().that().haveFullyQualifiedName(registry)
					.should().callMethodWhere(target(owner(resideInAnyPackage(VESSEL_PACKAGES))))
					.because("a content registry may build a vessel's content, never run its logic")
					.check(mainClasses);
		}
		for (String registry : List.of(SERVER_VESSEL_REGISTRY)) {
			noClasses().that().haveFullyQualifiedName(registry)
					.should().callMethodWhere(target(owner(resideInAnyPackage(VESSEL_PACKAGES))))
					.because("the vessel registry constructs definitions; it does not drive them")
					.check(mainClasses);
		}
		noClasses().that().haveFullyQualifiedName(CLIENT_VESSEL_REGISTRY)
				.should().callMethodWhere(target(owner(resideInAnyPackage(VESSEL_PACKAGES))))
				.because("the client vessel registry constructs definitions; it does not drive them")
				.check(clientClasses);
	}

	@Test
	void thePacketSurfaceIsExactlyWhatWeThinkItIs() {
		// The inventory, not the location. A ninth packet must be argued for in review, wherever it lives.
		Set<String> payloads = new TreeSet<>();
		for (JavaClasses output : List.of(mainClasses, clientClasses)) {
			for (JavaClass type : output) {
				boolean isPayload = type.getAllRawInterfaces().stream()
						.anyMatch(i -> i.getName().equals("net.minecraft.network.protocol.common.custom.CustomPacketPayload"));
				if (isPayload && !type.isInterface()) {
					payloads.add(type.getName());
				}
			}
		}
		assertEquals(new TreeSet<>(KNOWN_PAYLOADS), payloads,
				"the wire surface changed. Every ability cast travels on CharacterAbilityPayload; a new "
						+ "packet is a new input path and has to be justified, not merely placed somewhere "
						+ "no rule looks.");
	}

	@Test
	void noVesselOwnsItsOwnAbilityPacket() {
		// Checked against BOTH outputs. It used to check only the server one, and the review put a payload
		// straight into the client todo package — the case this rule names verbatim — and it passed.
		// allowEmptyShould is on for a measured reason: the client output declares no payload at all
		// today, and ArchUnit refuses an empty subject set by default. Without it this rule fails on the
		// client for having nothing to check, which is a different thing from passing. The guarantee that
		// a payload appearing anywhere gets noticed comes from thePacketSurfaceIsExactlyWhatWeThinkItIs,
		// which counts the inventory instead of filtering it.
		for (JavaClasses output : List.of(mainClasses, clientClasses)) {
			noClasses().that().implement("net.minecraft.network.protocol.common.custom.CustomPacketPayload")
					.should().resideInAnyPackage(VESSEL_PACKAGES)
					.because("abilities travel on the shared CharacterAbilityPayload, not on a per-vessel packet")
					.allowEmptyShould(true)
					.check(output);
		}
	}

	@Test
	void theNetworkLayerTouchesNoVesselCode() {
		// Matched by package now, not by substring: `.nobara.` also matched nothing in `.nobaranet.`,
		// which is exactly how the review smuggled a second path past this test.
		//
		// This used to allow exactly one entry — SelfResonanceRuntime, reached by an inline fully qualified
		// name, tracked as E13. That call now goes through CharacterDefinition.selectCurseLink like every
		// other per-vessel decision, so the allowance is gone and the expected set is empty. Asserting zero
		// is worth strictly more than deleting the rule: a receiver wired straight to a vessel runtime is
		// the single easiest seam breach to write, and this is the only check that sees it.
		Set<String> vesselDependencies = mainClasses.get(NETWORK_LAYER).getDirectDependenciesFromSelf().stream()
				.map(dependency -> dependency.getTargetClass())
				.filter(resideInAnyPackage(VESSEL_PACKAGES))
				.map(JavaClass::getName)
				.collect(Collectors.toCollection(TreeSet::new));

		assertEquals(Set.of(), vesselDependencies,
				"the network layer reached into a vessel package. A packet's handler must name a shared seam "
						+ "— CharacterAbilityExecutor or a CharacterDefinition hook — never a vessel runtime, "
						+ "or that vessel has acquired a private input path no other vessel can be given.");
	}

	private static JavaClasses importOnly(Path output, int atLeast) {
		assertTrue(Files.isDirectory(output), () -> "compiled output missing: " + output.toAbsolutePath());
		JavaClasses imported = new ClassFileImporter().importPath(output);
		assertImportedClassFloor(output, atLeast, ignored -> imported.size());
		return imported;
	}

	private static void assertImportedClassFloor(Path output, int floor, ToIntFunction<Path> classCount) {
		int actual = classCount.applyAsInt(output);
		assertTrue(actual >= floor,
				() -> "only " + actual + " classes imported from " + output
						+ "; the rules below would check almost nothing");
	}

	private static String capitalized(String id) {
		return Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}
}
