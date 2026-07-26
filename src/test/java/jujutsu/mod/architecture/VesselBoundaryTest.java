package jujutsu.mod.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The vessel seam, checked against compiled bytecode instead of against the spelling of the source.
 *
 * <p>These rules replace source-text assertions that could pass on semantically broken code and fail
 * on a rename. What they deliberately do <b>not</b> do is judge behaviour — whether a method uses the
 * value it just computed is not a structural question, and pretending otherwise would only buy a more
 * expensive grep. Asset, model, JSON, sound and localization checks stay file reads in
 * {@code ProjectSanityTest}, where they belong.
 *
 * <p><b>The two outputs are imported separately and never merged.</b> Loom splits the source sets, so
 * a single classpath import would fuse them and the side-separation rule would silently have nothing
 * to find. Each import asserts a floor on its class count, because a rule that runs over an empty set
 * reports success just as loudly as one that runs over the real tree.
 */
class VesselBoundaryTest {
	private static final String NOBARA = "..nobara..";
	private static final String TODO = "..todo..";

	/**
	 * The seam itself: the two registries whose whole job is to name every vessel exactly once. This is
	 * the approved exception, and it is two classes rather than a package wildcard on purpose.
	 */
	private static final String SERVER_VESSEL_REGISTRY = "jujutsu.mod.character.JujutsuCharacters";
	private static final String CLIENT_VESSEL_REGISTRY = "jujutsu.mod.client.character.JujutsuCharacterClients";

	/**
	 * A known defect, not a decision. {@code JujutsuNetworking.registerServerReceivers} registers the
	 * {@code SelectCurseLinkPayload} receiver by calling {@code SelfResonanceRuntime.select} through an
	 * inline fully qualified name — which is exactly why no source-text check ever saw it, and why
	 * {@code NobaraAbilitySlotsTest} went as far as asserting the call is present. It belongs in Nobara's
	 * {@code registerServerHooks}, next to the seven runtimes already installed there.
	 *
	 * <p>Excluding the class would leave a hole a second vessel could walk through, so
	 * {@link #theOneKnownNetworkLeakDoesNotGrow()} pins the exact set instead. That test fails both if
	 * another vessel reference is added here and if this one is finally removed — the second failure is
	 * the point, because it forces this entry to be deleted rather than inherited.
	 */
	private static final String NETWORK_LAYER = "jujutsu.mod.network.JujutsuNetworking";
	private static final String KNOWN_NETWORK_LEAK = "jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime";

	private static JavaClasses mainClasses;
	private static JavaClasses clientClasses;

	@BeforeAll
	static void importEachOutputOnItsOwn() {
		mainClasses = importOnly(Path.of("build/classes/java/main"), 90);
		clientClasses = importOnly(Path.of("build/classes/java/client"), 150);
	}

	@Test
	void noClientTypeLivesInTheServerOutput() {
		// Measured before this rule was written: `src/main` cannot *depend* on net.minecraft.client or
		// jujutsu.mod.client at all, because splitEnvironmentSourceSets keeps both off its compile
		// classpath — an attempt is "package does not exist", not a lint failure. So the dependency
		// direction is already the compiler's job, and asserting it here would be a rule that can never
		// go red. What the compiler does *not* catch is a file under src/main/java that simply declares a
		// client package: javac accepts the directory mismatch and the class lands in the server output,
		// which a dedicated server then loads. That is the case worth a rule, and it is provable.
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.should().resideInAnyPackage("jujutsu.mod.client..", "net.minecraft.client..")
				.because("a dedicated server loads this whole output; a client-named type here ships to it")
				.check(mainClasses);

		// The tripwire for the day the source sets stop being split. It cannot fail while they are, and it
		// is deliberately not the part of this test the mutation proof rests on.
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.should().dependOnClassesThat().resideInAnyPackage("net.minecraft.client..", "jujutsu.mod.client..")
				.because("src/main is loaded by dedicated servers, which have no client classes")
				.check(mainClasses);
	}

	@Test
	void vesselsDoNotKnowEachOther() {
		// Each vessel is meant to be removable and addable on its own. One import between them is how
		// that stops being true, and it is invisible until the third vessel makes it expensive.
		noClasses().that().resideInAPackage(NOBARA)
				.should().dependOnClassesThat().resideInAPackage(TODO)
				.because("a vessel must stay removable without touching another vessel")
				.check(mainClasses);
		noClasses().that().resideInAPackage(TODO)
				.should().dependOnClassesThat().resideInAPackage(NOBARA)
				.because("a vessel must stay removable without touching another vessel")
				.check(mainClasses);
		noClasses().that().resideInAPackage(NOBARA)
				.should().dependOnClassesThat().resideInAPackage(TODO)
				.because("a vessel must stay removable without touching another vessel")
				.check(clientClasses);
		noClasses().that().resideInAPackage(TODO)
				.should().dependOnClassesThat().resideInAPackage(NOBARA)
				.because("a vessel must stay removable without touching another vessel")
				.check(clientClasses);
	}

