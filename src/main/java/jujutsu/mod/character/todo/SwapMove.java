package jujutsu.mod.character.todo;

import net.minecraft.world.phys.Vec3;

/** One node's destination in an atomic swap plan. */
public record SwapMove(SwapNode node, Vec3 destination) {
	// A null node/destination is accepted as an invalid preflight candidate so pure tests can assert the
	// all-or-nothing rule without bootstrapping a Minecraft entity. SwapCommit rejects it before movement.
}
