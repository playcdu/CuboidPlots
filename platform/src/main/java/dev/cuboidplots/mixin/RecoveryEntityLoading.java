package dev.cuboidplots.mixin;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PersistentEntitySectionManager.class)
public interface RecoveryEntityLoading {
    @Invoker("ensureChunkQueuedForLoad") void cuboidplots$requestLoad(long chunk);
    @Invoker("processPendingLoads") void cuboidplots$acceptLoadedEntities();
}
