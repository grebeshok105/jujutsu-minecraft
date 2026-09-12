package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Wire contract for the cursed-spirit animation sync ids.
 *
 * <p>These four bytes travel inside {@code ClientboundEntityEventPacket}, whose client handler
 * ({@code ClientPacketListener.handleEntityEvent}) special-cases three ids with an unconditional
 * cast: 21 → {@code checkcast Guardian}, 35 → totem particles, 63 → {@code checkcast Sniffer}
 * plus a {@code SnifferSoundInstance}. Broadcasting any of them from a cursed spirit kills the
 * client with a {@code ClassCastException} and the connection dies with "Network Protocol
 * Error" — the defect the live pass found on 2026-09-12, when ATTACK_END was 63.
 *
 * <p>So this test pins three things: the exact quartet (a change must be deliberate), the byte
 * range, and exclusion of the reserved set. The reserved set is the javap-verified table from
 * the 1.21.8 client jar; extend it (with the same javap evidence) before moving an id.
 */
final class CursedSpiritAnimationStateTest {

	/** Ids the vanilla client listener casts unconditionally — never usable for our events. */
	private static final Set<Integer> RESERVED_VANILLA_EVENT_IDS = Set.of(21, 35, 63);

	@Test
	void animationIdsAreExactlyTheFrozenQuartet() {
		assertEquals(100, CursedSpiritEntity.ATTACK_START);
		assertEquals(101, CursedSpiritEntity.SCREAM_START);
		assertEquals(102, CursedSpiritEntity.ATTACK_END);
		assertEquals(103, CursedSpiritEntity.SCREAM_END);
	}

	@Test
	void animationIdsAreDistinctAndFitASignedByte() {
		long distinct = java.util.stream.Stream.of(CursedSpiritEntity.ATTACK_START,
						CursedSpiritEntity.SCREAM_START, CursedSpiritEntity.ATTACK_END,
						CursedSpiritEntity.SCREAM_END)
				.distinct()
				.count();
		assertEquals(4, distinct, "the four animation ids must be distinct");
		for (byte id : new byte[]{CursedSpiritEntity.ATTACK_START, CursedSpiritEntity.SCREAM_START,
				CursedSpiritEntity.ATTACK_END, CursedSpiritEntity.SCREAM_END}) {
			assertTrue(id >= 0 && id <= 127, "id " + id + " must stay a non-negative signed byte");
		}
	}

	@Test
	void animationIdsAvoidTheVanillaListenersReservedSet() {
		for (byte id : new byte[]{CursedSpiritEntity.ATTACK_START, CursedSpiritEntity.SCREAM_START,
				CursedSpiritEntity.ATTACK_END, CursedSpiritEntity.SCREAM_END}) {
			assertFalse(RESERVED_VANILLA_EVENT_IDS.contains((int) id),
					"id " + id + " is cast unconditionally by the vanilla client listener "
							+ "(63 → Sniffer): broadcasting it disconnects every client in range");
		}
	}
}
