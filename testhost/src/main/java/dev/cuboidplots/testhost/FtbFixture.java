package dev.cuboidplots.testhost;
import dev.ftb.mods.ftbchunks.api.*;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
final class FtbFixture implements ParentFixture {
    FtbFixture(MinecraftServer server){}
    public void setup(ServerPlayer... players){for(ServerPlayer p:players){((TeamManagerImpl)FTBTeamsAPI.api().getManager()).playerLoggedIn(null,p.getUUID(),p.getGameProfile().getName());((dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl)FTBChunksAPI.api().getManager().getOrCreateData(p)).updateLimits();}}
    public void claim(ServerPlayer p,int x,int z){var result=FTBChunksAPI.api().getManager().getOrCreateData(p).claim(p.createCommandSourceStack(),new ChunkDimPos(p.level().dimension(),x,z),false);if(!result.isSuccess()&&result!=ClaimResult.StandardProblem.ALREADY_CLAIMED)throw new IllegalStateException("FTB fixture claim failed: "+result.getResultId());}
    public void unclaim(ServerPlayer p,int x,int z){FTBChunksAPI.api().getManager().getOrCreateData(p).unclaim(p.createCommandSourceStack(),new ChunkDimPos(p.level().dimension(),x,z),false);}
    public boolean breakDenied(ServerPlayer p,BlockPos pos){return FTBChunksAPI.api().getManager().shouldPreventInteraction(p,InteractionHand.MAIN_HAND,pos,Protection.EDIT_BLOCK,null);}
    public boolean entityDenied(ServerPlayer p,net.minecraft.world.entity.Entity target,boolean attack){return FTBChunksAPI.api().getManager().shouldPreventInteraction(p,InteractionHand.MAIN_HAND,target.blockPosition(),attack?Protection.ATTACK_NONLIVING_ENTITY:Protection.INTERACT_ENTITY,target);}
    public void transfer(ServerPlayer oldOwner,ServerPlayer newOwner,int x,int z){((dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl)FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(oldOwner.level().dimension(),x,z))).setTeamData((dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl)FTBChunksAPI.api().getManager().getOrCreateData(newOwner));}
    public void claimDimension(ServerPlayer owner,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim,int x,int z){var result=FTBChunksAPI.api().getManager().getOrCreateData(owner).claim(owner.createCommandSourceStack(),new ChunkDimPos(dim,x,z),false);if(!result.isSuccess()&&result!=ClaimResult.StandardProblem.ALREADY_CLAIMED)throw new IllegalStateException("Dimension fixture claim failed: "+result.getResultId());}
}
