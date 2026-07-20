package jujutsu.mod;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public final class ProjectSanityTest {
	private static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Path MAIN_JAVA = ROOT.resolve("src/main/java");
	private static final Path CLIENT_JAVA = ROOT.resolve("src/client/java");
	private static final Path MAIN_RESOURCES = ROOT.resolve("src/main/resources");
	private static final Path JUJUTSU_ASSETS = MAIN_RESOURCES.resolve("assets/jujutsumod");
	private static final Pattern TEXTURE_ID = Pattern.compile("\"jujutsumod:([^\"]+)\"");
	private static final Pattern SOUND_ID = Pattern.compile("\"(?:minecraft|jujutsumod):([^\"]+)\"");
	private static final Pattern ITEM_MODEL_ID = Pattern.compile("\"model\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_BASE_MODEL_ID = Pattern.compile("\"base\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_TEXTURE_ID = Pattern.compile("\"([a-z0-9_]+)\"\\s*:\\s*\"jujutsumod:item/([^\"]+)\"");
	private static final Pattern ITEM_FACE = Pattern.compile("\"(?:north|south|east|west|up|down)\"\\s*:\\s*\\{([^}]*)}");
	private static final Pattern FACE_TEXTURE = Pattern.compile("\"texture\"\\s*:\\s*\"#([a-z0-9_]+)\"");
	private static final Pattern FACE_UV = Pattern.compile("\"uv\"\\s*:\\s*\\[\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*]");
	private static final String FORBIDDEN_FABRIC_IMPL = "net.fabricmc.fabric." + "impl.";
	private static final String CLIENT_IMPORT = "import net.minecraft." + "client.";

	private ProjectSanityTest() {}

	public static void main(String[] args) throws IOException {
		assertPerNailHairpinDamageContract();
		assertResonanceUsesInternalDamageAndStagger();
		assertCombatExpansionReviewFixes();
		assertParticleJsonTexturesExist();
		assertItemDefinitionsResolveToTextures();
		assertDefaultNobaraItemsUseProjectJjkModels();
		assertItemRegistryUsesKeyedProperties();
		assertExplicitNobaraActionsAreVisible();
		assertProjectJjkHairpinFinisherNumbers();
		assertHairpinFinishersSnapWithoutMarks();
		assertVfxCueTransportIsRegistered();
		assertVfxDirectorOwnsClientLifecycle();
		assertVfxCoreProvidesReusableChannels();
		assertNobaraUsesVfxCoreRecipes();
		assertNobaraVfxExpansionContract();
		assertStrawDollImpactOwnsCasterScreenFeedback();
		assertDefaultNobaraEntrypointSkipsLegacyRuntime();
		assertLegacyNobaraRuntimeIsRemoved();
		assertNobaraNailsEmbedLikeOpaqueBodyAnchors();
		assertNobaraTargetMarksUseVanillaGlowing();
		assertHairpinFinishersUseSnapImpulse();
		assertFirstPersonSnapPipelineWired();
		assertNobaraHammerHasExplosiveAndPiercingLaunchModes();
		assertStrawDollRitualUsesPhysicalRemnants();
		assertOriginalStrawDollAssetWired();
		assertBoundRemnantVisualVariants();
		assertNobaraNailAuraAvoidsSoulFire();
		assertHairpinScreenOverlayUsesSmoothGradientVignette();
		assertCharacterSelectUsesCheapUiPrimitives();
		assertGeckoLibNobaraPlayerModelWired();
		assertNobaraHeldItemsAndArmPosesWired();
		assertNobaraGeoHeadLookIsSafeAndEnabled();
		assertNobaraGeoRenderRestoresPoseStack();
		assertNobaraSkinUsesWideArms();
		assertSoundReferencesAreLocalAndPresent();
		assertNoForbiddenImports();
		System.out.println("ProjectSanityTest passed");
	}

	private static void assertBoundRemnantVisualVariants() throws IOException {
		Path definition = JUJUTSU_ASSETS.resolve("items/resonance_remnant.json");
		String json = Files.readString(definition);
		assert json.contains("\"property\": \"minecraft:component\"")
				: "Bound Remnant variants must use the native 1.21.8 component model property";
		assert json.contains("\"component\": \"jujutsumod:resonance_remnant_visual\"")
				: "Bound Remnant model must read its synchronized visual component";
		for (String type : List.of("flesh", "token", "curse")) {
			Path model = JUJUTSU_ASSETS.resolve("models/item/resonance_remnant_" + type + ".json");
			Path texture = JUJUTSU_ASSETS.resolve("textures/item/resonance_remnant_" + type + ".png");
			assert Files.exists(model) : "Missing Bound Remnant model: " + model;
			assert Files.readString(model).contains("jujutsumod:item/resonance_remnant_" + type)
					: "Bound Remnant model does not route to its texture: " + model;
			BufferedImage image = ImageIO.read(texture.toFile());
			assert image != null : "Unreadable Bound Remnant texture: " + texture;
			assert image.getWidth() == 64 && image.getHeight() == 64
					: "Bound Remnant texture must be exactly 64x64: " + texture;
			assert ((image.getRGB(0, 0) >>> 24) & 0xff) == 0
					: "Bound Remnant texture must retain a transparent background: " + texture;
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
					assert alpha == 0 || alpha == 255
							: "Bound Remnant pixel art must not contain translucent fringe: " + texture;
				}
			}
		}
	}

	private static void assertPerNailHairpinDamageContract() throws IOException {
		String ritual = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		String damage = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/NobaraDamageSources.java"));
		String bypassTag = Files.readString(MAIN_RESOURCES.resolve("data/minecraft/tags/damage_type/bypasses_cooldown.json"));
		assert ritual.contains("nail.anchor().stableId()") : "Enlarge must select concrete embedded nails";
		assert ritual.contains("HAIRPIN_ENLARGE_DAMAGE_PER_NAIL") : "Enlarge must damage once per concrete nail";
		assert !ritual.contains("ProjectJjkNailMarks.marks(living.getUUID(), gameTime)") : "Boom must not synthesize aggregate anchors from marks";
		assert damage.contains("HAIRPIN") : "Hairpin needs a dedicated damage type";
		assert bypassTag.contains("jujutsumod:hairpin") : "Hairpin damage must bypass vanilla hurt cooldown";
	}

	private static void assertResonanceUsesInternalDamageAndStagger() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java"));
		assert !runtime.contains("MobEffects.WEAKNESS") : "Resonance must not use vanilla Weakness";
		assert runtime.contains("CombatStagger.GLOBAL.apply") : "Resonance must use the explicit action-interrupt stagger";
	}

	private static void assertCombatExpansionReviewFixes() throws IOException {
		String guard = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/NobaraActionGuard.java"));
		assert guard.contains("AttackEntityCallback") && guard.contains("isHammer(player.getItemInHand(hand))") : "Hammer LMB must suppress vanilla entity damage";
		String ritual = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		assert ritual.contains("HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE") && ritual.contains("HairpinChainScheduler")
				: "Hairpin chains must preserve temporarily unloaded nails without blocking later steps";
		String focus = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/combat/BlackFlashFocus.java"));
		assert focus.contains("addTag(TAG)") && focus.contains("BlackFlashFocusPayload") : "Black Flash focus must persist and synchronize";
		String self = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/SelfResonanceRuntime.java"));
		assert self.contains("NobaraDamageSources.selfResonance") && self.contains("NobaraActionTimeline.SELF_RESONANCE") : "Self resonance needs true damage and shared windup";
		String animation = Files.readString(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/animations/projectjjk/npc.animation.json"));
		for (String clip : List.of("hammer_horizontal", "hammer_overhead", "hammer_nail_launch", "hammer_embedded_drive", "hammer_doll_strike", "self_resonance", "black_flash")) {
			assert animation.contains("animation.player_model." + clip) : "Missing dedicated Nobara animation: " + clip;
		}
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
				boolean geckoItem = json.contains("\"type\": \"geckolib:geckolib\"");
				Matcher modelMatcher = (geckoItem ? ITEM_BASE_MODEL_ID : ITEM_MODEL_ID).matcher(json);
				assert modelMatcher.find() : "Item definition has no jujutsumod model: " + itemDefinition;
				String modelName = modelMatcher.group(1);
				Path model = JUJUTSU_ASSETS.resolve("models/item").resolve(modelName + ".json");
				assert Files.exists(model) : "Missing item model " + model + " referenced by " + itemDefinition;

				String modelJson = Files.readString(model);
				if (geckoItem) {
					assert modelJson.contains("\"parent\": \"builtin/entity\"")
							: "GeckoLib item display model must use builtin/entity: " + model;
					continue;
				}
				Matcher textureMatcher = ITEM_TEXTURE_ID.matcher(modelJson);
				Map<String, Path> textures = new HashMap<>();
				while (textureMatcher.find()) {
					Path texture = JUJUTSU_ASSETS.resolve("textures/item").resolve(textureMatcher.group(2) + ".png");
					assert Files.exists(texture) : "Missing item texture " + texture + " referenced by " + model;
					textures.put(textureMatcher.group(1), texture);
				}
				assert !textures.isEmpty() : "Item model has no jujutsumod item textures: " + model;
				if (modelJson.contains("\"parent\": \"minecraft:item/generated\"")) {
					assert textures.containsKey("layer0") : "Generated item model has no layer0 texture: " + model;
					continue;
				}
				assert textures.containsKey("particle") : "3D item model has no particle texture reference: " + model;
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

	private static void assertLegacyNobaraRuntimeIsRemoved() throws IOException {
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/NobaraHairpinRuntime.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/NobaraCombatStateManager.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/HairpinGameplayService.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/HairpinNailItem.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/StrawDollHammerItem.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/debug/HairpinDebugLog.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/fx/HairpinTimeline.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/fx/HairpinVisualProfile.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/network/HairpinFxPayload.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/network/HairpinNailFlightPayload.java"));
		assertMissing(MAIN_JAVA.resolve("jujutsu/mod/network/PreparedNailsPayload.java"));
		assertMissing(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinPlayback.java"));
		assertMissing(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinPlaybackManager.java"));
		assertMissing(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/NobaraNailFlightManager.java"));
		assertMissing(ROOT.resolve("src/test/java/jujutsu/mod/character/nobara/HairpinGameplayServiceTest.java"));
		assertMissing(ROOT.resolve("src/test/java/jujutsu/mod/character/nobara/NobaraCombatStateManagerTest.java"));
		assertMissing(ROOT.resolve("src/test/java/jujutsu/mod/debug/HairpinDebugLogTest.java"));
		assertMissing(ROOT.resolve("src/test/java/jujutsu/mod/fx/HairpinTimelineTest.java"));
		assertMissing(ROOT.resolve("src/test/java/jujutsu/mod/fx/HairpinVisualProfileTest.java"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/models/item/hairpin_nail.json"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/models/item/straw_doll_hammer.json"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/hairpin_nail.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/straw_doll_hammer.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/model/dark_steel.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/model/hammer_wood.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/model/oxblood.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/item/model/steel_edge.png"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/include/hairpin_fracture.glsl"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/include/hairpin_timeline.glsl"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_cinematic_distort.fsh"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_cinematic_distort.vsh"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_snap_edge.fsh"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_snap_edge.vsh"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_residue_dissolve.fsh"));
		assertMissing(MAIN_RESOURCES.resolve("assets/jujutsumod/shaders/post/hairpin_residue_dissolve.vsh"));

		String build = Files.readString(ROOT.resolve("build.gradle"));
		assert !build.contains("testHairpinTimeline") : "Legacy Hairpin timeline test task must not be registered";
		assert !build.contains("testHairpinVisualProfile") : "Legacy Hairpin visual profile test task must not be registered";
		assert !build.contains("testHairpinDebugLog") : "Legacy Hairpin debug-log test task must not be registered";
		assert !build.contains("testNobaraCombatStateManager") : "Legacy Nobara combat-state test task must not be registered";
		assert !build.contains("testHairpinGameplayService") : "Legacy Hairpin gameplay-service test task must not be registered";

		String networking = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert !networking.contains("HairpinFxPayload") : "Legacy Hairpin FX payload must not be registered";
		assert !networking.contains("HairpinNailFlightPayload") : "Legacy nail flight payload must not be registered";
		assert !networking.contains("PreparedNailsPayload") : "Legacy prepared-nail payload must not be registered";

		String clientNetworking = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/network/JujutsuClientNetworking.java"));
		assert !clientNetworking.contains("HairpinFxPayload") : "Client must not receive legacy Hairpin FX payloads";
		assert !clientNetworking.contains("HairpinNailFlightPayload") : "Client must not receive legacy nail flight payloads";
		assert !clientNetworking.contains("PreparedNailsPayload") : "Client must not receive legacy prepared-nail payloads";

		String clientInit = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/JujutsuModClient.java"));
		assert !clientInit.contains("HairpinPlaybackManager") : "Client must not tick the removed legacy Hairpin playback manager";
		assert !clientInit.contains("NobaraNailFlightManager") : "Client must not tick the removed legacy nail-flight manager";
	}

	private static void assertMissing(Path path) {
		assert !Files.exists(path) : "Legacy Nobara file should be removed: " + path;
	}

	private static void assertDefaultNobaraItemsUseProjectJjkModels() throws IOException {
		Path defaultNail = JUJUTSU_ASSETS.resolve("items/hairpin_nail.json");
		Path defaultHammer = JUJUTSU_ASSETS.resolve("items/straw_doll_hammer.json");
		assert Files.readString(defaultNail).contains("\"model\": \"jujutsumod:item/projectjjk_hairpin_nail\"") : "hairpin_nail must render with the ProjectJJK nail model";
		assert Files.readString(defaultHammer).contains("\"model\": \"jujutsumod:item/projectjjk_straw_doll_hammer\"") : "straw_doll_hammer must render with the ProjectJJK hammer model";
		String hammerModel = Files.readString(JUJUTSU_ASSETS.resolve("models/item/projectjjk_straw_doll_hammer.json"));
		assert hammerModel.contains("\"round_face\"") && hammerModel.contains("\"claw_upper\"") && hammerModel.contains("\"claw_lower\"")
				: "Nobara hammer must read as a compact nail-driving claw hammer";
		assert !hammerModel.contains("oxblood") && hammerModel.contains("\"silver\"")
				: "Nobara hammer must use the approved silver/wood palette";
		assert Files.exists(MAIN_RESOURCES.resolve("source-assets/blockbench/nobara_compact_silver_hammer.bbmodel"))
				: "The compact hammer must retain its Blockbench source";
	}

	private static void assertExplicitNobaraActionsAreVisible() throws IOException {
		Path payload = MAIN_JAVA.resolve("jujutsu/mod/network/NobaraActionPayload.java");
		assert Files.exists(payload) : "Nobara Enlarge/Explosion must have an explicit client->server action payload";
		String networking = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert networking.contains("NobaraActionPayload.TYPE") : "Nobara action payload must be registered server-side";
		String keybinds = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("key.jujutsumod.nobara_hairpin_enlarge") : "Hairpin Enlarge must be a visible keybind";
		assert keybinds.contains("key.jujutsumod.nobara_hairpin_explosion") : "Hairpin Explosion must be a visible keybind";
		String screen = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/gui/neon/pages/CharacterPage.java"));
		assert screen.contains("ability.hairpin_enlarge") : "Character select must show Hairpin Enlarge in the kit preview";
		assert screen.contains("ability.hairpin_explosion") : "Character select must show Hairpin Explosion in the kit preview";
		String commands = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/command/JujutsuCommands.java"));
		assert commands.contains("\"enlarge\"") && commands.contains("\"explosion\"") : "Hairpin Enlarge/Explosion must have test commands";
		assert commands.contains("ProjectJjkNobaraActions.tryCast") : "Hairpin commands must use the shared Nobara selection gate";
		String actionRuntime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"));
		assert actionRuntime.contains("CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA") : "Nobara actions must reject non-Nobara players";
		assert actionRuntime.contains("startDirectedHairpin(player)") : "R must call the directed Hairpin runtime";
		assert actionRuntime.contains("startMassHairpin(player)") : "B must call the mass Hairpin runtime";
		String hammer = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java"));
		assert !hammer.contains("tryEnlargeMarkedTarget") : "Hammer must not hide Hairpin Enlarge as a fallback action";
		assert !hammer.contains("detonateMarks") : "Hammer must not hide Hairpin Explosion as a fallback action";
		assert Files.exists(JUJUTSU_ASSETS.resolve("textures/gui/abilities/hairpin_enlargement.png")) : "Missing Hairpin Enlarge UI icon";
		assert Files.exists(JUJUTSU_ASSETS.resolve("textures/gui/abilities/hairpin_explosion.png")) : "Missing Hairpin Explosion UI icon";
	}

	private static void assertProjectJjkHairpinFinisherNumbers() throws IOException {
		String profile = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java"));
		assert profile.contains("HAIRPIN_ENLARGE_RANGE = 20.0") : "Hairpin Enlarge range should match ProjectJJK player ability registry";
		assert profile.contains("HAIRPIN_ENLARGE_DAMAGE_PER_NAIL = 4.0f") : "Hairpin Enlarge should use the approved initial per-nail balance";
		assert profile.contains("HAIRPIN_BOOM_DAMAGE_PER_NAIL = 3.0f") : "Hairpin Explosion should use the approved initial per-nail balance";
		assert profile.contains("DETONATE_DAMAGE_PER_MARK = 0.0f") : "Hairpin Explosion must not scale from old jujutsumod mark damage";
	}

	private static void assertHairpinFinishersSnapWithoutMarks() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		assert runtime.contains("playCasterSnap(level, caster, 1, gameTime)") : "Hairpin finishers should still play the snap gesture when there is no active mark";
		assert runtime.contains("return true;") && runtime.contains("anchors.isEmpty()") : "Empty Hairpin Explosion should consume the action as a snap-only cast instead of showing no-target failure";
	}

	private static void assertNobaraNailsEmbedLikeOpaqueBodyAnchors() throws IOException {
		String nailEntity = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
		assert nailEntity.contains("ProjectJjkNailEmbedding.bodyEmbedPoint") : "Embedded nails must use body-space attachment math, not AABB clamp-only placement";
		assert !nailEntity.contains("setOldPosAndRot(next") : "Embedded nails must not reset old position each tick; that creates visible chase/teleporting";
		assert nailEntity.contains("embeddedLocalOffset()") : "Embedded nail renderer needs synced local body offset for render-attaching to the host";
		assert nailEntity.contains("level().isClientSide() ? entityData.get(DATA_EMBEDDED_TARGET_ID) : anchor.cachedEntityId()") : "Client embedded nail rendering must read the synced target id while the server uses the UUID-backed anchor cache";
		assert nailEntity.contains("living.yBodyRot") : "Embedded living-target nails must anchor to body rotation, not head/look yaw";
		assert nailEntity.contains("if (!level().isClientSide()) {\n\t\t\tsyncEmbeddedAttachment();")
				: "tickEmbedded must not push client-default ZERO local offsets into synched data (collapses nails to host feet)";
		String nailRenderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/ProjectJjkNailRenderer.java"));
		assert !nailRenderer.contains("EMBEDDED_NAIL_RENDER_DEPTH_OFFSET")
				: "Depth must not drag embedded nails down the victim hitbox; keep the original body anchor";
		assert !nailRenderer.contains("renderEmbeddedMark(") : "Embedded nails must not restore the removed broad translucent mark envelope";
		assert nailRenderer.contains("renderEmbeddedMarkPulse") : "Embedded nails must keep only the approved faint readable mark pulse";
		assert !nailRenderer.contains("state.hostOffset") : "Embedded nails must not use the old follower offset that visually chases target position";
		assert nailRenderer.contains("state.embeddedAnchorOffset") : "Embedded nails must render from the target body anchor, relative to the dispatcher render origin";
		assert nailRenderer.contains("living.yBodyRot") : "Embedded nail renderer must use interpolated body rotation for the host attachment";
		assert nailRenderer.contains("ItemDisplayContext.FIXED") : "Nail renderer should use the fixed 3D item transform for stable arrow-like embedding";
	}

	private static void assertNobaraTargetMarksUseVanillaGlowing() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		assert runtime.contains("MobEffects.GLOWING") : "Target marks must use Minecraft's real Glowing effect";
		assert runtime.contains("ChatFormatting.AQUA") : "Target mark Glowing must be cursed-energy cyan/blue, not vanilla white";
		assert runtime.contains("scoreboard.addPlayerToTeam(target.getScoreboardName(), markTeam)") : "Target mark must color Glowing through a scoreboard team";
		assert runtime.contains("previousTeamName") : "Target mark team coloring must remember the victim's previous scoreboard team";
		assert runtime.contains("scoreboard.removePlayerFromTeam(state.scoreboardName(), markTeam)") : "Clearing marks must remove only our temporary glow team membership";
		assert runtime.contains("pruneGlowingMarks(server, gameTime)") : "Expired target marks must clean up temporary glow team state";
		assert runtime.contains("restoreAllGlowTeams(server.getScoreboard())") : "Server stop must restore glow teams before dropping in-memory snapshots";
		assert runtime.contains("clearGlowingMark") : "Consumed marks must remove our Glowing effect instead of leaving a stale target mark";
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxWorldChannel.java"));
		assert !renderer.contains("renderTargetMarks") : "Target marks must not be custom world geometry when vanilla Glowing is active";
		assert !renderer.contains("renderBodyGlowShell") : "Target marks must not be the old free-floating body shell";
		assert !renderer.contains("TargetMarkRenderManager") : "Target mark world-render manager should not drive the visual mark";
	}

	private static void assertHairpinFinishersUseSnapImpulse() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		assert runtime.contains("NobaraVfxIds.FIRST_PERSON_SNAP") : "Hairpin finishers must request the typed first-person snap cue";
		assert !runtime.contains("NobaraVfxIds.HAMMER, Math.max(1, marks)") : "Hairpin Enlarge must not reuse the hammer swing cue";
		String client = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/network/JujutsuClientNetworking.java"));
		assert client.contains("VfxCuePayload.TYPE") : "Client networking must receive typed VFX cues";
		assert client.contains("VfxDirector.receive") : "Client networking must delegate visual cues to VfxDirector";
		assert !client.contains("ProjectJjkNobaraImpulsePayload") : "Client networking must not retain the legacy integer impulse switch";
	}

	private static void assertVfxCueTransportIsRegistered() throws IOException {
		String networking = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert networking.contains("VfxCuePayload.TYPE") : "VFX cue payload must be registered on the S2C channel";
		assert networking.contains("broadcastVfxCue") : "VFX core needs one radius-filtered server broadcast helper";
		assert networking.contains("sendVfxCue") : "VFX core needs one direct server send helper";
		assert networking.contains("ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)") : "VFX cue sends must remain capability-gated";
	}

	private static void assertVfxDirectorOwnsClientLifecycle() throws IOException {
		Path directorPath = CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxDirector.java");
		assert Files.exists(directorPath) : "Missing client VfxDirector";
		String director = Files.readString(directorPath);
		assert director.contains("WorldRenderEvents.AFTER_ENTITIES.register") : "VfxDirector must own the transient world render callback";
		assert director.contains("HudElementRegistry.attachElementAfter") : "VfxDirector must own the VFX HUD overlay callback";
		assert director.contains("ClientPlayConnectionEvents.DISCONNECT") : "VfxDirector must clear active scenes on disconnect";
		assert director.contains("VfxTimeline.isExpired") : "VfxDirector must discard expired late cues";
		assert director.contains("private static ClientLevel activeLevel") : "VfxDirector must track the active client world identity";
		assert director.contains("if (activeLevel != client.level)") : "VfxDirector must clear scenes when the ClientLevel object changes";
		assert Pattern.compile("if\\s*\\(activeLevel\\s*!=\\s*client\\.level\\)\\s*\\{\\s*clear\\s*\\(\\s*\\)\\s*;").matcher(director).find()
			: "VfxDirector must clear active scenes inside the ClientLevel identity-change branch";
		assert director.contains("activeLevel = client.level") : "VfxDirector must bind lifecycle state to the current ClientLevel";
		assert director.contains("activeLevel = null") : "VfxDirector must forget the world on disconnect or a null level";
		String clientEntrypoint = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/JujutsuModClient.java"));
		assert clientEntrypoint.contains("VfxDirector.initialize()") : "Client startup must initialize VFX Core before receivers";
	}

	private static void assertVfxCoreProvidesReusableChannels() throws IOException {
		String world = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxWorldChannel.java"));
		assert world.contains("triggerImpact") : "VFX world channel must expose a timed impact primitive";
		assert world.contains("renderCyanRing") && world.contains("addRibbon") && world.contains("addFlashBlade") : "VFX world channel must provide ring, ribbon, and blade primitives";
		assert world.contains("new ImpactFlash(cue") : "World primitives must retain the cue for live anchor resolution";
		assert world.contains("VfxAnchorResolver.resolve(flash.cue()") && world.contains("context.world().getEntity") : "World primitives must follow a live anchor and fall back through VfxAnchorResolver";
		assert world.contains("MAX_IMPACT_FLASHES") && world.contains("impactFlashes.remove(0)")
				: "World-channel work must stay bounded alongside director instances";
		String hud = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxHudChannel.java"));
		assert hud.contains("triggerImpact") && hud.contains("renderSmoothEdgeVignette") : "VFX HUD channel must own impact overlay primitives";
		assert hud.contains("VfxTimeline.startedAtMillis") : "HUD effects must enter the correct phase for late cues";
		String camera = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxCameraChannel.java"));
		assert camera.contains("triggerLaunch") && camera.contains("triggerHeavyImpact")
				&& camera.contains("triggerExplosion") && camera.contains("triggerRitual")
				&& camera.contains("triggerResonanceImpact")
				: "VFX camera channel must expose named cinematic profiles";
		assert camera.contains("VfxTimeline.startedAtMillis") : "Camera effects must enter the correct phase for late cues";
		assert camera.contains("MAX_CHANNEL_IMPULSES = 64") && camera.contains("addImpulse") && camera.contains("addFovImpulse")
				: "Camera and FOV work must stay bounded alongside director instances";
		String firstPerson = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxFirstPersonChannel.java"));
		assert firstPerson.contains("triggerSnap") && firstPerson.contains("DURATION_SECONDS") : "VFX first-person channel must own the snap animation";
		assert firstPerson.contains("VfxTimeline.startedAtNanos") : "First-person effects must enter the correct phase for late cues";
		String particles = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxParticleChannel.java"));
		assert particles.contains("burst") && particles.contains("ring") : "VFX particle channel must expose reusable burst and ring helpers";
		String sound = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxSoundChannel.java"));
		assert sound.contains("playNoFalloff") : "VFX sound channel must own local cinematic sound playback";
		String time = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxTimeChannel.java"));
		assert time.contains("triggerSlowMotion") && time.contains("activeScale") : "VFX time channel must own bounded client slow-motion";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/VfxDeltaTrackerMixin.java"))
				: "Global DeltaTracker mixin must not exist - it corrupts game time for all consumers";
		String mixins = Files.readString(ROOT.resolve("src/client/resources/jujutsumod.client.mixins.json"));
		assert !mixins.contains("VfxDeltaTrackerMixin") : "Client mixin config must not wire a global time-scaling mixin";
	}

	private static void assertNobaraUsesVfxCoreRecipes() throws IOException {
		Path recipesPath = CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java");
		assert Files.exists(recipesPath) : "Nobara requires VFX Core recipes";
		String recipes = Files.readString(recipesPath);
		assert recipes.contains("NobaraVfxIds.HAMMER") : "Missing Nobara hammer recipe";
		assert recipes.contains("NobaraVfxIds.RESONANCE_STRIKE") : "Missing Nobara resonance recipe";
		assert recipes.contains("NobaraVfxIds.ENLARGE") && recipes.contains("NobaraVfxIds.EXPLOSION") : "Missing Nobara finisher recipes";
		assert recipes.contains("NobaraVfxIds.FIRST_PERSON_SNAP") : "Missing Nobara first-person snap recipe";
		long openingBeatGuards = Pattern.compile("VfxTimeline\\.isOpeningBeat\\(initialAgeTicks\\)").matcher(recipes).results().count();
		assert openingBeatGuards >= 8 : "Every non-seekable Nobara sound/particle opening beat must reject late playback";
		long ageAwareChannelCalls = Pattern.compile("trigger(?:Launch|HeavyImpact|Explosion|Ritual|Swing|Impact|Snap|Blur|ResonanceImpact|SlowMotion|Nausea|BlackFlash|Flash)\\([^\\n]*initialAgeTicks\\)")
				.matcher(recipes).results().count();
		assert ageAwareChannelCalls == 44 : "All 44 Nobara realtime channel calls must receive initialAgeTicks; found " + ageAwareChannelCalls;
		assert !Files.exists(MAIN_JAVA.resolve("jujutsu/mod/network/ProjectJjkNobaraImpulsePayload.java")) : "Legacy integer VFX payload must be removed after migration";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinWorldRenderer.java")) : "Legacy Hairpin world renderer must be replaced by VFX Core";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinCinematicCamera.java")) : "Legacy Hairpin camera manager must be replaced by VFX Core";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinScreenOverlay.java")) : "Legacy Hairpin HUD manager must be replaced by VFX Core";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/ResonanceEffects.java")) : "Legacy resonance particle manager must be replaced by VFX Core";
		assert !Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/FpSnapAnimator.java")) : "Legacy first-person animator must be replaced by VFX Core";
	}

	private static void assertFirstPersonSnapPipelineWired() throws IOException {
		String mixins = Files.readString(ROOT.resolve("src/client/resources/jujutsumod.client.mixins.json"));
		assert mixins.contains("NobaraFirstPersonSnapMixin") : "First-person snap animation needs a narrow hand-render mixin";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxFirstPersonChannel.java")) : "Missing VFX Core first-person channel";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraFirstPersonSnapMixin.java")) : "Missing first-person snap hand render mixin";
		String snapMixin = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraFirstPersonSnapMixin.java"));
		assert !snapMixin.contains("ci.cancel()") : "First-person snap must not cancel vanilla hand rendering; that makes the arm disappear";
		assert snapMixin.contains("@Inject(method = \"renderHandsWithItems\", at = @At(\"HEAD\"))") : "First-person snap should apply a transform before vanilla hand rendering";
		assert snapMixin.contains("@Inject(method = \"renderHandsWithItems\", at = @At(\"RETURN\"))") : "First-person snap must restore the pose stack after vanilla hand rendering";
		assert snapMixin.contains("VfxDirector.firstPersonPose") : "First-person mixin must read the pose from VFX Core";
		String snap = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxFirstPersonChannel.java"));
		assert snap.contains("DURATION_SECONDS = 0.75f") : "Snap timing should preserve ProjectJJK's full 0..15 scaled snap phases";
		assert snap.contains("scaledProgress = progress * 15.0f") : "Snap timing must actually traverse the full 0..15 phase range";
		assert snap.contains("scaledProgress") && snap.contains("easeInQuint") && snap.contains("easeInCubic") : "Snap pose should keep ProjectJJK-style windup/hold/release phases";
	}

	private static void assertNobaraHammerHasExplosiveAndPiercingLaunchModes() throws IOException {
		String payload = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/NobaraActionPayload.java"));
		assert payload.contains("HAMMER_CONTEXT") : "Left-click hammer combat needs an explicit contextual action payload";
		String keybinds = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("keyAttack.isDown()") : "Client input must edge-detect left-click attack for explosive hammer launches";
		assert keybinds.contains("HAMMER_CONTEXT") : "Left-click hammer combat must send the contextual payload";
		String hammer = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java"));
		assert hammer.contains("launchHairpin(serverPlayer, stack, hand, false)") : "Right-click hammer use must launch non-explosive piercing nails";
		String actions = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"));
		assert actions.contains("HAMMER_CONTEXT") && actions.contains("NobaraHammerCombatRuntime.handleInput(player)") : "Nobara action gate must route left-click through the server contextual combat router";
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
		assert runtime.contains("launchHairpin(ServerPlayer player, boolean explosiveImpact)") : "Nobara runtime must expose an explosive/non-explosive launch mode";
		assert runtime.contains("isExplosiveLaunchLocked(player)") : "Hairpin Enlarge/Boom must be gated while explosive nails are in flight";
		assert runtime.contains("hasActiveExplosiveNails(player)") : "Explosive launch lock must follow actual live explosive nails, not only a fixed timer";
		String nail = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
		assert nail.contains("explosiveImpact") : "Nail entity must remember whether this launched nail should explode on impact";
		assert nail.contains("resolveNailImpact(serverLevel, this, hit, explosiveImpact)") : "Impact resolution must branch on explosive vs piercing launch mode";
		assert nail.contains("explodeAtTargetIfPassed") : "Explosive nails must detonate and disappear after reaching their target even when they miss collision";
	}

	private static void assertNobaraVfxExpansionContract() throws IOException {
		String ids = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/vfx/NobaraVfxIds.java"));
		String recipes = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
		for (String id : new String[] {"REMNANT_DROP", "RITUAL_BIND", "DOLL_STRIKE", "RESONANCE_RELEASE"}) {
			assert ids.contains(id) : "Missing Straw Doll VFX id " + id;
			assert recipes.contains("VfxDirector.register(NobaraVfxIds." + id)
					: "Missing Straw Doll VFX recipe registration " + id;
		}

		Path postProcessPath = CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxPostProcessChannel.java");
		assert Files.exists(postProcessPath) : "VfxDirector needs one internal bounded post-process channel";
		String postProcess = Files.readString(postProcessPath);
		assert postProcess.contains("processBlurEffect()") : "Post-process channel must use Minecraft 1.21.8 public blur processing";
		assert !postProcess.contains("WorldRenderEvents") : "Post-process channel must not register a parallel world callback";

		String director = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxDirector.java"));
		assert director.contains("VfxPostProcessChannel POST_PROCESS") : "Director must own the post-process channel";
		assert director.contains("POST_PROCESS.render") && director.contains("POST_PROCESS.clear()")
				: "Director must render and clear the post-process channel";
		assert director.contains("VfxTimeChannel TIME") && director.contains("TIME.clear()")
				: "Director must own and clear the client time channel";
		String context = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxContext.java"));
		assert context.contains("VfxPostProcessChannel postProcess") && context.contains("postProcess()")
				: "Recipes must reach blur only through VfxContext";
		assert context.contains("VfxTimeChannel time") && context.contains("time()")
				: "Recipes must reach slow-motion only through VfxContext";

		String camera = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxCameraChannel.java"));
		for (String profile : new String[] {"triggerLaunch", "triggerHeavyImpact", "triggerExplosion", "triggerRitual"}) {
			assert camera.contains(profile) : "Missing named camera profile " + profile;
		}
		assert camera.contains("clamp(sample(true)") && camera.contains("clamp(sample(false)")
				: "Stacked camera impulses must be clamped after sampling";
		String hud = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxHudChannel.java"));
		assert hud.contains("triggerNausea") && hud.contains("renderNausea") : "Target-local Resonance needs a bounded nausea screen overlay";
		assert !recipes.contains("ParticleTypes.SOUL_FIRE_FLAME") : "Nobara recipes must not read as blue fire";
		assert !recipes.contains("HAIRPIN_IGNITION_TICK") : "Nobara recipes must use compressed-energy particles, not ignition composition";
		long blurCalls = Pattern.compile("triggerBlur\\([^\\n]*initialAgeTicks\\)").matcher(recipes).results().count();
		assert blurCalls >= 4 : "Heavy Nobara scenes must use age-aware proximity-gated blur; found " + blurCalls;

		String ritual = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java"));
		for (String id : new String[] {"REMNANT_DROP", "RITUAL_BIND", "DOLL_STRIKE", "RESONANCE_RELEASE"}) {
			assert ritual.contains("NobaraVfxIds." + id) : "Server ritual must emit " + id;
		}
	}

	private static void assertStrawDollImpactOwnsCasterScreenFeedback() throws IOException {
		String recipes = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
		int start = recipes.indexOf("private static VfxInstance dollStrike");
		int end = recipes.indexOf("private static VfxInstance resonanceRelease", start);
		assert start >= 0 && end > start : "Straw Doll recipes must keep distinct caster and target impact phases";
		String dollStrike = recipes.substring(start, end);
		String ritual = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java"));
		assert !ritual.contains("tickRateManager") : "Resonance must not manipulate the global server tick rate";
		assert !dollStrike.contains("SoundEvents.ANVIL_USE") : "The doll strike must not sound like an anvil";
		for (String sound : new String[] {"PROJECTJJK_IMPLODE", "PROJECTJJK_DEEP_EXPLOSION", "PROJECTJJK_BLACK_FLASH_IMPACT", "PROJECTJJK_LONG_WHOOSH"}) {
			assert dollStrike.contains(sound) : "The doll strike is missing sound layer " + sound;
		}
		assert dollStrike.contains("ParticleTypes.EXPLOSION_EMITTER")
				&& dollStrike.contains("triggerExplosion") && dollStrike.contains("triggerFlash")
				: "The doll strike must stack explosions, camera impulse, and a long flash";
		assert dollStrike.contains("context.hud().triggerNausea(0.85f, initialAgeTicks)")
				: "A local Nobara caster must receive the heavy Resonance screen impact";
		String hud = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxHudChannel.java"));
		assert hud.contains("graphics.fill(0, 0, width, height, (washAlpha << 24) | 0x00120A18)")
				: "The Resonance nausea overlay must include a visible full-screen wash";
	}

	private static void assertStrawDollRitualUsesPhysicalRemnants() throws IOException {
		Path ritualPath = MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java");
		assert Files.exists(ritualPath) : "Nobara Resonance needs a dedicated server straw-doll runtime";
		String ritual = Files.readString(ritualPath);
		assert ritual.contains("RESONANCE_TARGET") : "Resonance must resolve a typed target-bound remnant";
		assert ritual.contains("NobaraActionTimeline.DOLL_STRIKE.impactTick()") : "Resonance must use the shared readable wind-up";
		assert ritual.contains("consumeResources(caster, remnant)") : "Resonance must consume one remnant and one nail only at impact";
		assert ritual.contains("ProjectJjkRitualPolicy.validate") : "Runtime validation must use the tested ritual policy";
		assert ritual.contains("ServerEntityEvents.ENTITY_UNLOAD")
				: "Partial remnant progress must clear when a living target unloads";
		assert ritual.contains("List.copyOf(PENDING_RITUALS.values())")
				&& ritual.contains("PENDING_RITUALS.remove(pending.casterId(), pending)")
				: "Pending rituals must tolerate synchronous death callbacks mutating the map during impact";
		assert !ritual.contains("sendParticles(") && !ritual.contains("playSound(") && !ritual.contains("spawnResonanceStrike")
				: "Transient Straw Doll ritual feedback must travel only through VFX Core cues";
		assert !ritual.contains("ProjectJjkNailMarks")
				&& !ritual.contains("discardOwnedEmbeddedNails")
				&& !ritual.contains("clearGlowingMark")
				: "Physical-remnant Resonance must not read or consume Hairpin marks and embedded nails";
		assert !ritual.contains("getDisplayName().getString()")
				: "Bound target names must retain their translatable Component instead of server-rendered text";
		String remnant = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkResonanceRemnant.java"));
		assert remnant.contains("Component targetName")
				&& remnant.contains("ComponentSerialization.CODEC")
				&& remnant.contains("ComponentSerialization.STREAM_CODEC")
				: "Typed remnants must persist and network-sync the localized target Component";

		String hammer = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java"));
		assert hammer.contains("ProjectJjkStrawDollRuntime.tryStart") : "Shift-hammer must start the physical remnant ritual";
		assert !hammer.contains("performResonance") : "Shift-hammer must not use the removed mark-only Resonance path";
		assert !Files.exists(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkResonanceLink.java"))
				: "The old mark-only two-step Resonance link must stay removed";

		String loadout = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraLoadout.java"));
		assert loadout.contains("JujutsuItems.STRAW_DOLL") : "Nobara's loadout must include one reusable straw doll";
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
		assert runtime.contains("onOrdinaryNailHit(level, owner, directTarget, point)")
				: "Ordinary nail hits must advance remnant acquisition";
	}

	private static void assertOriginalStrawDollAssetWired() throws IOException {
		Path geo = JUJUTSU_ASSETS.resolve("geckolib/models/straw_doll.geo.json");
		Path animations = JUJUTSU_ASSETS.resolve("geckolib/animations/straw_doll.animation.json");
		Path texture = JUJUTSU_ASSETS.resolve("textures/item/straw_doll.png");
		Path itemDefinition = JUJUTSU_ASSETS.resolve("items/straw_doll.json");
		Path itemModel = JUJUTSU_ASSETS.resolve("models/item/straw_doll.json");
		Path sourceModel = MAIN_RESOURCES.resolve("source-assets/blockbench/straw_doll.bbmodel");
		Path textureGenerator = MAIN_RESOURCES.resolve("source-assets/blockbench/generate_straw_doll_textures.ps1");
		Path previewRenderer = MAIN_RESOURCES.resolve("source-assets/blockbench/render_straw_doll_preview.py");
		for (Path required : new Path[] {geo, animations, texture, itemDefinition, itemModel, sourceModel, textureGenerator, previewRenderer}) {
			assert Files.exists(required) : "Missing original straw doll asset: " + required;
		}
		String buildScript = Files.readString(ROOT.resolve("build.gradle"));
		assert Pattern.compile("exclude\\s+[\\\"']source-assets/\\*\\*[\\\"']").matcher(buildScript).find()
				: "Editable source assets and generators must not be packaged into the runtime JAR";

		String geoJson = Files.readString(geo);
		assert geoJson.contains("geometry.jujutsumod.straw_doll") : "Straw doll geometry must use the jujutsumod namespace";
		for (String bone : new String[] {"root", "body", "head", "arm_left", "arm_right", "leg_left", "leg_right"}) {
			assert geoJson.contains("\"name\": \"" + bone + "\"") : "Straw doll is missing animated bone " + bone;
		}
		for (String line : geoJson.lines().toList()) {
			Matcher boxUv = Pattern.compile("\"size\": \\[([\\d.]+), ([\\d.]+), ([\\d.]+)](?:.*)\"uv\": \\[([\\d.]+), ([\\d.]+)]").matcher(line);
			if (boxUv.find()) {
				double sizeX = Double.parseDouble(boxUv.group(1));
				double sizeY = Double.parseDouble(boxUv.group(2));
				double sizeZ = Double.parseDouble(boxUv.group(3));
				assert sizeX >= 0.999 && sizeY >= 0.999 && sizeZ >= 0.999
						: "Straw doll Box UV cube has a sub-unit face that Blockbench may render incorrectly: " + line.trim();
				double maxU = Double.parseDouble(boxUv.group(4)) + 2.0 * (sizeX + sizeZ);
				double maxV = Double.parseDouble(boxUv.group(5)) + sizeZ + sizeY;
				assert maxU <= 64.001 && maxV <= 64.001 : "Straw doll box UV exceeds the 64x64 texture: " + line.trim();
				double minU = Double.parseDouble(boxUv.group(4));
				double minV = Double.parseDouble(boxUv.group(5));
				assert !(minU < 64.0 && maxU > 48.0 && minV < 32.0 && maxV > 16.0)
						: "Idle straw doll cube overlaps the effects-only cyan atlas region: " + line.trim();
			}
		}

		String animationJson = Files.readString(animations);
		for (String animation : new String[] {"idle", "ritual_raise", "impact", "release"}) {
			assert animationJson.contains("animation.straw_doll." + animation) : "Missing straw doll animation " + animation;
		}
		BufferedImage dollTexture = ImageIO.read(texture.toFile());
		assert dollTexture != null && dollTexture.getWidth() == 64 && dollTexture.getHeight() == 64
				: "Straw doll texture must be a readable 64x64 PNG";
		assert Files.readString(itemDefinition).contains("\"type\": \"geckolib:geckolib\"")
				: "Minecraft 1.21.8 item definition must delegate to GeckoLib 5 special rendering";
		assert Files.readString(itemModel).contains("\"parent\": \"builtin/entity\"")
				: "Straw doll display model must use builtin/entity";
		String sourceModelJson = Files.readString(sourceModel);
		assert Pattern.compile("\"model_identifier\"\\s*:\\s*\"geometry\\.jujutsumod\\.straw_doll\"").matcher(sourceModelJson).find()
				: "Blockbench source must retain the exported geometry identity";
		assert Pattern.compile("\"relative_path\"\\s*:\\s*\"\\.\\./\\.\\./assets/jujutsumod/textures/item/straw_doll\\.png\"").matcher(sourceModelJson).find()
				: "Blockbench source must resolve the portable runtime texture when opened directly";
		assert !sourceModelJson.contains("\"animators\":{}")
				: "Blockbench source must retain editable keyframes for every Straw Doll animation";
		assert sourceModelJson.split("\\\"type\\\":\\\"cube\\\"", -1).length - 1 >= 25
				: "Blockbench source must retain every detailed runtime cube, not a reduced blockout";
		assert sourceModelJson.split("\\\"type\\\":\\\"bone\\\"", -1).length - 1 >= 14
				: "Blockbench source must retain all runtime animation bone tracks";
		String previewSource = Files.readString(previewRenderer);
		assert previewSource.contains("Image.open(TEXTURE)") && previewSource.contains("texture_color")
				: "Headless asset previews must sample the actual runtime texture instead of heuristic colors";

		for (String forbiddenCopy : new String[] {
				"geo/projectjjk/doll.geo.json",
				"animations/projectjjk/doll.animation.json",
				"textures/projectjjk/entity/doll.png"
		}) {
			assert !Files.exists(JUJUTSU_ASSETS.resolve(forbiddenCopy))
					: "ProjectJJK research doll asset must not be packaged in the runtime namespace: " + forbiddenCopy;
		}

		Path itemClass = MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollItem.java");
		Path renderer = CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/doll/ProjectJjkStrawDollRenderer.java");
		Path model = CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/doll/ProjectJjkStrawDollModel.java");
		String itemSource = Files.readString(itemClass);
		assert itemSource.contains("implements GeoItem") && itemSource.contains("GeoItem.registerSyncedAnimatable(this)")
				: "Straw doll item must use the GeckoLib 5 synced item contract";
		assert Files.exists(renderer) && Files.exists(model) : "Missing GeckoLib straw doll client renderer/model";

		for (Path originalAsset : new Path[] {geo, animations, sourceModel, itemClass, renderer, model}) {
			String source = Files.readString(originalAsset).toLowerCase();
			assert !source.contains("assets/projectjjk") && !source.contains(".reference/projectjjk")
					: "Original straw doll runtime/source must not reference ProjectJJK assets: " + originalAsset;
		}
	}

	private static void assertNobaraNailAuraAvoidsSoulFire() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
		assert !runtime.contains("ParticleTypes.SOUL_FIRE_FLAME") : "Nobara nail aura must not use vanilla soul-fire particles";
		assert !runtime.contains("HAIRPIN_IGNITION_TICK") && runtime.contains("spawnPreparedNailPressure")
				: "Prepared and flying nails must use compressed-energy motes instead of ignition particles";
		int impactCue = runtime.indexOf("emitImpactCue(level, point, owner);");
		int ordinaryImpactBranch = runtime.indexOf("if (!explosiveImpact)");
		assert impactCue >= 0 && impactCue < ordinaryImpactBranch
				: "Every ordinary or explosive nail impact must emit the named impact camera/HUD cue before branching";
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/ProjectJjkNailRenderer.java"));
		assert renderer.contains("renderCompressedEnergyAura") && renderer.contains("renderPressureBand")
				: "Real nail entities must render a narrow rim, pressure bands, and orbiting slivers";
		assert renderer.contains("renderEmbeddedMarkPulse")
				: "Embedded nails must retain a faint state-driven mark pulse after the flight aura ends";
		assert renderer.contains("VfxPalette") : "Persistent nail aura must reuse the VFX Core cursed-energy palette";
		assert !renderer.contains("renderBlueForceFieldEnvelope") : "The rejected broad nail envelope must stay removed";
		assert !renderer.contains("renderCyanNailFireAura") : "Blue nail aura must not use the rejected cyan flame ribbon geometry";
		assert !renderer.contains("ParticleTypes.SOUL_FIRE_FLAME") : "Blue nail aura must be rendered geometry, not vanilla particles";
	}

	private static void assertHairpinScreenOverlayUsesSmoothGradientVignette() throws IOException {
		String overlay = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/vfx/VfxHudChannel.java"));
		assert overlay.contains("renderSmoothEdgeVignette") : "Impact screen darkness must use the smooth vignette path";
		assert overlay.contains("layers = 28") : "Impact screen darkness needs enough layers to avoid visible hard bands";
		assert !overlay.contains("renderEdgeTears") : "Impact screen darkness must not draw strip-like edge tears";
		assert !overlay.contains("index < 5") : "Impact screen darkness must not draw obvious horizontal sweep stripes";
	}

	private static void assertCharacterSelectUsesCheapUiPrimitives() throws IOException {
		String uiRender = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/UiRender.java"));
		assert !uiRender.contains("for (int row = 0; row < h; row++)") : "Large rounded UI panels must not submit one fill per pixel row";
		assert !uiRender.contains("cornerInset(") : "Rounded rects should use cheap block primitives instead of per-row corner scans";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/neon/render/SdfRenderer.java"))
				: "Neon dashboard must use the SDF shader renderer for GPU-batched surfaces";
		String sdfRenderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/neon/render/SdfRenderer.java"));
		assert sdfRenderer.contains("drawIndexed") : "SDF renderer must batch all shapes into one draw call";
	}

	private static void assertGeckoLibNobaraPlayerModelWired() throws IOException {
		String gradle = Files.readString(ROOT.resolve("build.gradle"));
		assert gradle.contains("software.bernie.geckolib") : "GeckoLib must be a declared dependency for the Nobara player geo model";
		String properties = Files.readString(ROOT.resolve("gradle.properties"));
		assert properties.contains("geckolib_version=5.2.2") : "GeckoLib version must match the installed 1.21.8 runtime jar";
		String modJson = Files.readString(ROOT.resolve("src/main/resources/fabric.mod.json"));
		assert modJson.contains("\"geckolib\"") : "fabric.mod.json must declare the required GeckoLib runtime dependency";
		String mixins = Files.readString(ROOT.resolve("src/client/resources/jujutsumod.client.mixins.json"));
		assert mixins.contains("NobaraPlayerRendererMixin") : "Nobara player geo render must hook the vanilla player renderer";
		assert mixins.contains("NobaraLivingEntityRendererMixin") : "Nobara player geo render must hook the declared LivingEntityRenderer render method";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java")) : "Missing Nobara GeckoLib animatable";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoModel.java")) : "Missing Nobara GeckoLib model";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoRenderer.java")) : "Missing Nobara GeckoLib renderer";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraLivingEntityRendererMixin.java")) : "Missing LivingEntityRenderer hook for Nobara player geo render";
		assert Files.exists(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json")) : "Missing GeckoLib 5 Nobara model asset";
		assert Files.exists(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/animations/projectjjk/npc.animation.json")) : "Missing GeckoLib 5 Nobara animation asset";
		assert Files.exists(MAIN_RESOURCES.resolve("assets/jujutsumod/textures/projectjjk/entity/npcs/nobara_kugisaki.png")) : "Missing Nobara NPC texture";
		String geoModel = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoModel.java"));
		assert geoModel.contains("projectjjk/nobara_kugisaki") : "GeckoLib model key should be stripped to projectjjk/nobara_kugisaki";
		assert geoModel.contains("projectjjk/npc") : "GeckoLib animation key should be stripped to projectjjk/npc";
		assert !geoModel.contains("geo/projectjjk") : "GeckoLib 5 does not bake models from the old geo/ path";
		assert !geoModel.contains("animations/projectjjk") : "GeckoLib 5 does not bake animations from the old animations/ path";
		String manager = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/character/ClientCharacterSelectionManager.java"));
		assert manager.contains("rememberEntity") && manager.contains("selectionByEntityId") : "Renderer needs entity-id lookup while keeping GUI portrait skin logic separate";
		assert manager.contains("RenderContext") : "Nobara Gecko renderer needs the extracted player entity and partial tick, not just a UUID";
		assert manager.contains("WeakReference<AbstractClientPlayer>") : "Client render context must not strongly retain old player entities across worlds";
		String playerMixin = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraPlayerRendererMixin.java"));
		assert playerMixin.contains("rememberEntity(player, partialTick)") : "Player render extraction must remember the actual player entity for GeckoLib replaced-entity rendering";
		String livingMixin = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraLivingEntityRendererMixin.java"));
		assert livingMixin.contains("renderContextByEntityId(playerState.id)") : "Nobara geo render must resolve the player entity before rendering";
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoRenderer.java"));
		assert renderer.contains("fillRenderState(getAnimatable(), player") : "Nobara geo render must fill GeckoLib render data before rendering";
		assert renderer.contains("DataTickets.PACKED_LIGHT") : "Nobara geo render must provide GeckoLib packed light data outside the dispatcher path";
		assert !renderer.contains("render(cast(state), matrices") : "Nobara geo render must not render a raw vanilla PlayerRenderState without GeckoLib data";
		assert !renderer.contains("catch (IllegalArgumentException") : "Nobara geo render must not silently fall back to the old player skin when GeckoLib data is missing";
		String animatable = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java"));
		assert animatable.contains("state.isMoving()") : "Nobara idle/walk/run must use GeckoLib movement data";
		assert animatable.contains("DataTickets.SPRINTING") && animatable.contains("DataTickets.VELOCITY") : "Nobara run animation must use real player movement tickets";
		assert !animatable.contains("speedValue >") : "HumanoidRenderState.speedValue is a vanilla limb scale, not a movement trigger";
		String geo = Files.readString(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json"));
		assert geo.contains("\"name\": \"bb_main\",\n\t\t\t\t\t\"parent\": \"skirt\"") : "Nobara skirt/coat panels must follow the body instead of floating as a root bone";
		String card = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/gui/neon/pages/CharacterPage.java"));
		assert card.contains("textures/entity/character/nobara.png") : "Character select portrait must keep using the player-skin head, not the GeckoLib NPC texture";
	}

	private static void assertNobaraHeldItemsAndArmPosesWired() throws IOException {
		Path heldItemLayer = CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraHeldItemLayer.java");
		assert Files.exists(heldItemLayer) : "Nobara replacement renderer must restore held-item rendering";
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoRenderer.java"));
		assert renderer.contains("addRenderLayer(new NobaraHeldItemLayer<>(this))")
				: "Nobara renderer must attach the held-item layer";
		assert renderer.contains("vanillaPoseModel.setupAnim(renderState)") && renderer.contains("DataTickets.HUMANOID_MODEL")
				: "Nobara renderer must derive arm poses from the current vanilla player render state";
		String geoModel = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoModel.java"));
		assert geoModel.contains("applyVanillaArmPose") && geoModel.contains("DataTickets.HUMANOID_MODEL")
				: "Nobara model must apply vanilla-equivalent held-item arm rotations";
		String geo = Files.readString(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json"));
		assert Pattern.compile("\"name\"\\s*:\\s*\"rightHandItem\"\\s*,\\s*\"parent\"\\s*:\\s*\"right_elbow\"").matcher(geo).find()
				: "Nobara model needs a right-hand item attachment under the right elbow";
		assert Pattern.compile("\"name\"\\s*:\\s*\"leftHandItem\"\\s*,\\s*\"parent\"\\s*:\\s*\"left_elbow\"").matcher(geo).find()
				: "Nobara model needs a left-hand item attachment under the left elbow";
		String itemRegistry = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/registry/JujutsuItems.java"));
		assert itemRegistry.contains("ItemLore") && itemRegistry.contains("tooltip.jujutsumod.straw_doll.ritual")
				: "Straw Doll tooltip must use the current item-lore API to explain how to start Resonance";
		for (String language : new String[] {"en_us.json", "ru_ru.json"}) {
			String lang = Files.readString(JUJUTSU_ASSETS.resolve("lang").resolve(language));
			assert lang.contains("tooltip.jujutsumod.straw_doll.ritual") && lang.contains("tooltip.jujutsumod.straw_doll.requires")
					: "Missing Straw Doll ritual tooltip translations in " + language;
		}
	}

	private static void assertNobaraGeoHeadLookIsSafeAndEnabled() throws IOException {
		String geoModel = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoModel.java"));
		assert geoModel.contains("setCustomAnimations") : "Nobara Gecko model must apply a safe per-frame head look pass";
		assert geoModel.contains("getBone(HEAD_BONE)") : "Nobara head look must rotate the separate head bone only";
		assert geoModel.contains("NobaraPlayerGeoAnimatable.headLookWeight(animationState, playerState)") : "Nobara head look must use the animatable/controller-aware action weight";
		assert geoModel.contains("MAX_HEAD_YAW_DEGREES = 38.0f") : "Nobara head yaw clamp must stay conservative after the unsafe 75 degree attempt";
		assert geoModel.contains("MAX_HEAD_PITCH_DEGREES = 22.0f") : "Nobara head pitch clamp must stay conservative after the unsafe 45 degree attempt";
		assert geoModel.contains("head.resetStateChanges()") : "Nobara render-only head look must not leak rotationChanged into GeckoLib's next-frame reset bookkeeping";
		assert !geoModel.contains("MAX_HEAD_YAW_DEGREES = 75.0f") : "Do not restore the old unsafe head yaw range";
		assert !geoModel.contains("MAX_HEAD_PITCH_DEGREES = 45.0f") : "Do not restore the old unsafe head pitch range";
		String animatable = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java"));
		assert animatable.contains("headKeyframedActionIsPlaying(state)") : "Nobara head look must attenuate while ProjectJJK head-keyframed action clips are active";
		assert animatable.contains("getTriggeredAnimation()") && animatable.contains("getCurrentRawAnimation()") : "Nobara head look must account for GeckoLib triggered and current raw action animations";
		assert animatable.contains("animation == SNAP") && animatable.contains("animation == SPELL_5") && animatable.contains("animation == SWIPE_1") : "Nobara head look action guard must include snap, spell, and swipe ProjectJJK clips";
		String geo = Files.readString(MAIN_RESOURCES.resolve("assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json"));
		assert Pattern.compile("\"name\"\\s*:\\s*\"head\"\\s*,\\s*\"parent\"\\s*:\\s*\"body\"").matcher(geo).find() : "Nobara model must keep a separate head bone parented to body for look tracking";
	}

	private static void assertNobaraGeoRenderRestoresPoseStack() throws IOException {
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/nobara/NobaraPlayerGeoRenderer.java"));
		assert renderer.contains("restorePoseStack") : "Nobara GeckoLib replacement render must restore PoseStack depth after rendering";
		assert renderer.contains("matrices.pushPose()") : "Nobara GeckoLib replacement render needs a local guard pose";
		assert renderer.contains("finally") : "PoseStack restoration must run even when GeckoLib render exits unusually";
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
