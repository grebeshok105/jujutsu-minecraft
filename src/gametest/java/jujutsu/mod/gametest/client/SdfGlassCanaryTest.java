package jujutsu.mod.gametest.client;

import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import jujutsu.mod.client.ui.neon.render.SdfPipelines;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;

/**
 * Glass-pipeline canary: compiles and draws the SDF glass shader in a live render context —
 * one legacy shape plus one glass shape (negative highlight) through a real
 * {@link SdfRenderer#flush()} on the render thread. A GLSL compile error, an unbound sampler,
 * or an illegal SceneSampler copy surfaces as a GL error / exception on the render thread and
 * fails the test. A screenshot with both shapes on screen is the evidence artifact.
 *
 * <p>This closes the validation gap the entrypoint crash exposed: {@code qualityGate} compiles
 * Java but never boots a client, so GLSL only proves itself here.
 */
public final class SdfGlassCanaryTest implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		// Draw both paths through one real flush on the render thread. The first flush also
		// triggers lazy shader compilation of SDF_GLASS; a compile failure throws here.
		context.runOnClient(client -> {
			SdfRenderer renderer = new SdfRenderer();
			renderer.begin();
			renderer.add(SdfShape.builder()
					.rect(40f, 40f, 56f, 64f)
					.radius(8f)
					.border(1f, 0x73FFFFFF)
					.glow(6f, 0x38E48A36)
					.highlight(0.5f)
					.fill(0x73263B52, 0x5C1B2C42)
					.build());
			renderer.add(SdfShape.builder()
					.rect(120f, 40f, 56f, 64f)
					.radius(8f)
					.border(1f, 0x73FFFFFF)
					.glow(10f, 0x55BFD8FF)
					.highlight(-0.5f)
					.fill(0x73263B52, 0x5C1B2C42)
					.build());
			renderer.flush();
			renderer.close();
		});

		// Let the driver surface async pipeline/GL errors across a few rendered frames,
		// then capture evidence with both shapes on screen.
		context.waitTicks(3);
		Path screenshot = context.takeScreenshot("sdf_glass_canary");
		if (!Files.exists(screenshot)) {
			throw new AssertionError("glass canary screenshot missing: " + screenshot);
		}
	}
}
