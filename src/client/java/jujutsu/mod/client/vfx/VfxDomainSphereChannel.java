package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.vfx.domain.DomainSphereRenderer;
import jujutsu.mod.client.vfx.domain.DomainSphereTiming;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/**
 * Active-sphere bookkeeping for the world-space domain-sphere post-effect.
 *
 * <p>The channel owns state only: it registers no events and allocates no GL objects. Wiring lives in
 * {@link VfxDirector}, the GPU work in {@link DomainSphereRenderer}, whose constructor is GPU-free —
 * that split is what lets JUnit construct this channel headless.
 */
public final class VfxDomainSphereChannel implements AutoCloseable {
	private static final int MAX_ACTIVE_SPHERES = 4;
	private final List<ActiveSphere> activeSpheres = new ArrayList<>();
	private final DomainSphereRenderer renderer = new DomainSphereRenderer();
	private boolean disabledForSession;

	public void triggerSphere(VfxCue cue, DomainSphereTiming timing) {
		if (disabledForSession) {
			return;
		}
		activeSpheres.add(new ActiveSphere(cue, timing));
		if (activeSpheres.size() > MAX_ACTIVE_SPHERES) {
			activeSpheres.remove(0);
		}
	}

	void render(WorldRenderContext context) {
		if (disabledForSession || activeSpheres.isEmpty()) {
			return;
		}
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = context.world().getGameTime();
		List<DomainSphereRenderer.Sphere> spheres = new ArrayList<>(activeSpheres.size());
		for (Iterator<ActiveSphere> iterator = activeSpheres.iterator(); iterator.hasNext();) {
			ActiveSphere active = iterator.next();
			float age = VfxTimeline.ageTicks(active.cue(), gameTime, partialTick);
			if (active.timing().isExpired(age)) {
				iterator.remove();
				continue;
			}
			spheres.add(new DomainSphereRenderer.Sphere(
					active.cue().origin(),
					(float) active.timing().radiusAt(age),
					active.timing().fadeAt(age),
					active.timing().expansionProgress(age),
					active.timing().ageProgress(age)));
		}
		if (spheres.isEmpty()) {
			return;
		}
		try {
			renderer.render(Minecraft.getInstance(), context, spheres);
		} catch (RuntimeException | LinkageError error) {
			disabledForSession = true;
			clear();
			JujutsuMod.LOGGER.warn("Disabling domain sphere VFX for this client session: {}", error.toString());
		}
	}

	void clear() {
		activeSpheres.clear();
	}

	void resetSession() {
		clear();
		disabledForSession = false;
	}

	/** Test hook: spheres retained right now. Expiry is applied by {@link #render(WorldRenderContext)}, not here. */
	int activeCount() {
		return activeSpheres.size();
	}

	@Override
	public void close() {
		clear();
		renderer.close();
	}

	private record ActiveSphere(VfxCue cue, DomainSphereTiming timing) {}
}
