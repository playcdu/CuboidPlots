package dev.cuboidplots.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Force the recipient inventory to disk before committing its recovery delivery receipt. */
@Mixin(PlayerList.class)
public interface PlayerSaveAccess {
    @Invoker("save") void cuboidplots$savePlayer(ServerPlayer player);
}
