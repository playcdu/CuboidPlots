package dev.cuboidplots.testhost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
interface ParentFixture {
    void setup(ServerPlayer... players);
    void claim(ServerPlayer owner,int x,int z);
    void unclaim(ServerPlayer owner,int x,int z);
    boolean breakDenied(ServerPlayer player,BlockPos pos);
}
