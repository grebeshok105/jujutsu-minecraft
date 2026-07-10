package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class NailAnchorTest {
	private NailAnchorTest() {}

	public static void main(String[] args) {
		UUID target = UUID.randomUUID();
		NailAnchor entity = NailAnchor.entity(target, 17, new Vec3(0.1, 0.8, -0.2), new Vec3(0.0, 0.0, 1.0));
		assert entity.kind() == NailAnchor.Kind.ENTITY;
		assert entity.stableId().equals(target);
		assert entity.cachedEntityId() == 17;
		assert NailAnchorResolution.forEntity(false, false, false) == NailAnchorResolution.TEMPORARILY_UNAVAILABLE;
		assert NailAnchorResolution.forEntity(false, true, false) == NailAnchorResolution.CONFIRMED_REMOVED;
		assert NailAnchorResolution.forEntity(true, false, true) == NailAnchorResolution.RESOLVED;

		NailAnchor block = NailAnchor.block(new BlockPos(4, 70, -3), "minecraft:stone", new Vec3(0.25, 0.5, 0.75), new Vec3(0.0, 1.0, 0.0));
		assert block.kind() == NailAnchor.Kind.BLOCK;
		assert block.blockPos().equals(new BlockPos(4, 70, -3));
		assert NailAnchorResolution.forBlock(true, true) == NailAnchorResolution.RESOLVED;
		assert NailAnchorResolution.forBlock(true, false) == NailAnchorResolution.CONFIRMED_REMOVED;
		assert NailAnchorResolution.forBlock(false, false) == NailAnchorResolution.TEMPORARILY_UNAVAILABLE;

		ResourceLocation type = ResourceLocation.parse("jujutsumod:test_tool");
		NailAnchor runtime = NailAnchor.runtime(type, target, Vec3.ZERO, new Vec3(1.0, 0.0, 0.0));
		assert runtime.kind() == NailAnchor.Kind.RUNTIME_OBJECT;
		assert runtime.runtimeType().equals(type);
		NailRuntimeAnchorRegistry registry = new NailRuntimeAnchorRegistry();
		registry.register(type, (objectId, lastKnown) -> NailRuntimeAnchorRegistry.Result.resolved(new Vec3(2.0, 3.0, 4.0), new Vec3(0.0, 1.0, 0.0)));
		assert registry.resolve(runtime, Vec3.ZERO).resolution() == NailAnchorResolution.RESOLVED;
		assert registry.resolve(runtime, Vec3.ZERO).position().equals(new Vec3(2.0, 3.0, 4.0));
		NailAnchor unknown = NailAnchor.runtime(ResourceLocation.parse("jujutsumod:missing"), target, Vec3.ZERO, new Vec3(0.0, 0.0, 1.0));
		assert registry.resolve(unknown, Vec3.ZERO).resolution() == NailAnchorResolution.INVALID;
		System.out.println("NailAnchorTest passed");
	}
}
