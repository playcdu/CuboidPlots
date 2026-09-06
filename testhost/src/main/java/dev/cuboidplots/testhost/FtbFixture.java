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
    public void setup(ServerPlayer... players){for(ServerPlayer p:players)((TeamManagerImpl)FTBTeamsAPI.api().getManager()).playerLoggedIn(null,p.getUUID(),p.getGameProfile().getName());}
    public void claim(ServerPlayer p,int x,int z){FTBChunksAPI.api().getManager().getOrCreateData(p).claim(p.createCommandSourceStack(),new ChunkDimPos(p.level().dimension(),x,z),false);}
    public void unclaim(ServerPlayer p,int x,int z){FTBChunksAPI.api().getManager().getOrCreateData(p).unclaim(p.createCommandSourceStack(),new ChunkDimPos(p.level().dimension(),x,z),false);}
    public boolean breakDenied(ServerPlayer p,BlockPos pos){return FTBChunksAPI.api().getManager().shouldPreventInteraction(p,InteractionHand.MAIN_HAND,pos,Protection.EDIT_BLOCK,null);}
}
