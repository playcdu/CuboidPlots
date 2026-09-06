package dev.cuboidplots.mixin.integration;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.cuboidplots.platform.AddonRuntime;
import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbchunks.data.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.FTBChunksTeamData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** This older upstream assigns a public field instead of calling setTeamData. */
@Mixin(value = FTBChunks.class, remap = false)
public abstract class FtbOwnershipMixin {
  @WrapOperation(
      method = "transferClaims",
      at =
          @At(
              value = "FIELD",
              target =
                  "Ldev/ftb/mods/ftbchunks/data/ClaimedChunk;teamData:Ldev/ftb/mods/ftbchunks/data/FTBChunksTeamData;",
              opcode = org.objectweb.asm.Opcodes.PUTFIELD),
      remap = false)
  private void cuboidplots$transfer(
      ClaimedChunk claim, FTBChunksTeamData replacement, Operation<Void> original) {
    if (!claim.teamData.getTeamId().equals(replacement.getTeamId()))
      AddonRuntime.ownershipChanged(
          claim.pos.dimension.location().toString(), claim.pos.x, claim.pos.z);
    original.call(claim, replacement);
  }
}
