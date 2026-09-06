package dev.cuboidplots.mixin.integration;

import dev.cuboidplots.platform.AddonRuntime;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Team transfers can replace a claim's team without unregistering its chunk. */
@Mixin(value = ClaimedChunkImpl.class, remap = false)
public abstract class FtbOwnershipMixin {
  @Inject(method = "setTeamData", at = @At("HEAD"), remap = false)
  private void cuboidplots$transfer(ChunkTeamDataImpl replacement, CallbackInfo info) {
    ClaimedChunkImpl claim = (ClaimedChunkImpl) (Object) this;
    if (!claim.getTeamData().getTeam().getId().equals(replacement.getTeam().getId())) {
      var pos = claim.getPos();
      AddonRuntime.ownershipChanged(pos.dimension().location().toString(), pos.x(), pos.z());
    }
  }
}
