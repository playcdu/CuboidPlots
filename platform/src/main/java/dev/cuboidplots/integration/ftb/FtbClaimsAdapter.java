package dev.cuboidplots.integration.ftb;

import dev.cuboidplots.core.ParentClaims;
import dev.ftb.mods.ftbchunks.data.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;

public final class FtbClaimsAdapter implements ParentClaims {
  public String integration() {
    return "ftbchunks";
  }

  public String ownerAt(String dimension, int x, int z) {
    if (!FTBChunksAPI.isManagerLoaded()) throw new IllegalStateException("FTB Chunks is not ready");
    var chunk =
        FTBChunksAPI.getManager()
            .getChunk(
                new ChunkDimPos(
                    ResourceKey.create(
                        Registry.DIMENSION_REGISTRY, new ResourceLocation(dimension)),
                    x,
                    z));
    return chunk == null ? null : chunk.getTeamData().getTeam().getId().toString();
  }

  public boolean canManage(UUID player, String owner) {
    // A block-interaction ally is not a region manager. Use actual team rank.
    var team = FTBTeamsAPI.getManager().getTeamByID(UUID.fromString(owner));
    return team != null
        && (team.getOwner().equals(player) || team.getHighestRank(player).isOfficer());
  }
}
