package dev.cuboidplots.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Recovery must load persisted item entities as well as terrain before taking a snapshot. */
@Mixin(ServerLevel.class)
public interface RecoveryEntityAccess {
    @Accessor("entityManager") PersistentEntitySectionManager<Entity> cuboidplots$entityManager();
}
