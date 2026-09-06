package dev.cuboidplots.mixin.integration;

import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbchunks.data.*;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-host-only bridge to the exact parent transfer path used by team changes. */
@Mixin(value = FTBChunks.class, remap = false)
public interface FtbTransferAccess {
  @Invoker("transferClaims")
  void cuboidplots$transfer(
      FTBChunksTeamData from, FTBChunksTeamData to, Collection<ClaimedChunk> chunks);
}
