package jujutsu.mod.cursedincident.object;

import java.util.UUID;

import jujutsu.mod.cursedincident.ObjectSpawner;
import jujutsu.mod.cursedincident.runtime.ObjectDwellTracker;
import jujutsu.mod.registry.JujutsuItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Production implementation of the B1 object-spawn seam. */
public final class ObjectSpawnerImpl implements ObjectSpawner {
    public static final ObjectSpawnerImpl INSTANCE = new ObjectSpawnerImpl();

    public ObjectSpawnerImpl() {
    }

    @Override
    public UUID spawn(ServerLevel level, BlockPos pos, String typeId, int grade, long seed) {
        if (level == null || pos == null) {
            return null;
        }
        RandomSource random = RandomSource.create(seed);
        CursedObjectType type = typeId == null || typeId.isBlank()
                ? CursedObjectRegistry.randomType(random, grade)
                : CursedObjectRegistry.byId(typeId);
        if (type == null || type == CursedObjectRegistry.QA_PROBE || !CursedObjectRegistry.canMint(type)) {
            return null;
        }
        int appliedGrade = normalizeGrade(type, grade);
        UUID instanceId = UUID.randomUUID();
        CursedObjectState state = CursedObjectState.fresh(instanceId, type.id(), appliedGrade, level.getGameTime());
        if (!CursedObjectRegistry.registerInstance(state)) {
            return null;
        }
        ItemStack stack = CursedObjectItem.stack(state);
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setUnlimitedLifetime();
        entity.setNoPickUpDelay();
        if (!level.addFreshEntity(entity)) {
            CursedObjectRegistry.unregisterInstance(instanceId);
            return null;
        }
        ObjectDwellTracker.noteWorldItem(entity);
        return instanceId;
    }

    public UUID spawn(ServerLevel level, BlockPos pos, CursedObjectType type, int grade, long seed) {
        return spawn(level, pos, type == null ? null : type.id(), grade, seed);
    }

    public static ItemStack mintStack(String typeId, int grade, long mintedGameTime, long seed) {
        RandomSource random = RandomSource.create(seed);
        CursedObjectType type = typeId == null || typeId.isBlank()
                ? CursedObjectRegistry.randomType(random, grade)
                : CursedObjectRegistry.byId(typeId);
        if (type == null || type == CursedObjectRegistry.QA_PROBE || !CursedObjectRegistry.canMint(type)) {
            return ItemStack.EMPTY;
        }
        int appliedGrade = normalizeGrade(type, grade);
        CursedObjectState state = CursedObjectState.fresh(UUID.randomUUID(), type.id(), appliedGrade, mintedGameTime);
        if (!CursedObjectRegistry.registerInstance(state)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = CursedObjectItem.stack(state);
        return stack;
    }

    private static int normalizeGrade(CursedObjectType type, int grade) {
        if (grade < type.gradeMin()) {
            return type.gradeMin();
        }
        if (grade > type.gradeMax()) {
            return type.gradeMax();
        }
        return grade;
    }
}
