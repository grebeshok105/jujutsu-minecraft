package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

final class VfxFirstPersonSignTest {
	@Test
	void signUsesPointEightSecondsAndIgnoresRetriggerUntilFinished() {
		AtomicLong now = new AtomicLong(1_000_000_000L);
		VfxFirstPersonChannel channel = new VfxFirstPersonChannel(now::get);

		channel.triggerSign();
		now.addAndGet(400_000_000L);
		assertEquals(0.5f, channel.progress(), 0.0001f);
		channel.triggerSign();
		assertEquals(0.5f, channel.progress(), 0.0001f, "duplicate SIGN must not restart");
		now.addAndGet(400_000_000L);
		channel.expireIfFinished();
		assertNull(channel.style());
	}

	@Test
	void signPoseIsMirroredByTheSharedDualArmPathAndCancelsCleanly() {
		AtomicLong now = new AtomicLong(2_000_000_000L);
		VfxFirstPersonChannel channel = new VfxFirstPersonChannel(now::get);
		channel.triggerSign();
		float progress = channel.progress();
		VfxFirstPersonChannel.Pose right = channel.dualArmPose(
				VfxFirstPersonChannel.Style.SIGN, HumanoidArm.RIGHT, progress);
		VfxFirstPersonChannel.Pose left = channel.dualArmPose(
				VfxFirstPersonChannel.Style.SIGN, HumanoidArm.LEFT, progress);

		assertNotNull(right);
		assertEquals(right, left, "the mixin owns side mirroring from one shared pose sample");
		assertTrue(right.translateY() > 0.0f);
		channel.cancel();
		assertNull(channel.style());
	}
}
