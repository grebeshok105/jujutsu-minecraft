package jujutsu.mod.character.todo;

import java.util.UUID;

/** A quota-reserved Revised swap waiting for its scheduled server tick. */
public record PendingAutoSwap(UUID targetUuid, long executeAtGameTime) {}
