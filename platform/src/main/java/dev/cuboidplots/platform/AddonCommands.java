package dev.cuboidplots.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.cuboidplots.core.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Small command adapter. All authorization and state changes live in RegionService. */
public final class AddonCommands {
    private static final Map<UUID,Selection> selections=new HashMap<>();
    public static final class Selection {
        public String dimension;
        public Position first,second;
        public Cuboid bounds() { if(first==null||second==null)throw new IllegalArgumentException("Set both corners with /cuboid pos1 and /cuboid pos2"); return new Cuboid(first,second); }
    }
    public static Selection selection(ServerPlayer player) {
        Selection value=selections.computeIfAbsent(player.getUUID(), id -> new Selection());
        String dimension=player.level().dimension().location().toString();
        if(!dimension.equals(value.dimension)) {value.dimension=dimension;value.first=null;value.second=null;}
        return value;
    }
    public static void corner(ServerPlayer player,int corner,BlockPos pos) {
        Selection value=selection(player);
        if(corner==1)value.first=AddonRuntime.position(pos); else value.second=AddonRuntime.position(pos);
        tell(player,"Corner "+corner+": "+pos.getX()+" "+pos.getY()+" "+pos.getZ()+" in "+value.dimension);
    }
    public static boolean admin(ServerPlayer player) { return AddonRuntime.config!=null && AddonRuntime.config.administrators.contains(player.getUUID()); }
    public static RegionService service() { if(AddonRuntime.regions==null)throw new IllegalStateException("CuboidPlots is not ready");return AddonRuntime.regions; }
    public static void tell(ServerPlayer player,String text) { player.sendSystemMessage(Component.literal("[CuboidPlots] "+text)); }
    public static UUID resolve(ServerPlayer player,String nameOrId) {
        try{return UUID.fromString(nameOrId);}catch(IllegalArgumentException ignored){}
        ServerPlayer found=player.server.getPlayerList().getPlayerByName(nameOrId);
        if(found==null)throw new IllegalArgumentException("Player must be online by name, or use their exact UUID");
        return found.getUUID();
    }
    public static Region create(ServerPlayer player,String name) throws Exception {
        Selection selected=selection(player); Cuboid box=selected.bounds();
        if(box.min.y<player.level().getMinBuildHeight() || box.max.y>=player.level().getMaxBuildHeight())throw new IllegalArgumentException("Selection exceeds this dimension's build height");
        return service().create(player.getUUID(),admin(player),name,selected.dimension,box);
    }
    public static void resize(ServerPlayer player,String name) throws Exception {
        Region region=service().inspect(player.getUUID(),admin(player),name);
        Selection selected=selection(player); Cuboid box=selected.bounds();
        if(!region.dimension.equals(selected.dimension))throw new IllegalArgumentException("Selection must be in the region's dimension");
        if(box.min.y<player.level().getMinBuildHeight()||box.max.y>=player.level().getMaxBuildHeight())throw new IllegalArgumentException("Selection exceeds build height");
        service().resize(player.getUUID(),admin(player),name,box);
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root=literal("cuboid").requires(source -> source.getEntity() instanceof ServerPlayer)
            .executes(context -> run(context,player -> RegionMenu.open(player)));
        root.then(literal("menu").executes(context -> run(context,RegionMenu::open)));
        root.then(literal("help").executes(context -> run(context,player -> {
            tell(player,"/cuboid opens the management menu. pos1|pos2 [x y z], selection, create <name>, list, info <name>, resize <name>, delete <name>");
            tell(player,"tool gives a surveyor's stick: left-click corner 1, right-click corner 2. preview draws a private outline for 30 seconds. returns collects your saved shulker boxes.");
            tell(player,"grant|revoke <region> <online-name-or-UUID> <action>, permissions <region>, owner <region> <player>, quota, diagnose <action> <x> <y> <z>");
            tell(player,"Actions: "+Arrays.toString(Action.values())+". Missing grants defer to the parent. /cuboid why shows the last protection probe.");
        })));
        for(int i=1;i<=2;i++){final int number=i;
            root.then(literal("pos"+number).executes(context -> run(context,player -> corner(player,number,player.blockPosition())))
                .then(argument("position",BlockPosArgument.blockPos()).executes(context -> run(context,player -> corner(player,number,BlockPosArgument.getBlockPos(context,"position"))))));
        }
        for(String command:Arrays.asList("selection","list","quota","why","returns","tool","preview"))root.then(literal(command).executes(context -> run(context,player -> execute(player,command,""))));
        for(String command:Arrays.asList("create","info","resize","delete","grant","revoke","permissions","owner","diagnose"))
            root.then(literal(command).then(argument("arguments",StringArgumentType.greedyString()).executes(context -> run(context,player -> execute(player,command,StringArgumentType.getString(context,"arguments"))))));
        dispatcher.register(root);
    }
    private static void execute(ServerPlayer player,String command,String arguments) throws Exception {
        String[] args=arguments.trim().isEmpty()?new String[0]:arguments.trim().split("\\s+");
        UUID actor=player.getUUID();boolean admin=admin(player);RegionService service=service();
        switch(command){
            case "selection": Selection selection=selection(player);tell(player,selection.dimension+": "+selection.first+" / "+selection.second);if(selection.first!=null&&selection.second!=null)tell(player,selection.bounds().toString());break;
            case "list": for(Region region:service.list(actor,admin))tell(player,region.name+" | "+region.dimension+" | "+region.bounds+(region.suspended?" | SUSPENDED":""));break;
            case "quota": long[] used=service.usage(actor);Limits limits=service.effectiveLimits(actor);tell(player,"Regions "+used[0]+" / "+limit(limits.regions)+", blocks "+used[1]+" / "+limit(limits.volume)+". Remaining: "+limit(Limits.remaining(used[0],limits.regions))+" regions, "+limit(Limits.remaining(used[1],limits.volume))+" blocks");break;
            case "create": require(args,1);tell(player,"Created "+create(player,args[0]).name);break;
            case "resize": require(args,1);resize(player,args[0]);tell(player,"Updated bounds from selection");break;
            case "delete": require(args,1);service.delete(actor,admin,args[0]);tell(player,"Deleted region; creator quota released");break;
            case "info": case "permissions": require(args,1);Region region=service.inspect(actor,admin,args[0]);tell(player,region.name+": "+region.bounds+" in "+region.dimension+", parent="+region.integration+"/"+region.parentOwner+", creator="+region.creator+", assigned owner="+region.assignedOwner+", suspended="+region.suspended);region.grants.forEach((id,actions)->tell(player,id+": "+actions));break;
            case "owner": require(args,2);service.assignOwner(actor,admin,args[0],resolve(player,args[1]));tell(player,"Assigned cuboid owner; creator quota and action grants are unchanged");break;
            case "grant": case "revoke": require(args,3);service.permission(actor,admin,args[0],resolve(player,args[1]),Action.valueOf(args[2].toUpperCase(Locale.ROOT)),command.equals("grant"));tell(player,"Permission updated");break;
            case "diagnose": require(args,4);Decision decision=service.evaluate(actor,player.level().dimension().location().toString(),Action.valueOf(args[0].toUpperCase(Locale.ROOT)),Collections.singletonList(new Position(Integer.parseInt(args[1]),Integer.parseInt(args[2]),Integer.parseInt(args[3]))),true);tell(player,"Core position probe: "+decision.result+": "+decision.reason+". Actual actions also require a safely bounded footprint.");break;
            case "why": tell(player,ActionScope.lastReason(actor));break;
            case "tool": SelectionTool.give(player);break;
            case "preview": Selection selected=selection(player);SelectionTool.preview(player,selected.dimension,selected.bounds());break;
            case "returns": int delivered=AddonRuntime.recovery.collect(player);tell(player,"Collected "+delivered+" return boxes; "+AddonRuntime.recovery.available(actor)+" remain safely stored. Free inventory slots for more.");break;
            default: throw new IllegalArgumentException("Unknown command");
        }
    }
    public static String limit(long value){return value==-1?"unlimited":Long.toString(value);}
    private static void require(String[] arguments,int count){if(arguments.length!=count)throw new IllegalArgumentException("Expected "+count+" arguments; see /cuboid help");}
    private interface CommandAction{void run(ServerPlayer player)throws Exception;}
    private static int run(CommandContext<CommandSourceStack> context,CommandAction action){
        try{action.run(context.getSource().getPlayerOrException());return 1;}
        catch(Exception ex){context.getSource().sendFailure(Component.literal(ex.getMessage()==null?ex.toString():ex.getMessage()));return 0;}
    }
}
