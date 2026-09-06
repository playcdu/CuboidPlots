package dev.cuboidplots.testhost;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import xaero.pac.common.server.api.OpenPACServerAPI;
final class OpacFixture implements ParentFixture {
    private final MinecraftServer server;
    OpacFixture(MinecraftServer server){this.server=server;}
    public void setup(ServerPlayer... players){}
    public void claim(ServerPlayer p,int x,int z){OpenPACServerAPI.get(server).getServerClaimsManager().claim(p.level().dimension().location(),p.getUUID(),0,x,z,false);}
    public void unclaim(ServerPlayer p,int x,int z){OpenPACServerAPI.get(server).getServerClaimsManager().unclaim(p.level().dimension().location(),x,z);}
    public boolean breakDenied(ServerPlayer p,BlockPos pos){return OpenPACServerAPI.get(server).getChunkProtection().onBlockInteraction(p,InteractionHand.MAIN_HAND,ItemStack.EMPTY,p.serverLevel(),pos,Direction.UP,true,false);}
}
