package dev.cuboidplots.testhost;

import dev.ftb.mods.ftbchunks.data.*;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

final class FtbFixture implements ParentFixture {
  FtbFixture(MinecraftServer server) {}

  public void setup(ServerPlayer... players) {
    for (ServerPlayer p : players) {
      ((TeamManager) FTBTeamsAPI.getManager())
          .playerLoggedIn(null, p.getUUID(), p.getGameProfile().getName());
      ((dev.ftb.mods.ftbchunks.data.FTBChunksTeamData) FTBChunksAPI.getManager().getData(p))
          .updateLimits();
    }
  }

  public void claim(ServerPlayer p, int x, int z) {
    var result =
        FTBChunksAPI.getManager()
            .getData(p)
            .claim(p.createCommandSourceStack(), new ChunkDimPos(p.level.dimension(), x, z), false);
    if (!result.isSuccess() && result != ClaimResults.ALREADY_CLAIMED)
      throw new IllegalStateException("FTB fixture claim failed: " + result);
  }

  public void unclaim(ServerPlayer p, int x, int z) {
    FTBChunksAPI.getManager()
        .getData(p)
        .unclaim(p.createCommandSourceStack(), new ChunkDimPos(p.level.dimension(), x, z), false);
  }

  public boolean breakDenied(ServerPlayer p, BlockPos pos) {
    return FTBChunksAPI.getManager()
        .protect(p, InteractionHand.MAIN_HAND, pos, Protection.EDIT_BLOCK, null);
  }

  public boolean entityDenied(
      ServerPlayer p, net.minecraft.world.entity.Entity target, boolean attack) {
    return FTBChunksAPI.getManager()
        .protect(
            p,
            InteractionHand.MAIN_HAND,
            target.blockPosition(),
            attack ? Protection.ATTACK_NONLIVING_ENTITY : Protection.INTERACT_ENTITY,
            target);
  }

  public void transfer(ServerPlayer oldOwner, ServerPlayer newOwner, int x, int z) {
    var manager = FTBChunksAPI.getManager();
    ((dev.cuboidplots.mixin.integration.FtbTransferAccess)
            dev.ftb.mods.ftbchunks.FTBChunks.instance)
        .cuboidplots$transfer(
            manager.getData(oldOwner),
            manager.getData(newOwner),
            java.util.Collections.singletonList(
                manager.getChunk(new ChunkDimPos(oldOwner.level.dimension(), x, z))));
  }

  public void claimDimension(
      ServerPlayer owner,
      net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,
      int x,
      int z) {
    var result =
        FTBChunksAPI.getManager()
            .getData(owner)
            .claim(owner.createCommandSourceStack(), new ChunkDimPos(dim, x, z), false);
    if (!result.isSuccess() && result != ClaimResults.ALREADY_CLAIMED)
      throw new IllegalStateException("Dimension fixture claim failed: " + result);
  }
}
