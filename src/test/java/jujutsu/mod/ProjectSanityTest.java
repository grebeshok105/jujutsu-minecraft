package jujutsu.mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ProjectSanityTest {
	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path MAIN_JAVA = ROOT.resolve("src/main/java");
	private static final Path MAIN_RESOURCES = ROOT.resolve("src/main/resources");
	private static final Path JUJUTSU_ASSETS = MAIN_RESOURCES.resolve("assets/jujutsumod");
	private static final Pattern TEXTURE_ID = Pattern.compile("\"jujutsumod:([^\"]+)\"");
	private static final Pattern SOUND_ID = Pattern.compile("\"(?:minecraft|jujutsumod):([^\"]+)\"");
	private static final Pattern ITEM_MODEL_ID = Pattern.compile("\"model\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_TEXTURE_ID = Pattern.compile("\"layer0\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final String FORBIDDEN_FABRIC_IMPL = "net.fabricmc.fabric." + "impl.";
	private static final String CLIENT_IMPORT = "import net.minecraft." + "client.";

	private ProjectSanityTest() {}

	public static void main(String[] args) throws IOException {
		assertParticleJsonTexturesExist();
		assertItemDefinitionsResolveToTextures();
		assertSoundReferencesAreLocalAndPresent();
		assertNoForbiddenImports();
		System.out.println("ProjectSanityTest passed");
	}

	private static void assertParticleJsonTexturesExist() throws IOException {
		Path particlesDir = JUJUTSU_ASSETS.resolve("particles");
		try (Stream<Path> files = Files.list(particlesDir)) {
			for (Path particleJson : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				String json = Files.readString(particleJson);
				Matcher matcher = TEXTURE_ID.matcher(json);
				boolean foundTexture = false;
				while (matcher.find()) {
					foundTexture = true;
					Path texture = JUJUTSU_ASSETS.resolve("textures/particle").resolve(matcher.group(1) + ".png");
					assert Files.exists(texture) : "Missing particle texture " + texture + " referenced by " + particleJson;
				}
				assert foundTexture : "Particle JSON has no jujutsumod textures: " + particleJson;
			}
		}
	}

	private static void assertItemDefinitionsResolveToTextures() throws IOException {
		Path itemsDir = JUJUTSU_ASSETS.resolve("items");
		try (Stream<Path> files = Files.list(itemsDir)) {
			for (Path itemDefinition : files.filter(path -> path.toString().endsWith(".json")).toList()) {
				String json = Files.readString(itemDefinition);
				Matcher modelMatcher = ITEM_MODEL_ID.matcher(json);
				assert modelMatcher.find() : "Item definition has no jujutsumod model: " + itemDefinition;
				String modelName = modelMatcher.group(1);
				Path model = JUJUTSU_ASSETS.resolve("models/item").resolve(modelName + ".json");
				assert Files.exists(model) : "Missing item model " + model + " referenced by " + itemDefinition;

				String modelJson = Files.readString(model);
				Matcher textureMatcher = ITEM_TEXTURE_ID.matcher(modelJson);
				assert textureMatcher.find() : "Item model has no jujutsumod layer0 texture: " + model;
				Path texture = JUJUTSU_ASSETS.resolve("textures/item").resolve(textureMatcher.group(1) + ".png");
				assert Files.exists(texture) : "Missing item texture " + texture + " referenced by " + model;
			}
		}
	}

	private static void assertSoundReferencesAreLocalAndPresent() throws IOException {
		Path soundsJson = JUJUTSU_ASSETS.resolve("sounds.json");
		String json = Files.readString(soundsJson);
		Matcher matcher = SOUND_ID.matcher(json);
		while (matcher.find()) {
			Path sound = JUJUTSU_ASSETS.resolve("sounds").resolve(matcher.group(1) + ".ogg");
			assert Files.exists(sound) : "Missing sound asset " + sound + " referenced by " + soundsJson;
		}
	}

	private static void assertNoForbiddenImports() throws IOException {
		try (Stream<Path> files = Files.walk(ROOT.resolve("src"))) {
			for (Path javaFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(javaFile);
				assert !source.contains(FORBIDDEN_FABRIC_IMPL) : "Forbidden Fabric impl import in " + javaFile;
				if (javaFile.startsWith(MAIN_JAVA)) {
					assert !source.contains(CLIENT_IMPORT) : "Client import in common source set: " + javaFile;
				}
			}
		}
	}
}
