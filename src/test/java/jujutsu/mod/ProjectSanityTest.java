package jujutsu.mod;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public final class ProjectSanityTest {
	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path MAIN_JAVA = ROOT.resolve("src/main/java");
	private static final Path MAIN_RESOURCES = ROOT.resolve("src/main/resources");
	private static final Path JUJUTSU_ASSETS = MAIN_RESOURCES.resolve("assets/jujutsumod");
	private static final Pattern TEXTURE_ID = Pattern.compile("\"jujutsumod:([^\"]+)\"");
	private static final Pattern SOUND_ID = Pattern.compile("\"(?:minecraft|jujutsumod):([^\"]+)\"");
	private static final Pattern ITEM_MODEL_ID = Pattern.compile("\"model\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_TEXTURE_ID = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_FACE = Pattern.compile("\"(?:north|south|east|west|up|down)\"\\s*:\\s*\\{([^}]*)}");
	private static final Pattern FACE_TEXTURE = Pattern.compile("\"texture\"\\s*:\\s*\"#([a-z0-9_]+)\"");
	private static final Pattern FACE_UV = Pattern.compile("\"uv\"\\s*:\\s*\\[\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*]");
	private static final String FORBIDDEN_FABRIC_IMPL = "net.fabricmc.fabric." + "impl.";
	private static final String CLIENT_IMPORT = "import net.minecraft." + "client.";

	private ProjectSanityTest() {}

	public static void main(String[] args) throws IOException {
		assertParticleJsonTexturesExist();
		assertItemDefinitionsResolveToTextures();
		assertDefaultNobaraItemsUseProjectJjkModels();
		assertItemRegistryUsesKeyedProperties();
		assertDefaultNobaraEntrypointSkipsLegacyRuntime();
		assertNobaraSkinUsesWideArms();
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
				Map<String, Path> textures = new HashMap<>();
				while (textureMatcher.find()) {
					Path texture = JUJUTSU_ASSETS.resolve("textures/item").resolve(textureMatcher.group(2) + ".png");
					assert Files.exists(texture) : "Missing item texture " + texture + " referenced by " + model;
					textures.put(textureMatcher.group(1), texture);
				}
				assert !textures.isEmpty() : "Item model has no jujutsumod item textures: " + model;
				assert textures.containsKey("particle") : "Item model has no particle texture reference: " + model;
				assertModelFaceUvsAreOpaque(model, modelJson, textures);
			}
		}
	}

	private static void assertModelFaceUvsAreOpaque(Path model, String modelJson, Map<String, Path> textures) throws IOException {
		Matcher faceMatcher = ITEM_FACE.matcher(modelJson);
		boolean foundFace = false;
		while (faceMatcher.find()) {
			foundFace = true;
			String faceJson = faceMatcher.group(1);
			Matcher textureMatcher = FACE_TEXTURE.matcher(faceJson);
			Matcher uvMatcher = FACE_UV.matcher(faceJson);
			assert textureMatcher.find() : "Item model face has no texture reference in " + model;
			assert uvMatcher.find() : "Item model face has no uv coordinates in " + model;
			String textureKey = textureMatcher.group(1);
			Path texturePath = textures.get(textureKey);
			assert texturePath != null : "Item model face references unknown texture key #" + textureKey + " in " + model;
			BufferedImage image = ImageIO.read(texturePath.toFile());
			assert image != null : "Could not read item texture " + texturePath;
			double u0 = Double.parseDouble(uvMatcher.group(1));
			double v0 = Double.parseDouble(uvMatcher.group(2));
			double u1 = Double.parseDouble(uvMatcher.group(3));
			double v1 = Double.parseDouble(uvMatcher.group(4));
			assertOpaqueCoverage(model, texturePath, image, u0, v0, u1, v1);
		}
		assert foundFace : "Item model has no 3D faces to validate: " + model;
	}

	private static void assertOpaqueCoverage(Path model, Path texturePath, BufferedImage image, double u0, double v0, double u1, double v1) {
		int minX = Math.max(0, (int) Math.floor(Math.min(u0, u1) / 16.0 * image.getWidth()));
		int maxX = Math.min(image.getWidth(), (int) Math.ceil(Math.max(u0, u1) / 16.0 * image.getWidth()));
		int minY = Math.max(0, (int) Math.floor(Math.min(v0, v1) / 16.0 * image.getHeight()));
		int maxY = Math.min(image.getHeight(), (int) Math.ceil(Math.max(v0, v1) / 16.0 * image.getHeight()));
		int total = Math.max(1, (maxX - minX) * (maxY - minY));
		int opaque = 0;
		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x < maxX; x++) {
				if (((image.getRGB(x, y) >>> 24) & 0xff) >= 224) {
					opaque++;
				}
			}
		}
		assert opaque * 100 / total >= 90 : "Item model face samples transparent texture area in " + model + " texture=" + texturePath;
	}

	private static void assertItemRegistryUsesKeyedProperties() throws IOException {
		Path itemRegistry = MAIN_JAVA.resolve("jujutsu/mod/registry/JujutsuItems.java");
		String source = Files.readString(itemRegistry);
		assert source.contains("ResourceKey.create(BuiltInRegistries.ITEM.key()") : "JujutsuItems must create ResourceKey<Item> before Item construction";
		assert source.contains("properties.setId(key)") : "JujutsuItems must call Item.Properties#setId before Item construction";
		assert !source.contains("new Item(new Item.Properties())") : "JujutsuItems must not construct items with unkeyed default properties";
		assert source.contains("HAIRPIN_NAIL = createProjectJjkNail(\"hairpin_nail\"") : "Default hairpin_nail must use ProjectJJK runtime item";
		assert source.contains("STRAW_DOLL_HAMMER = createProjectJjkHammer(\"straw_doll_hammer\"") : "Default straw_doll_hammer must use ProjectJJK runtime item";
	}

	private static void assertDefaultNobaraEntrypointSkipsLegacyRuntime() throws IOException {
		Path entrypoint = MAIN_JAVA.resolve("jujutsu/mod/JujutsuMod.java");
		String source = Files.readString(entrypoint);
		assert !source.contains("NobaraHairpinRuntime.register()") : "Legacy Nobara runtime must not register when ProjectJJK Nobara is the default";
	}

	private static void assertDefaultNobaraItemsUseProjectJjkModels() throws IOException {
		Path defaultNail = JUJUTSU_ASSETS.resolve("items/hairpin_nail.json");
		Path defaultHammer = JUJUTSU_ASSETS.resolve("items/straw_doll_hammer.json");
		assert Files.readString(defaultNail).contains("\"model\": \"jujutsumod:item/projectjjk_hairpin_nail\"") : "hairpin_nail must render with the ProjectJJK nail model";
		assert Files.readString(defaultHammer).contains("\"model\": \"jujutsumod:item/projectjjk_straw_doll_hammer\"") : "straw_doll_hammer must render with the ProjectJJK hammer model";
	}

	private static void assertNobaraSkinUsesWideArms() throws IOException {
		Path skin = JUJUTSU_ASSETS.resolve("textures/entity/character/nobara.png");
		BufferedImage image = ImageIO.read(skin.toFile());
		assert image != null : "Could not read Nobara skin " + skin;
		assert image.getWidth() == 64 && image.getHeight() == 64 : "Nobara skin must be a 64x64 player skin";
		assertWideArmCoverage(image, 40, 16, "right arm");
		assertWideArmCoverage(image, 32, 48, "left arm");
	}

	private static void assertWideArmCoverage(BufferedImage image, int x, int y, String label) {
		assertOpaqueRect(image, x + 4, y, 8, 4, label + " top/bottom must be classic 4px-wide UVs");
		assertOpaqueRect(image, x, y + 4, 16, 12, label + " side faces must be classic 4px-wide UVs");
	}

	private static void assertOpaqueRect(BufferedImage image, int x, int y, int width, int height, String message) {
		for (int py = y; py < y + height; py++) {
			for (int px = x; px < x + width; px++) {
				int alpha = (image.getRGB(px, py) >>> 24) & 0xff;
				assert alpha >= 224 : message + " at " + px + "," + py;
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
