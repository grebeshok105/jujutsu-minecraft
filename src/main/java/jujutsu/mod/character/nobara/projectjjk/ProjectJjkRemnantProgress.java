package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProjectJjkRemnantProgress {
	private final int hitThreshold;
	private final Map<HitKey, Integer> hits = new HashMap<>();

	public ProjectJjkRemnantProgress(int hitThreshold) {
		this.hitThreshold = hitThreshold;
	}

	public boolean recordHit(UUID casterId, UUID targetId) {
		HitKey key = new HitKey(casterId, targetId);
		int hitCount = hits.getOrDefault(key, 0) + 1;
		if (hitCount >= hitThreshold) {
			hits.remove(key);
			return true;
		}
		hits.put(key, hitCount);
		return false;
	}

	public void clearCaster(UUID casterId) {
		hits.keySet().removeIf(key -> key.casterId().equals(casterId));
	}

	public void clearTarget(UUID targetId) {
		hits.keySet().removeIf(key -> key.targetId().equals(targetId));
	}

	public void clear() {
		hits.clear();
	}

	private record HitKey(UUID casterId, UUID targetId) {}
}
