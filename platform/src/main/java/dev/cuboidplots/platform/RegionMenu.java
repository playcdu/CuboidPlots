package dev.cuboidplots.platform;

import dev.cuboidplots.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import java.util.*;

/**
 * A vanilla six-row chest protocol with server-owned display slots. No client mod needed.
 * Every click reauthorizes through RegionService. Shift, number-key, drag and drop clicks
 * cannot take decorative items or modify the viewer's inventory through this menu.
 */
public final class RegionMenu extends ChestMenu {
    private final ServerPlayer viewer;
    private final SimpleContainer display;
    private final Map<Integer,MenuAction> actions=new HashMap<>();
    private String page="home", selected, message="Choose a region or create one from your selection";
    private UUID recipient;
    private int pageIndex;
    private boolean assigning;
    private interface MenuAction { void run() throws Exception; }
    private RegionMenu(int id,Inventory inventory,ServerPlayer viewer,SimpleContainer display) {
        super(MenuType.GENERIC_9x6,id,inventory,display,6);this.viewer=viewer;this.display=display;render();
    }
    public static void open(ServerPlayer player) {
        AddonCommands.service();
        player.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new RegionMenu(id,inventory,player,new SimpleContainer(54)),Component.literal("Cuboid Plots  |  Claim manager")));
    }
    @Override public boolean stillValid(Player player){return player==viewer && AddonRuntime.regions!=null;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return false;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(player!=viewer || type!=ClickType.PICKUP || button!=0)return;
        try { MenuAction action=actions.get(slot);if(action!=null)action.run(); }
        catch(Exception ex){message="Could not apply: "+ex.getMessage();AddonCommands.tell(viewer,message);}
        render();broadcastChanges();
    }
    private void render(){
        actions.clear();
        for(int i=0;i<54;i++)display.setItem(i,icon(Items.GRAY_STAINED_GLASS_PANE," ",ChatFormatting.DARK_GRAY));
        button(4,Items.GRASS_BLOCK,"CUBOID PLOTS",ChatFormatting.AQUA,null,"Independent spaces. Precise permissions.",message);
        button(45,Items.ARROW,"Back to regions",ChatFormatting.YELLOW,()->{page="home";pageIndex=0;},"Your accessible cuboid regions");
        button(53,Items.BARRIER,"Close",ChatFormatting.RED,viewer::closeContainer);
        try {
            switch(page){case "home":home();break;case "region":region();break;case "players":players();break;case "permissions":permissions();break;case "delete":deletion();break;default:page="home";home();}
        } catch(Exception ex){button(22,Items.BARRIER,"Unavailable",ChatFormatting.RED,null,ex.getMessage()==null?ex.toString():ex.getMessage());}
    }
    private Region current(){return AddonCommands.service().inspect(viewer.getUUID(),AddonCommands.admin(viewer),selected);}
    private void home(){
        var service=AddonCommands.service();List<Region> regions=service.list(viewer.getUUID(),AddonCommands.admin(viewer));
        for(int i=pageIndex*28;i<Math.min(regions.size(),pageIndex*28+28);i++){
            Region region=regions.get(i);int slot=contentSlot(i-pageIndex*28);
            button(slot,region.suspended?Items.RED_CONCRETE:Items.GRASS_BLOCK,region.name,region.suspended?ChatFormatting.RED:ChatFormatting.GREEN,()->{selected=region.name;page="region";pageIndex=0;},
                region.dimension,region.bounds.toString(),"Owner: "+name(region.assignedOwner),region.grants.size()+" players with grants",region.suspended?"Suspended: parent binding changed":"Click to manage this space");
        }
        if(regions.isEmpty())button(22,Items.MAP,"Your first cuboid",ChatFormatting.GREEN,null,"Set two corners, then create a region.","The parent chunks must already be claimed.","Use /cuboid pos1 and /cuboid pos2", "or the corner buttons below.");
        long[] used=service.usage(viewer.getUUID());
        String limitText;
        try{Limits limits=service.effectiveLimits(viewer.getUUID());limitText=AddonCommands.limit(limits.regions)+" regions / "+AddonCommands.limit(limits.volume)+" blocks";}
        catch(Exception ex){limitText="Provider unavailable: increases disabled";}
        button(49,Items.COMPASS,"Your allowance",ChatFormatting.GOLD,null,"Used: "+used[0]+" regions / "+used[1]+" blocks","Limits: "+limitText,"All inclusive blocks count, including air.","Grants from other players use no quota.");
        button(46,Items.WOODEN_AXE,"Set corner 1 here",ChatFormatting.AQUA,()->{AddonCommands.corner(viewer,1,viewer.blockPosition());message="First corner set at your feet";},"Current dimension and block position", "Precise alternative: /cuboid pos1 x y z");
        button(47,Items.IRON_AXE,"Set corner 2 here",ChatFormatting.AQUA,()->{AddonCommands.corner(viewer,2,viewer.blockPosition());message="Second corner set at your feet";},"Current dimension and block position", "Precise alternative: /cuboid pos2 x y z");
        button(50,Items.LIME_CONCRETE,"Create from selection",ChatFormatting.GREEN,()->{Region region=AddonCommands.create(viewer,"plot_"+Long.toString(System.currentTimeMillis(),36));selected=region.name;page="region";message="Created. No action grants are added automatically.";},"Creates a uniquely named cuboid.","For a custom name: /cuboid create <name>","Ownership, overlap and quotas are checked.");
        String available;try{available=AddonRuntime.recovery.available(viewer.getUUID())+" boxes waiting";}catch(Exception ex){available="Recovery storage unavailable";}
        button(48,Items.PURPLE_SHULKER_BOX,"Collect returned items",ChatFormatting.LIGHT_PURPLE,()->{int collected=AddonRuntime.recovery.collect(viewer);message="Collected "+collected+" boxes. Remaining boxes stay reserved for you.";},available,"Only your assigned-owner returns are visible.","Full inventory? Make space and collect later.");
        button(51,Items.BOOK,"Command reference",ChatFormatting.AQUA,()->{viewer.closeContainer();AddonCommands.tell(viewer,"Use /cuboid help for all commands. Missing grants preserve normal parent permissions.");},"/cuboid help", "No WorldEdit or client addon is required.");
        if(pageIndex>0)button(18,Items.ARROW,"Previous page",ChatFormatting.YELLOW,()->pageIndex--);
        if((pageIndex+1)*28<regions.size())button(26,Items.ARROW,"Next page",ChatFormatting.YELLOW,()->pageIndex++);
    }
    private void region(){
        Region region=current();
        button(13,Items.MAP,region.name,ChatFormatting.GREEN,null,region.dimension,"Minimum: "+region.bounds.min,"Maximum: "+region.bounds.max,"Volume: "+region.bounds.volume()+" blocks","Creator / quota: "+name(region.creator),"Assigned owner: "+name(region.assignedOwner),"Status: "+(region.suspended?"SUSPENDED":"Active binding checked on each action"));
        button(29,Items.PLAYER_HEAD,"Player permissions",ChatFormatting.AQUA,()->{page="players";assigning=false;pageIndex=0;},"Choose a player, then toggle individual actions.","No grant means normal parent protection.");
        button(31,Items.GOLDEN_HELMET,"Assign cuboid owner",ChatFormatting.GOLD,()->{page="players";assigning=true;pageIndex=0;},"Parent claim managers only.","Returns stored items and tracked placed blocks", "to the previous assigned owner.","Delegates this cuboid's permission management.","Does not move quota charges or grant actions.");
        button(33,Items.SHEARS,"Resize from selection",ChatFormatting.YELLOW,()->{AddonCommands.resize(viewer,selected);message="Bounds updated";},"Parent claim managers only.","Both selection corners must be set.","Quota and overlap checks are atomic.");
        button(40,Items.TNT,"Delete region",ChatFormatting.RED,()->page="delete","Confirmation required.","Releases the creator's quota.","The parent chunks stay claimed.");
    }
    private void players(){
        Region region=current();LinkedHashSet<UUID> ids=new LinkedHashSet<>(region.grants.keySet());
        ids.add(region.assignedOwner);for(ServerPlayer player:viewer.server.getPlayerList().getPlayers())ids.add(player.getUUID());
        List<UUID> players=new ArrayList<>(ids);
        for(int i=pageIndex*28;i<Math.min(players.size(),pageIndex*28+28);i++){
            UUID id=players.get(i);
            button(contentSlot(i-pageIndex*28),Items.PLAYER_HEAD,name(id),ChatFormatting.AQUA,()->{
                if(assigning){AddonCommands.service().assignOwner(viewer.getUUID(),AddonCommands.admin(viewer),selected,id);page="region";message="Assigned owner: "+name(id);}
                else{recipient=id;page="permissions";}
            },id.toString(),assigning?"Click to assign this cuboid":"Click to edit granular permissions",region.grants.getOrDefault(id,Collections.emptySet()).size()+" explicit grants");
        }
        if(pageIndex>0)button(18,Items.ARROW,"Previous page",ChatFormatting.YELLOW,()->pageIndex--);
        if((pageIndex+1)*28<players.size())button(26,Items.ARROW,"Next page",ChatFormatting.YELLOW,()->pageIndex++);
        button(49,Items.WRITABLE_BOOK,"Offline players",ChatFormatting.YELLOW,null,"Use /cuboid grant <region> <UUID> <action>","Existing UUID entries appear in this menu.","Names are resolved only from online players.");
    }
    private void permissions(){
        Region region=current();Set<Action> granted=region.grants.getOrDefault(recipient,Collections.emptySet());
        Action[] all=Action.values();Item[] icons={Items.IRON_PICKAXE,Items.BRICKS,Items.CHEST,Items.OAK_DOOR,Items.LEVER,Items.NOTE_BLOCK,Items.SHEARS,Items.APPLE,Items.ARMOR_STAND,Items.WOODEN_SWORD};
        for(int i=0;i<all.length;i++){
            Action action=all[i];boolean active=granted.contains(action);int slot=i<5?11+i:29+i-5;
            button(slot,icons[i],action.name().replace('_',' '),active?ChatFormatting.GREEN:ChatFormatting.GRAY,()->{
                Region fresh=current();boolean now=fresh.grants.getOrDefault(recipient,Collections.emptySet()).contains(action);
                AddonCommands.service().permission(viewer.getUUID(),AddonCommands.admin(viewer),selected,recipient,action,!now);
                message=(now?"Revoked ":"Granted ")+action+" for "+name(recipient);
            },"Player: "+name(recipient),active?"GRANTED: exception inside safe bounds":"PARENT RULES: no addon exception",active?"Click to revoke":"Click to grant","Other action categories remain independent.");
        }
        button(49,Items.BOOK,"Exception-only permissions",ChatFormatting.GOLD,null,"Owners keep normal parent permissions.","No grant is not an explicit denial.","Container permission opens the inventory.","It does not filter individual inventory slots.","Unsafe or unknown effects defer to parent rules.");
    }
    private void deletion(){
        Region region=current();
        button(22,Items.PAPER,"Delete "+region.name+"?",ChatFormatting.YELLOW,null,"Returns stored items, drops and tracked blocks", "to "+name(region.assignedOwner)+" in shulker boxes.","Removes all cuboid grants permanently.","Releases "+region.bounds.volume()+" blocks of quota.","Parent chunks remain claimed.");
        button(30,Items.RED_CONCRETE,"Confirm deletion",ChatFormatting.RED,()->{AddonCommands.service().delete(viewer.getUUID(),AddonCommands.admin(viewer),selected);page="home";pageIndex=0;message="Region deleted. Parent protection applies.";});
        button(32,Items.LIME_CONCRETE,"Keep region",ChatFormatting.GREEN,()->page="region");
    }
    private static int contentSlot(int index){return 10+(index/7)*9+index%7;}
    private String name(UUID id){ServerPlayer player=viewer.server.getPlayerList().getPlayer(id);return player==null?id.toString():player.getGameProfile().getName();}
    private void button(int slot,Item item,String title,ChatFormatting color,MenuAction action,String... lore){display.setItem(slot,icon(item,title,color,lore));if(action!=null)actions.put(slot,action);}
    private static ItemStack icon(Item item,String title,ChatFormatting color,String... lore){
        ItemStack stack=new ItemStack(item);stack.setHoverName(Component.literal(title).withStyle(color).withStyle(style->style.withItalic(false)));
        ListTag lines=new ListTag();for(String line:lore)lines.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY).withStyle(style->style.withItalic(false)))));
        stack.getOrCreateTagElement("display").put("Lore",lines);return stack;
    }
}
