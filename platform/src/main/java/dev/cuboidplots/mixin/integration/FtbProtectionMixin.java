package dev.cuboidplots.mixin.integration;

import dev.cuboidplots.platform.*;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManager;
import dev.ftb.mods.ftbchunks.data.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ClaimedChunkManager.class, remap = false)
public abstract class FtbProtectionMixin {
  @Inject(method = "protect", at = @At("RETURN"), cancellable = true, remap = false)
  private void cuboidplots$exception(
      Entity actor,
      InteractionHand hand,
      BlockPos pos,
      Protection protection,
      Entity target,
      CallbackInfoReturnable<Boolean> result) {
    // Change only FTB's own denial. Never uncancel an event belonging to another protection mod.
    // The vanilla scope distinguishes BREAK from PLACE even when Protection combines them.
    if (result.getReturnValueZ() && ActionScope.grants(actor, pos, target))
      result.setReturnValue(false);
  }

  @Inject(method = "unregisterClaim", at = @At("HEAD"), remap = false)
  private void cuboidplots$unclaim(ChunkDimPos pos, CallbackInfo info) {
    AddonRuntime.ownershipChanged(pos.dimension.location().toString(), pos.x, pos.z);
  }
}
