package dev.cuboidplots.integration.ftb;

import dev.cuboidplots.core.ParentClaims;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import java.util.UUID;

public final class FtbClaimsAdapter implements ParentClaims {
    public String integration() { return "ftbchunks"; }
    public String ownerAt(String dimension, int x, int z) {
        if (!FTBChunksAPI.api().isManagerLoaded()) throw new IllegalStateException("FTB Chunks is not ready");
        var chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension)), x, z));
        return chunk == null ? null : chunk.getTeamData().getTeam().getId().toString();
    }
    public boolean canManage(UUID player, String owner) {
        // A block-interaction ally is not a region manager. Use actual team rank.
        return FTBTeamsAPI.api().getManager().getTeamByID(UUID.fromString(owner))
            .map(team -> team.getOwner().equals(player) || team.getRankForPlayer(player).isOfficerOrBetter()).orElse(false);
    }
}
