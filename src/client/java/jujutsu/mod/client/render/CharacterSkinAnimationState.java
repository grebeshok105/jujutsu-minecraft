package jujutsu.mod.client.render;

import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/** Exact render-time snapshot for the vanilla player parts changed by the skin animation bridge. */
public final class CharacterSkinAnimationState implements AutoCloseable {
	private final List<PartState> parts;
	private boolean closed;

	private CharacterSkinAnimationState(List<PartState> parts) {
		this.parts = parts;
	}

	public static CharacterSkinAnimationState capture(PlayerModel model) {
		return new CharacterSkinAnimationState(List.of(
				PartState.capture(model.root()),
				PartState.capture(model.body),
				PartState.capture(model.head),
				PartState.capture(model.leftArm),
				PartState.capture(model.rightArm),
				PartState.capture(model.leftLeg),
				PartState.capture(model.rightLeg)));
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		for (PartState part : parts) {
			part.restore();
		}
	}

	private record PartState(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot,
			float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
		private static PartState capture(ModelPart part) {
			return new PartState(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
					part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
		}

		private void restore() {
			part.x = x;
			part.y = y;
			part.z = z;
			part.xRot = xRot;
			part.yRot = yRot;
			part.zRot = zRot;
			part.xScale = xScale;
			part.yScale = yScale;
			part.zScale = zScale;
			part.visible = visible;
			part.skipDraw = skipDraw;
		}
	}
}
