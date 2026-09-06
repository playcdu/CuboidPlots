package dev.cuboidplots.testhost;

import com.mojang.authlib.GameProfile;
import dev.cuboidplots.core.*;
import dev.cuboidplots.platform.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Included only by -PtestHost. Real parent methods and world mutations on a dedicated server. */
public final class ServerAcceptance implements ModInitializer {
    private static final UUID OWNER=new UUID(1,1),ALICE=new UUID(1,2),BOB=new UUID(1,3);
    private static int checks;
    public void onInitialize(){
        if(!Boolean.getBoolean("cuboidplots.test"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> run(server)));
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("[CuboidPlots test] PASS "+label);}
    private static ServerPlayer player(MinecraftServer server,UUID id,String name){
        ServerPlayer player=new ServerPlayer(server,server.overworld(),new GameProfile(id,name));
        player.connection=new ServerGamePacketListenerImpl(server,new Connection(PacketFlow.SERVERBOUND),player);
        player.setPos(1,100,1);
        return player;
    }
    private static void run(MinecraftServer server){
        Path report=server.getWorldPath(LevelResource.ROOT).resolve("cuboidplots-acceptance.txt");
        try{
            ServerLevel level=server.overworld();String dim=level.dimension().location().toString();
            ServerPlayer owner=player(server,OWNER,"CuboidOwner"),alice=player(server,ALICE,"CuboidAlice"),bob=player(server,BOB,"CuboidBob");
            check(!server.getPlayerList().isOp(alice.getGameProfile())&&!server.getPlayerList().isOp(bob.getGameProfile()),"two ordinary non-operator identities");
            ParentFixture parent=AddonRuntime.parentId.equals("ftbchunks")?new FtbFixture(server):new OpacFixture(server);
            parent.setup(owner,alice,bob);
            RegionService service=AddonRuntime.regions;
            if(Files.exists(report)){
                check(service.inspect(OWNER,false,"lower").grants.get(ALICE).contains(Action.BREAK),"grant persisted across actual server restart");
                BlockPos inside=new BlockPos(2,100,2);level.setBlockAndUpdate(inside,Blocks.STONE.defaultBlockState());
                try(ActionScope scope=ActionPlanner.breaking(alice,inside)){check(!parent.breakDenied(alice,inside),"persisted grant traverses parent protection on restarted server");}
                Files.write(report,Arrays.asList("RESTART PASS "+checks+" assertions"),StandardOpenOption.APPEND);
                return;
            }
            for(int x=0;x<4;x++)for(int z=0;z<2;z++)parent.claim(owner,x,z);
            // High above flat terrain, with a deliberately inert neighborhood for bounded actions.
            for(BlockPos pos:BlockPos.betweenClosed(0,97,0,63,111,31))level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            service.create(OWNER,false,"lower",dim,box(0,99,0,5,102,5));
            service.create(OWNER,false,"beside",dim,box(6,99,0,11,102,5));
            service.create(OWNER,false,"upper",dim,box(0,103,0,5,107,5));
            service.create(OWNER,false,"four",dim,box(16,99,0,47,100,31));
            service.permission(OWNER,false,"lower",ALICE,Action.BREAK,true);
            service.permission(OWNER,false,"beside",BOB,Action.PLACE,true);
            service.permission(OWNER,false,"upper",BOB,Action.CONTAINER,true);
            BlockPos inside=new BlockPos(2,100,2),outside=new BlockPos(12,100,2),other=new BlockPos(8,100,2);
            level.setBlockAndUpdate(inside,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(outside,Blocks.STONE.defaultBlockState());
            check(parent.breakDenied(alice,inside),"parent baseline denies untrusted player without action scope");
            try(ActionScope scope=ActionPlanner.breaking(alice,inside)){check(!parent.breakDenied(alice,inside),"cuboid exception overrides real parent denial");}
            try(ActionScope scope=ActionPlanner.breaking(alice,outside)){check(parent.breakDenied(alice,outside),"standing inside does not grant target outside");}
            alice.setPos(13,100,2);
            try(ActionScope scope=ActionPlanner.breaking(alice,inside)){check(!parent.breakDenied(alice,inside),"standing outside can act on authorized target");}
            check(alice.gameMode.destroyBlock(inside)&&level.getBlockState(inside).isAir(),"actual game mode destroys authorized block");
            check(!alice.gameMode.destroyBlock(outside)&&level.getBlockState(outside).is(Blocks.STONE),"actual game mode leaves denied outside block intact");
            level.setBlockAndUpdate(inside,Blocks.STONE.defaultBlockState());
            service.permission(OWNER,false,"lower",ALICE,Action.BREAK,false);
            try(ActionScope scope=ActionPlanner.breaking(alice,inside)){check(parent.breakDenied(alice,inside),"revocation restores real parent denial");}
            service.permission(OWNER,false,"lower",ALICE,Action.BREAK,true);
            check(service.usage(OWNER)[0]==4,"two same-chunk, stacked, and four-chunk cuboids coexist");
            RegionMenu.open(owner);check(owner.containerMenu.slots.size()==90,"server opens vanilla 54-slot chest menu plus player inventory");
            owner.closeContainer();
            service.create(OWNER,false,"stale",dim,box(48,99,0,51,101,3));service.permission(OWNER,false,"stale",ALICE,Action.BREAK,true);
            parent.unclaim(owner,3,0);
            check(service.inspect(OWNER,false,"stale").suspended,"real parent unclaim suspends stale cuboid grants");
            Files.write(report,Arrays.asList("FIRST RUN PASS "+checks+" assertions", "Scope: dedicated-server parent calls and selected world mutations; not client packet verification."));
        }catch(Throwable ex){ex.printStackTrace();try{Files.write(report,Arrays.asList("FAIL "+ex));}catch(Exception ignored){} }
        finally{System.out.println("[CuboidPlots test] Test run finished, stopping server");server.halt(false);}
    }
    private static Cuboid box(int x,int y,int z,int a,int b,int c){return new Cuboid(new Position(x,y,z),new Position(a,b,c));}
}
