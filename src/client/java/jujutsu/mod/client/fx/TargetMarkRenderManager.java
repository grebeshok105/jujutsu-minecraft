package jujutsu.mod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jujutsu.mod.network.ProjectJjkTargetMarkPayload;

public final class TargetMarkRenderManager {
	private static final Map<Integer, TargetMark> MARKS = new ConcurrentHashMap<>();

	private TargetMarkRenderManager() {}

	public static void apply(ProjectJjkTargetMarkPayload payload, long gameTime) {
		if (payload.marks() <= 0 || payload.expiresGameTime() <= gameTime) {
			MARKS.remove(payload.targetEntityId());
			return;
		}
		MARKS.put(payload.targetEntityId(), new TargetMark(payload.targetEntityId(), payload.marks(), gameTime, payload.expiresGameTime()));
	}

	public static List<TargetMark> active(long gameTime) {
		List<TargetMark> active = new ArrayList<>();
		Iterator<Map.Entry<Integer, TargetMark>> iterator = MARKS.entrySet().iterator();
		while (iterator.hasNext()) {
			TargetMark mark = iterator.next().getValue();
			if (mark.expiresGameTime() <= gameTime) {
				iterator.remove();
				continue;
			}
			active.add(mark);
		}
		return active;
	}

	public static void remove(int targetEntityId) {
		MARKS.remove(targetEntityId);
	}

	public static void clear() {
		MARKS.clear();
	}

	public record TargetMark(int targetEntityId, int marks, long startedGameTime, long expiresGameTime) {
		public float fade(long gameTime, float partialTick) {
			float remaining = Math.max(0.0f, expiresGameTime - gameTime - partialTick);
			return Math.min(1.0f, remaining / 12.0f);
		}

		public float age(long gameTime, float partialTick) {
			return Math.max(0.0f, gameTime - startedGameTime + partialTick);
		}
	}
}
