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
	private static final Path CLIENT_JAVA = ROOT.resolve("src/client/java");
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
		assertExplicitNobaraActionsAreVisible();
		assertProjectJjkHairpinFinisherNumbers();
		assertDefaultNobaraEntrypointSkipsLegacyRuntime();
		assertNobaraNailsEmbedLikeOpaqueBodyAnchors();
		assertNobaraTargetMarksUseVanillaGlowing();
		assertHairpinFinishersUseSnapImpulse();
		assertFirstPersonSnapPipelineWired();
		assertNobaraHammerHasExplosiveAndPiercingLaunchModes();
		assertNobaraNailAuraAvoidsSoulFire();
		assertHairpinScreenOverlayUsesSmoothGradientVignette();
		assertCharacterSelectUsesCheapUiPrimitives();
		assertGeckoLibNobaraPlayerModelWired();
		assertNobaraGeoHeadLookIsSafeAndEnabled();
		assertNobaraGeoRenderRestoresPoseStack();
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

	private static void assertExplicitNobaraActionsAreVisible() throws IOException {
		Path payload = MAIN_JAVA.resolve("jujutsu/mod/network/NobaraActionPayload.java");
		assert Files.exists(payload) : "Nobara Enlarge/Explosion must have an explicit client->server action payload";
		String networking = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/JujutsuNetworking.java"));
		assert networking.contains("NobaraActionPayload.TYPE") : "Nobara action payload must be registered server-side";
		String keybinds = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("key.jujutsumod.nobara_hairpin_enlarge") : "Hairpin Enlarge must be a visible keybind";
		assert keybinds.contains("key.jujutsumod.nobara_hairpin_explosion") : "Hairpin Explosion must be a visible keybind";
		String screen = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/gui/CharacterSelectScreen.java"));
		assert screen.contains("ability.hairpin_enlarge") : "Character select must show Hairpin Enlarge in the kit preview";
		assert screen.contains("ability.hairpin_explosion") : "Character select must show Hairpin Explosion in the kit preview";
		String commands = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/command/JujutsuCommands.java"));
		assert commands.contains("\"enlarge\"") && commands.contains("\"explosion\"") : "Hairpin Enlarge/Explosion must have test commands";
		assert commands.contains("ProjectJjkNobaraActions.tryCast") : "Hairpin commands must use the shared Nobara selection gate";
		String actionRuntime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"));
		assert actionRuntime.contains("CharacterSelectionManager.selected(player) != JujutsuCharacter.NOBARA") : "Nobara actions must reject non-Nobara players";
		assert actionRuntime.contains("tryEnlargeMarkedTarget(player)") : "Hairpin Enlarge action must call the runtime cast";
		assert actionRuntime.contains("detonateMarks(player)") : "Hairpin Explosion action must call the runtime cast";
		String hammer = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java"));
		assert !hammer.contains("tryEnlargeMarkedTarget") : "Hammer must not hide Hairpin Enlarge as a fallback action";
		assert !hammer.contains("detonateMarks") : "Hammer must not hide Hairpin Explosion as a fallback action";
		assert Files.exists(JUJUTSU_ASSETS.resolve("textures/gui/abilities/hairpin_enlargement.png")) : "Missing Hairpin Enlarge UI icon";
		assert Files.exists(JUJUTSU_ASSETS.resolve("textures/gui/abilities/hairpin_explosion.png")) : "Missing Hairpin Explosion UI icon";
	}

	private static void assertProjectJjkHairpinFinisherNumbers() throws IOException {
		String profile = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java"));
		assert profile.contains("HAIRPIN_ENLARGE_RANGE = 20.0") : "Hairpin Enlarge range should match ProjectJJK player ability registry";
		assert profile.contains("HAIRPIN_ENLARGE_DAMAGE = 12.0f") : "Hairpin Enlarge damage should match ProjectJJK player ability registry";
		assert profile.contains("DETONATE_DAMAGE_BASE = 1.0f") : "Hairpin Explosion damage should match ProjectJJK player ability registry";
		assert profile.contains("DETONATE_DAMAGE_PER_MARK = 0.0f") : "Hairpin Explosion must not scale from old jujutsumod mark damage";
	}

	private static void assertNobaraNailsEmbedLikeOpaqueBodyAnchors() throws IOException {
		String nailEntity = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
		assert nailEntity.contains("ProjectJjkNailEmbedding.bodyEmbedPoint") : "Embedded nails must use body-space attachment math, not AABB clamp-only placement";
		assert !nailEntity.contains("setOldPosAndRot(next") : "Embedded nails must not reset old position each tick; that creates visible chase/teleporting";
		assert nailEntity.contains("embeddedLocalOffset()") : "Embedded nail renderer needs synced local body offset for render-attaching to the host";
		assert nailEntity.contains("level().isClientSide() ? entityData.get(DATA_EMBEDDED_TARGET_ID) : embeddedTargetId") : "Client embedded nail rendering must read the synced target id, not the stale server-only field";
		assert nailEntity.contains("target.yBodyRot") : "Embedded nails must anchor to body rotation, not head/look yaw";
		String nailRenderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/render/ProjectJjkNailRenderer.java"));
		assert !nailRenderer.contains("renderEmbeddedMark") : "Embedded nail renderer must not draw translucent lightning ribbons on the nail mesh";
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
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinWorldRenderer.java"));
		assert !renderer.contains("renderTargetMarks") : "Target marks must not be custom world geometry when vanilla Glowing is active";
		assert !renderer.contains("renderBodyGlowShell") : "Target marks must not be the old free-floating body shell";
		assert !renderer.contains("TargetMarkRenderManager") : "Target mark world-render manager should not drive the visual mark";
	}

	private static void assertHairpinFinishersUseSnapImpulse() throws IOException {
		String payload = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/ProjectJjkNobaraImpulsePayload.java"));
		assert payload.contains("FP_SNAP") : "Hairpin Enlarge/Explosion need an explicit first-person snap impulse";
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
		assert runtime.contains("ProjectJjkNobaraImpulsePayload.FP_SNAP") : "Hairpin finishers must request snap animation on the caster";
		assert !runtime.contains("ProjectJjkNobaraImpulsePayload.HAMMER, Math.max(1, marks)") : "Hairpin Enlarge must not reuse the hammer/anvil impulse";
		String client = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/network/JujutsuClientNetworking.java"));
		assert client.contains("ProjectJjkNobaraImpulsePayload.FP_SNAP") : "Client networking must handle the first-person snap impulse";
		assert client.contains("FpSnapAnimator.playSnap") : "Snap impulse must start the first-person hand animation";
	}

	private static void assertFirstPersonSnapPipelineWired() throws IOException {
		String mixins = Files.readString(ROOT.resolve("src/client/resources/jujutsumod.client.mixins.json"));
		assert mixins.contains("NobaraFirstPersonSnapMixin") : "First-person snap animation needs a narrow hand-render mixin";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/FpSnapAnimator.java")) : "Missing first-person snap animator";
		assert Files.exists(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraFirstPersonSnapMixin.java")) : "Missing first-person snap hand render mixin";
		String snapMixin = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/mixin/NobaraFirstPersonSnapMixin.java"));
		assert !snapMixin.contains("ci.cancel()") : "First-person snap must not cancel vanilla hand rendering; that makes the arm disappear";
		assert snapMixin.contains("@Inject(method = \"renderHandsWithItems\", at = @At(\"HEAD\"))") : "First-person snap should apply a transform before vanilla hand rendering";
		assert snapMixin.contains("@Inject(method = \"renderHandsWithItems\", at = @At(\"RETURN\"))") : "First-person snap must restore the pose stack after vanilla hand rendering";
		String snap = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/FpSnapAnimator.java"));
		assert snap.contains("DURATION_SECONDS = 0.75f") : "Snap timing should preserve ProjectJJK's full 0..15 scaled snap phases";
		assert snap.contains("scaledProgress") && snap.contains("easeInQuint") && snap.contains("easeInCubic") : "Snap pose should keep ProjectJJK-style windup/hold/release phases";
	}

	private static void assertNobaraHammerHasExplosiveAndPiercingLaunchModes() throws IOException {
		String payload = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/network/NobaraActionPayload.java"));
		assert payload.contains("NAIL_LAUNCH_EXPLOSIVE") : "Left-click hammer launch needs an explicit explosive nail-launch payload";
		String keybinds = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("keyAttack.isDown()") : "Client input must edge-detect left-click attack for explosive hammer launches";
		assert keybinds.contains("NAIL_LAUNCH_EXPLOSIVE") : "Left-click hammer launch must send the explosive payload";
		String hammer = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java"));
		assert hammer.contains("launchHairpin(serverPlayer, stack, hand, false)") : "Right-click hammer use must launch non-explosive piercing nails";
		String actions = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraActions.java"));
		assert actions.contains("NAIL_LAUNCH_EXPLOSIVE") && actions.contains("launchHairpin(player, true)") : "Nobara action gate must route left-click to explosive launch mode";
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
		assert runtime.contains("launchHairpin(ServerPlayer player, boolean explosiveImpact)") : "Nobara runtime must expose an explosive/non-explosive launch mode";
		assert runtime.contains("isExplosiveLaunchLocked(player)") : "Hairpin Enlarge/Boom must be gated while explosive nails are in flight";
		assert runtime.contains("hasActiveExplosiveNails(player)") : "Explosive launch lock must follow actual live explosive nails, not only a fixed timer";
		String nail = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java"));
		assert nail.contains("explosiveImpact") : "Nail entity must remember whether this launched nail should explode on impact";
		assert nail.contains("resolveNailImpact(serverLevel, this, hit, explosiveImpact)") : "Impact resolution must branch on explosive vs piercing launch mode";
		assert nail.contains("explodeAtTargetIfPassed") : "Explosive nails must detonate and disappear after reaching their target even when they miss collision";
	}

	private static void assertNobaraNailAuraAvoidsSoulFire() throws IOException {
		String runtime = Files.readString(MAIN_JAVA.resolve("jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"));
		assert !runtime.contains("ParticleTypes.SOUL_FIRE_FLAME") : "Nobara nail aura must not use vanilla soul-fire particles";
		String renderer = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinWorldRenderer.java"));
		assert renderer.contains("renderBlueForceFieldEnvelope") : "Prepared and flying nails must use the previous blue force-field envelope";
		assert !renderer.contains("renderCyanNailFireAura") : "Blue nail aura must not use the rejected cyan flame ribbon geometry";
		assert !renderer.contains("ParticleTypes.SOUL_FIRE_FLAME") : "Blue nail aura must be rendered geometry, not vanilla particles";
	}

	private static void assertHairpinScreenOverlayUsesSmoothGradientVignette() throws IOException {
		String overlay = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/fx/HairpinScreenOverlay.java"));
		assert overlay.contains("renderSmoothEdgeVignette") : "Impact screen darkness must use the smooth vignette path";
		assert overlay.contains("layers = 28") : "Impact screen darkness needs enough layers to avoid visible hard bands";
		assert !overlay.contains("renderEdgeTears") : "Impact screen darkness must not draw strip-like edge tears";
		assert !overlay.contains("index < 5") : "Impact screen darkness must not draw obvious horizontal sweep stripes";
	}

	private static void assertCharacterSelectUsesCheapUiPrimitives() throws IOException {
		String uiRender = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/UiRender.java"));
		assert !uiRender.contains("for (int row = 0; row < h; row++)") : "Large rounded UI panels must not submit one fill per pixel row";
		assert !uiRender.contains("cornerInset(") : "Rounded rects should use cheap block primitives instead of per-row corner scans";
		String uiButton = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/UiButton.java"));
		assert !uiButton.contains("for (int row = 0; row < h; row++)") : "Character select buttons must not paint gradients one pixel row at a time";
		String card = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/CharacterCard.java"));
		assert !card.contains("y - lift") : "Character cards must not animate through rounded integer Y jumps";
		assert !card.contains("headX - 5") : "Nobara portrait must not draw the side background around the skin head";
		assert !card.contains("coatY") : "Nobara portrait must not draw the old orange body/coat stub below the head";
		assert !card.contains("nailX") : "Nobara portrait must not draw the old right-side nail that reads like a T";
		assert !card.contains("y + h - 12") : "Character cards must not draw the dark strip under the technique label";
		assert card.contains("drawPortraitBackdrop") : "Nobara portrait should reuse the cleaner Default-style portrait backdrop";
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
		String card = Files.readString(CLIENT_JAVA.resolve("jujutsu/mod/client/ui/CharacterCard.java"));
		assert card.contains("textures/entity/character/nobara.png") : "Character select portrait must keep using the player-skin head, not the GeckoLib NPC texture";
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