	@Test
	void sharedServerCodeAsksTheVesselRatherThanNamingOne() {
		// The allowlist is four content registries and the vessel registry, named one by one. Content
		// registration is a different category from dispatch: an item, entity, effect or component has to
		// be registered somewhere central, and doing so does not make shared code branch on a character.
		noClasses().that().resideInAPackage("jujutsu.mod..")
				.and().resideOutsideOfPackages(NOBARA, TODO)
				.and().doNotHaveFullyQualifiedName(SERVER_VESSEL_REGISTRY)
				.and().doNotHaveFullyQualifiedName("jujutsu.mod.registry.JujutsuItems")
				.and().doNotHaveFullyQualifiedName("jujutsu.mod.registry.JujutsuEntities")
				.and().doNotHaveFullyQualifiedName("jujutsu.mod.registry.JujutsuEffects")
				.and().doNotHaveFullyQualifiedName("jujutsu.mod.registry.JujutsuDataComponents")
				.and().doNotHaveFullyQualifiedName(NETWORK_LAYER)
				.should().dependOnClassesThat().resideInAnyPackage(NOBARA, TODO)
				.because("shared code asks the vessel's definition instead of naming a vessel")
				.check(mainClasses);
	}

	@Test
	void sharedClientCodeAsksTheVesselRatherThanNamingOne() {
		// ProjectJjkNailRenderer is the one entry that is not obviously a registry. It renders a Nobara
		// entity while sitting in the shared render package, even though jujutsu.mod.client.render.nobara
		// exists. Recorded here as one named class rather than a package wildcard, so moving it later
		// deletes a line instead of widening a hole.
		noClasses().that().resideInAPackage("jujutsu.mod.client..")
				.and().resideOutsideOfPackages(NOBARA, TODO)
				.and().doNotHaveFullyQualifiedName(CLIENT_VESSEL_REGISTRY)
				.and().doNotHaveFullyQualifiedName("jujutsu.mod.client.render.ProjectJjkNailRenderer")
				.should().dependOnClassesThat().resideInAnyPackage(NOBARA, TODO)
				.because("shared client code reads the vessel's client definition instead of naming a vessel")
				.check(clientClasses);
	}

	@Test
	void initNetworkAndInputNeverInstallAVesselDirectly() {
		// The narrow version of the rule above, and the one that actually protects the promise that adding
		// a vessel edits no shared file: both entrypoints loop their registry, so a vessel that installs
		// itself from here would be a runtime nobody registered.
		noClasses().that().resideInAPackage("jujutsu.mod.network..")
				.and().doNotHaveFullyQualifiedName(NETWORK_LAYER)
				.or().haveFullyQualifiedName("jujutsu.mod.JujutsuMod")
				.should().dependOnClassesThat().resideInAnyPackage(NOBARA, TODO)
				.because("mod init and the network layer install vessels through the registry, never by name")
				.check(mainClasses);
		noClasses().that().resideInAnyPackage("jujutsu.mod.client.network..", "jujutsu.mod.client.input..")
				.or().haveFullyQualifiedName("jujutsu.mod.client.JujutsuModClient")
				.should().dependOnClassesThat().resideInAnyPackage(NOBARA, TODO)
				.because("client init, networking and keybinds stay vessel-agnostic")
				.check(clientClasses);
	}

	@Test
	void noVesselOwnsItsOwnAbilityPacket() {
		// Every cast arrives through the one shared CharacterAbilityPayload. A vessel that declares a
		// payload has opened a second input path, which is exactly how Nobara and Todo drifted apart once.
		noClasses().that().implement("net.minecraft.network.protocol.common.custom.CustomPacketPayload")
				.should().resideInAnyPackage(NOBARA, TODO)
				.because("abilities travel on the shared CharacterAbilityPayload, not on a per-vessel packet")
				.check(mainClasses);
	}

	@Test
	void theOneKnownNetworkLeakDoesNotGrow() {
		// The exception carved out above, pinned to its exact shape. Both directions are failures worth
		// having: a second vessel reference here means the hole widened, and an empty set means the leak
		// was fixed and this test plus its allowlist entry should go with it.
		Set<String> vesselDependencies = mainClasses.get(NETWORK_LAYER).getDirectDependenciesFromSelf().stream()
				.map(dependency -> dependency.getTargetClass().getName())
				.filter(name -> name.contains(".nobara.") || name.contains(".todo."))
				.collect(Collectors.toCollection(TreeSet::new));

		assertEquals(Set.of(KNOWN_NETWORK_LEAK), vesselDependencies,
				"JujutsuNetworking may touch exactly one vessel class, the known SelfResonanceRuntime leak. "
						+ "If this set is empty the leak is fixed: delete this test and the allowlist entry beside it. "
						+ "If it grew, a second vessel just acquired a private network path.");
	}

	/**
	 * Imports one compiled output on its own and refuses to continue if it looks empty. A missing or
	 * unbuilt directory would otherwise turn every rule below into a check over nothing.
	 */
	private static JavaClasses importOnly(Path output, int atLeast) {
		assertTrue(Files.isDirectory(output), () -> "compiled output missing: " + output.toAbsolutePath());
		JavaClasses imported = new ClassFileImporter().importPath(output);
		assertTrue(imported.size() >= atLeast,
				() -> "only " + imported.size() + " classes imported from " + output
						+ "; the rules below would check almost nothing");
		return imported;
	}
}
