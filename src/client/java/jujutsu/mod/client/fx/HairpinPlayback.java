package jujutsu.mod.client.fx;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.fx.HairpinTimeline;
import jujutsu.mod.network.HairpinFxPayload;

public final class HairpinPlayback {
	private final HairpinFxPayload payload;
	private final long startedAtMillis;

	public HairpinPlayback(HairpinFxPayload payload, long startedAtMillis) {
		this.payload = payload;
		this.startedAtMillis = startedAtMillis;
	}

	public long elapsedMillis(long currentTimeMillis) {
		return Math.max(0L, currentTimeMillis - startedAtMillis);
	}

	public HairpinTimeline.Phase phase(long currentTimeMillis) {
		return HairpinTimeline.phaseAt(elapsedMillis(currentTimeMillis));
	}

	public float progressInPhase(long currentTimeMillis) {
		return HairpinTimeline.progressInPhase(elapsedMillis(currentTimeMillis));
	}

	public boolean isDone(long currentTimeMillis) {
		return phase(currentTimeMillis) == HairpinTimeline.Phase.DONE;
	}

	public int seed() {
		return payload.seed();
	}

	public Vec3 target() {
		return new Vec3(payload.targetX(), payload.targetY(), payload.targetZ());
	}

	public List<Vec3> nails() {
		return List.of(
				new Vec3(payload.nail0X(), payload.nail0Y(), payload.nail0Z()),
				new Vec3(payload.nail1X(), payload.nail1Y(), payload.nail1Z()),
				new Vec3(payload.nail2X(), payload.nail2Y(), payload.nail2Z()),
				new Vec3(payload.nail3X(), payload.nail3Y(), payload.nail3Z())
		);
	}
}
